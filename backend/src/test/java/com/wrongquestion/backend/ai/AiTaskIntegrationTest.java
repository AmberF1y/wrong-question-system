package com.wrongquestion.backend.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sun.net.httpserver.HttpServer;
import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.dto.CreateQuestionRequest;
import com.wrongquestion.backend.question.image.service.QuestionImageService;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import com.wrongquestion.backend.question.service.QuestionService;
import com.wrongquestion.backend.review.repository.QuestionReviewStateRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@SpringBootTest
@AutoConfigureMockMvc
class AiTaskIntegrationTest {
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01, 0x02, 0x03, 0x04
    };
    private static final AtomicReference<String> BODY = new AtomicReference<>("");
    private static final AtomicInteger HTTP_STATUS = new AtomicInteger(200);
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final AtomicReference<CountDownLatch> STARTED = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> RELEASE = new AtomicReference<>();
    private static final HttpServer SERVER = startServer();

    private static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/v1/chat/completions",
                    exchange -> {
                        REQUESTS.incrementAndGet();
                        String request =
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8);
                        if (!request.contains("data:image/png;base64,")
                                || !"Bearer fixture-key"
                                        .equals(
                                                exchange.getRequestHeaders()
                                                        .getFirst("Authorization"))) {
                            exchange.sendResponseHeaders(400, -1);
                            exchange.close();
                            return;
                        }
                        if (STARTED.get() != null) STARTED.get().countDown();
                        try {
                            if (RELEASE.get() != null) RELEASE.get().await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        byte[] response = BODY.get().getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(HTTP_STATUS.get(), response.length);
                        exchange.getResponseBody().write(response);
                        exchange.close();
                    });
            server.setExecutor(
                    Executors.newCachedThreadPool(
                            r -> {
                                var t = new Thread(r, "ai-fixture-http");
                                t.setDaemon(true);
                                return t;
                            }));
            server.start();
            return server;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.ai.mode", () -> "compatible");
        registry.add(
                "app.ai.base-url",
                () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/v1");
        registry.add("app.ai.api-key", () -> "fixture-key");
        registry.add("app.ai.input-price-per-million", () -> "1");
        registry.add("app.ai.output-price-per-million", () -> "2");
        registry.add(
                "app.storage.question-image-directory", () -> "./target/ai-integration-images");
    }

    @Autowired AiTaskService service;
    @Autowired AiImageStorage images;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired KnowledgePointRepository knowledge;
    @Autowired QuestionRepository questionRepository;
    @Autowired QuestionReviewStateRepository reviewStates;
    @Autowired QuestionService questions;
    @Autowired QuestionImageService questionImages;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    private final List<UUID> ids = new ArrayList<>();
    private final List<Long> questionIds = new ArrayList<>();
    private Long rootId, childId;
    private int previousUsage;

    @BeforeEach
    void setup() {
        assertEquals(
                "wrong_question_system_test", db.queryForObject("SELECT DATABASE()", String.class));
        REQUESTS.set(0);
        HTTP_STATUS.set(200);
        STARTED.set(null);
        RELEASE.set(null);
        previousUsage = service.configuration().callsToday();
        db.update("DELETE FROM ai_daily_usage WHERE business_date=?", LocalDate.now(clock));
        var root = knowledge.saveAndFlush(new KnowledgePoint("AI测试科目-" + UUID.randomUUID(), null));
        rootId = root.getId();
        childId = knowledge.saveAndFlush(new KnowledgePoint("测试知识点", root)).getId();
        output(List.of(childId));
    }

    @AfterEach
    void cleanup() {
        if (RELEASE.get() != null) RELEASE.get().countDown();
        for (UUID id : ids) {
            var task = service.get(id);
            if (task.questionId() != null && !questionIds.contains(task.questionId()))
                questionIds.add(task.questionId());
            if (AiTaskServiceTestSupport.active(task.status())) service.cancel(id);
            service.delete(id);
        }
        for (Long id : questionIds) if (questionRepository.existsById(id)) questions.delete(id);
        knowledge.deleteById(childId);
        knowledge.deleteById(rootId);
        db.update(
                "INSERT INTO ai_daily_usage(business_date,call_count) VALUES (?,?) ON DUPLICATE KEY"
                        + " UPDATE call_count=?",
                LocalDate.now(clock),
                previousUsage,
                previousUsage);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "题目.png", "image/png", PNG);
    }

    private UUID create() {
        UUID id = UUID.randomUUID();
        service.create(id, file(), rootId);
        ids.add(id);
        return id;
    }

    private void output(List<Long> suggestions) {
        String content =
                json.writeValueAsString(
                        Map.of(
                                "questionText",
                                "计算 $1+1$",
                                "correctAnswer",
                                "2",
                                "analysis",
                                "图片中的原解析",
                                "knowledgePointIds",
                                suggestions,
                                "warnings",
                                List.of()));
        BODY.set(
                json.writeValueAsString(
                        Map.of(
                                "choices",
                                List.of(
                                        Map.of(
                                                "finish_reason",
                                                "stop",
                                                "message",
                                                Map.of("content", content))),
                                "usage",
                                Map.of("prompt_tokens", 100, "completion_tokens", 25))));
    }

    private CreateQuestionRequest confirmed() {
        return new CreateQuestionRequest(
                "人工核对题目", "我的答案", "正确答案", "人工核对解析", "计算失误", List.of(childId));
    }

    @Test
    void shouldPersistTaskAndReturnSameTaskForDuplicateSubmission() {
        var id = create();
        assertEquals("QUEUED", service.get(id).status());
        assertEquals(id.toString(), service.create(id, file(), rootId).id());
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM ai_task WHERE id=?", Integer.class, id.toString()));
        var other =
                new MockMultipartFile(
                        "file", "other.png", "image/png", Arrays.copyOf(PNG, PNG.length + 1));
        assertEquals(
                "AI_IDEMPOTENCY_CONFLICT",
                assertThrows(AiException.class, () -> service.create(id, other, rootId)).getCode());
    }

    @Test
    void shouldRecognizeDraftLogUsageAndAvoidWritingQuestionBeforeConfirmation() {
        var id = create();
        long before = questionRepository.count();
        assertTrue(service.executeNext());
        var task = service.get(id);
        assertEquals("SUCCEEDED", task.status());
        assertEquals("计算 $1+1$", task.draft().questionText());
        assertEquals(List.of(childId), task.draft().knowledgePointIds());
        assertEquals(before, questionRepository.count());
        var call = service.calls(id).getFirst();
        assertEquals(100L, call.inputTokens());
        assertEquals(25L, call.outputTokens());
        assertEquals(0, call.estimatedCost().compareTo(new java.math.BigDecimal("0.00015000")));
        assertEquals(1, REQUESTS.get());
        assertEquals(1, service.configuration().callsToday());
    }

    @Test
    void shouldAtomicallySaveOriginalImageAndOnlyCreateOneQuestionForConcurrentConfirmation()
            throws Exception {
        var id = create();
        service.executeNext();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Long> apply =
                    () -> {
                        barrier.await();
                        return service.apply(id, confirmed()).id();
                    };
            var first = executor.submit(apply);
            var second = executor.submit(apply);
            Long question = first.get(10, TimeUnit.SECONDS);
            assertEquals(question, second.get(10, TimeUnit.SECONDS));
            assertEquals("APPLIED", service.get(id).status());
            assertEquals(question, service.get(id).questionId());
            var detail = questions.getById(question);
            assertEquals("人工核对题目", detail.questionText());
            assertNotNull(detail.imagePath());
            assertArrayEquals(
                    PNG, questionImages.load(question).resource().getContentAsByteArray());
            assertEquals(
                    LocalDate.now(clock).plusDays(1),
                    reviewStates.findById(question).orElseThrow().getNextReviewDate());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRollbackFailedConfirmationAndKeepDraftAvailable() {
        var id = create();
        service.executeNext();
        long count = questionRepository.count();
        var bad =
                new CreateQuestionRequest("题目", "答案", "正确答案", "解析", "原因", List.of(Long.MAX_VALUE));
        assertThrows(RuntimeException.class, () -> service.apply(id, bad));
        assertEquals(count, questionRepository.count());
        assertEquals("SUCCEEDED", service.get(id).status());
    }

    @Test
    void shouldRejectSavingBeforeRecognitionIsComplete() {
        var id = create();
        assertEquals(
                "AI_DRAFT_NOT_READY",
                assertThrows(AiException.class, () -> service.apply(id, confirmed())).getCode());
    }

    @Test
    void shouldDiscardInvalidAndCrossSubjectKnowledgeSuggestions() {
        var other = knowledge.saveAndFlush(new KnowledgePoint("另一个科目-" + UUID.randomUUID(), null));
        try {
            var id = create();
            output(List.of(childId, other.getId(), Long.MAX_VALUE));
            service.executeNext();
            assertEquals(List.of(childId), service.get(id).draft().knowledgePointIds());
            assertTrue(
                    service.get(id).draft().warnings().stream().anyMatch(s -> s.contains("已移除")));
        } finally {
            knowledge.deleteById(other.getId());
        }
    }

    @Test
    void shouldBoundAutomaticRetriesAndAllowExplicitManualRetry() {
        var id = create();
        HTTP_STATUS.set(429);
        BODY.set("secret-provider-response-fixture-key");
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertTrue(service.executeNext());
            assertEquals(attempt < 3 ? "RETRY_WAIT" : "FAILED", service.get(id).status());
            db.update(
                    "UPDATE ai_task SET available_at=? WHERE id=?",
                    LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                    id.toString());
        }
        assertEquals(3, REQUESTS.get());
        assertEquals(3, service.calls(id).size());
        assertFalse(service.get(id).errorMessage().contains("fixture-key"));
        HTTP_STATUS.set(200);
        output(List.of(childId));
        service.retry(id);
        service.executeNext();
        assertEquals("SUCCEEDED", service.get(id).status());
        assertEquals(4, service.get(id).attemptCount());
    }

    @Test
    void shouldRecordReportedUsageEvenWhenDraftOutputIsInvalid() {
        var id = create();
        BODY.set(
                json.writeValueAsString(
                        Map.of(
                                "choices",
                                List.of(Map.of("message", Map.of("content", "{}"))),
                                "usage",
                                Map.of("prompt_tokens", 100, "completion_tokens", 25))));
        service.executeNext();
        assertEquals("FAILED", service.get(id).status());
        assertEquals("AI_INVALID_OUTPUT", service.get(id).errorCode());
        assertEquals(100L, service.calls(id).getFirst().inputTokens());
        assertNotNull(service.calls(id).getFirst().estimatedCost());
    }

    @Test
    void shouldEnforceDailyQuotaBeforeSendingModelRequest() {
        var id = create();
        db.update(
                "INSERT INTO ai_daily_usage(business_date,call_count) VALUES (?,20)",
                LocalDate.now(clock));
        assertFalse(service.executeNext());
        assertEquals("AI_DAILY_LIMIT", service.get(id).errorCode());
        assertEquals(0, REQUESTS.get());
        assertTrue(service.calls(id).isEmpty());
    }

    @Test
    void shouldRecoverInterruptedTasksWithoutResubmittingPotentiallyBilledRequests() {
        var id = create();
        db.update("UPDATE ai_task SET status='RUNNING',attempt_count=1 WHERE id=?", id.toString());
        db.update(
                "INSERT INTO ai_call_record(task_id,attempt,provider,model,status,created_at)"
                        + " VALUES (?,1,'compatible','test-vision','RUNNING',?)",
                id.toString(),
                LocalDateTime.now(ZoneOffset.UTC));
        service.recoverInterrupted();
        assertEquals("FAILED", service.get(id).status());
        assertEquals("AI_PROCESS_INTERRUPTED", service.get(id).errorCode());
        assertEquals("INTERRUPTED", service.calls(id).getFirst().status());
        assertFalse(service.executeNext());
        assertEquals(0, REQUESTS.get());
    }

    @Test
    void shouldCancelInFlightCallAndDiscardLateResult() throws Exception {
        var id = create();
        STARTED.set(new CountDownLatch(1));
        RELEASE.set(new CountDownLatch(1));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(service::executeNext);
            assertTrue(STARTED.get().await(5, TimeUnit.SECONDS));
            service.cancel(id);
            RELEASE.get().countDown();
            assertTrue(future.get(8, TimeUnit.SECONDS));
            assertEquals("CANCELLED", service.get(id).status());
            assertNull(service.get(id).draft());
            assertEquals("CANCELLED", service.calls(id).getFirst().status());
        } finally {
            RELEASE.get().countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldExposeSafeApiAndValidateInvalidImagesAndMissingIds() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(
                        multipart("/api/ai/tasks")
                                .file(file())
                                .header("Idempotency-Key", id.toString())
                                .param("rootId", rootId.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.imagePath").doesNotExist());
        ids.add(id);
        mvc.perform(get("/api/ai/tasks/" + id + "/image"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/api/ai/tasks").param("status", "BAD")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ai/tasks/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(
                        multipart("/api/ai/tasks")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "evil.png",
                                                "image/png",
                                                "<script>".getBytes()))
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_IMAGE_UNSUPPORTED"));
        mvc.perform(get("/api/ai/configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void shouldLimitManualRetriesEvenAfterEarlyPermanentFailure() {
        var id = create();
        HTTP_STATUS.set(401);
        BODY.set("secret");
        service.executeNext();
        for (int i = 0; i < 2; i++) {
            service.retry(id);
            service.executeNext();
        }
        assertEquals(3, service.get(id).attemptCount());
        assertEquals(
                "AI_RETRY_LIMIT",
                assertThrows(AiException.class, () -> service.retry(id)).getCode());
    }

    @Test
    void shouldLeaveOriginalQuestionAfterDeletingAppliedTask() {
        var id = create();
        service.executeNext();
        var detail = service.apply(id, confirmed());
        questionIds.add(detail.id());
        String key =
                db.queryForObject(
                        "SELECT image_path FROM ai_task WHERE id=?", String.class, id.toString());
        service.delete(id);
        ids.remove(id);
        assertThrows(AiException.class, () -> images.read(key));
        assertNotNull(questions.getById(detail.id()).imagePath());
    }

    @Test
    void shouldRejectStaleRetryRequestEvenWhenPreviousRetryAlreadyFailed() {
        var id = create();
        HTTP_STATUS.set(401);
        BODY.set("secret");
        service.executeNext();
        service.retry(id, 0);
        service.executeNext();
        assertEquals(
                "AI_RETRY_CONCURRENT_MODIFICATION",
                assertThrows(AiException.class, () -> service.retry(id, 0)).getCode());
        assertEquals(1, service.get(id).retryCount());
    }

    @Test
    void shouldAllowOnlyOneExternalCallWhenTwoWorkersCompeteForLastQuota() throws Exception {
        var first = create();
        var second = create();
        db.update(
                "INSERT INTO ai_daily_usage(business_date,call_count) VALUES (?,19)",
                LocalDate.now(clock));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(service::executeNext);
            var b = executor.submit(service::executeNext);
            a.get(8, TimeUnit.SECONDS);
            b.get(8, TimeUnit.SECONDS);
            assertEquals(1, REQUESTS.get());
            assertEquals(
                    Set.of("FAILED", "SUCCEEDED"),
                    Set.of(service.get(first).status(), service.get(second).status()));
            assertEquals(20, service.configuration().callsToday());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRecoverStalledTaskWithoutAutomaticallyCallingModelAgain() {
        var id = create();
        db.update(
                "UPDATE ai_task SET status='RUNNING',updated_at=? WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10),
                id.toString());
        service.recoverExpired();
        assertEquals("AI_TASK_STALLED", service.get(id).errorCode());
        assertEquals("FAILED", service.get(id).status());
        assertFalse(service.executeNext());
        assertEquals(0, REQUESTS.get());
    }

    @Test
    void shouldPageAndFilterGlobalCallRecordsWithoutExposingPromptOrSecrets() throws Exception {
        var first = create();
        service.executeNext();
        var second = create();
        HTTP_STATUS.set(401);
        BODY.set("secret");
        service.executeNext();
        var failed = service.allCalls(0, 1, "FAILED");
        assertEquals(1, failed.totalElements());
        assertEquals(second.toString(), failed.items().getFirst().taskId());
        assertEquals("AI_PROVIDER_AUTHENTICATION", failed.items().getFirst().call().errorCode());
        assertEquals(2, service.allCalls(0, 20, null).totalElements());
        assertTrue(service.allCalls(1, 2, null).items().isEmpty());
        mvc.perform(get("/api/ai/calls").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].taskId").value(second.toString()))
                .andExpect(jsonPath("$.items[0].call.apiKey").doesNotExist());
        mvc.perform(get("/api/ai/calls").param("status", "BAD")).andExpect(status().isBadRequest());
        assertEquals(1, service.allCalls(0, 1, null).items().size());
    }

    @Test
    void shouldRecordReportedUsageWhenModelOutputIsTruncated() {
        var id = create();
        BODY.set(
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":25}}");
        service.executeNext();
        assertEquals("FAILED", service.get(id).status());
        assertEquals(100L, service.calls(id).getFirst().inputTokens());
        assertEquals(25L, service.calls(id).getFirst().outputTokens());
    }

    @Test
    void shouldReturnConsistentValidationErrorForMissingIdempotencyAndRetryGeneration()
            throws Exception {
        mvc.perform(multipart("/api/ai/tasks").file(file()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/ai/tasks/" + UUID.randomUUID() + "/retry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private static class AiTaskServiceTestSupport {
        static boolean active(String status) {
            return Set.of("QUEUED", "RUNNING", "RETRY_WAIT").contains(status);
        }
    }
}
