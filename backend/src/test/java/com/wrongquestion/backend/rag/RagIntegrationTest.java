package com.wrongquestion.backend.rag;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sun.net.httpserver.HttpServer;
import com.wrongquestion.backend.ai.*;
import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.dto.CreateQuestionRequest;
import com.wrongquestion.backend.question.service.QuestionService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
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
class RagIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicReference<String> ANSWER = new AtomicReference<>();
    private static final AtomicBoolean EMBED_FAIL = new AtomicBoolean();
    private static final AtomicInteger CHATS = new AtomicInteger(), EMBEDS = new AtomicInteger();
    private static final AtomicReference<String> LAST_CHAT = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> STARTED = new AtomicReference<>(),
            RELEASE = new AtomicReference<>();
    private static final HttpServer SERVER = server();

    private static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/v1/",
                    exchange -> {
                        String text =
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8);
                        var input = JSON.readTree(text);
                        String output;
                        int status = 200;
                        if (!"Bearer fixture-key"
                                .equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                            exchange.sendResponseHeaders(401, -1);
                            exchange.close();
                            return;
                        }
                        if (exchange.getRequestURI().getPath().endsWith("/embeddings")) {
                            EMBEDS.incrementAndGet();
                            if (EMBED_FAIL.get()) {
                                status = 503;
                                output = "{}";
                            } else {
                                List<Object> data = new ArrayList<>();
                                for (int i = 0; i < input.path("input").size(); i++)
                                    data.add(
                                            Map.of(
                                                    "index",
                                                    i,
                                                    "embedding",
                                                    input.path("input")
                                                                    .get(i)
                                                                    .asText()
                                                                    .contains("quantumxyz")
                                                            ? List.of(0, 1)
                                                            : List.of(1, 0)));
                                output =
                                        JSON.writeValueAsString(
                                                Map.of(
                                                        "data",
                                                        data,
                                                        "usage",
                                                        Map.of("prompt_tokens", 20)));
                            }
                        } else {
                            CHATS.incrementAndGet();
                            LAST_CHAT.set(text);
                            if (STARTED.get() != null) STARTED.get().countDown();
                            try {
                                if (RELEASE.get() != null)
                                    RELEASE.get().await(15, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            output =
                                    JSON.writeValueAsString(
                                            Map.of(
                                                    "choices",
                                                    List.of(
                                                            Map.of(
                                                                    "message",
                                                                    Map.of("content", ANSWER.get()),
                                                                    "finish_reason",
                                                                    "stop")),
                                                    "usage",
                                                    Map.of(
                                                            "prompt_tokens",
                                                            100,
                                                            "completion_tokens",
                                                            40)));
                        }
                        byte[] bytes = output.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(status, bytes.length);
                        exchange.getResponseBody().write(bytes);
                        exchange.close();
                    });
            server.setExecutor(
                    Executors.newCachedThreadPool(
                            r -> {
                                var t = new Thread(r);
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
    static void config(DynamicPropertyRegistry r) {
        r.add("app.ai.mode", () -> "compatible");
        r.add("app.ai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/v1");
        r.add("app.ai.api-key", () -> "fixture-key");
        r.add("app.rag.embedding-enabled", () -> true);
        r.add("app.rag.input-price-per-million", () -> "1");
        r.add("app.rag.output-price-per-million", () -> "2");
        r.add("app.rag.embedding-price-per-million", () -> "0.5");
    }

    @Autowired RagService service;
    @Autowired RagIndexService index;
    @Autowired RagModelClient model;
    @Autowired KnowledgePointRepository knowledge;
    @Autowired QuestionService questions;
    @Autowired JdbcTemplate db;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    @Autowired AiTaskService ai;
    private UUID session;
    private Long root, child, question;
    private final List<Long> extraQuestions = new ArrayList<>();
    private final List<UUID> indexIds = new ArrayList<>();
    private int previous;

    @BeforeEach
    void setup() {
        assertEquals(
                "wrong_question_system_test", db.queryForObject("SELECT DATABASE()", String.class));
        previous = service.configuration().callsToday();
        db.update("DELETE FROM ai_daily_usage WHERE business_date=?", LocalDate.now(clock));
        CHATS.set(0);
        EMBEDS.set(0);
        EMBED_FAIL.set(false);
        STARTED.set(null);
        RELEASE.set(null);
        ANSWER.set(
                JSON.writeValueAsString(
                        Map.of(
                                "answer",
                                "二分查找需要检查边界条件 [S1]",
                                "citations",
                                List.of("S1"),
                                "insufficientEvidence",
                                false)));
        var r = knowledge.saveAndFlush(new KnowledgePoint("RAG测试-" + UUID.randomUUID(), null));
        root = r.getId();
        child = knowledge.saveAndFlush(new KnowledgePoint("二分查找", r)).getId();
        question =
                questions
                        .create(
                                new CreateQuestionRequest(
                                        "二分查找为什么会出现边界错误？",
                                        "left <= right 写错",
                                        "检查区间是否为空",
                                        "循环边界必须与区间定义一致，$O(\\log n)$",
                                        "忽略边界",
                                        List.of(child)))
                        .id();
        session = UUID.randomUUID();
        service.createSession(session);
    }

    @AfterEach
    void cleanup() {
        if (RELEASE.get() != null) RELEASE.get().countDown();
        db.update("DELETE FROM rag_session WHERE id=?", session.toString());
        for (UUID id : indexIds) db.update("DELETE FROM rag_request WHERE id=?", id.toString());
        if (db.queryForObject("SELECT COUNT(*) FROM question WHERE id=?", Long.class, question) > 0)
            questions.delete(question);
        for (Long id : extraQuestions) questions.delete(id);
        knowledge.deleteById(child);
        knowledge.deleteById(root);
        db.update(
                "INSERT INTO ai_daily_usage(business_date,call_count) VALUES (?,?) ON DUPLICATE KEY"
                        + " UPDATE call_count=?",
                LocalDate.now(clock),
                previous,
                previous);
    }

    private UUID ask(String text) {
        var id = UUID.randomUUID();
        service.ask(id, session, new RagDtos.Ask(text, root, null, null, false));
        return id;
    }

    private RagDtos.Request run(String text) {
        var id = ask(text);
        assertTrue(service.executeNext());
        return service.get(id);
    }

    @Test
    void generatesACitedAnswerWithThreeAuditedCalls() {
        var r = run("二分查找边界");
        assertEquals("SUCCEEDED", r.status());
        assertEquals("GENERATED", r.answerType());
        assertEquals(List.of("S1"), r.citations());
        assertEquals(question, r.sources().getFirst().questionId());
        assertEquals("HYBRID", r.retrievalMethod());
        assertEquals(3, service.calls(UUID.fromString(r.id())).size());
        assertEquals(3, service.configuration().callsToday());
        assertNotNull(service.calls(UUID.fromString(r.id())).getFirst().estimatedCost());
        assertTrue(
                ai.allCalls(0, 50, null).items().stream()
                        .anyMatch(
                                c ->
                                        c.taskId().equals(r.id())
                                                && c.kind().equals("RAG")
                                                && c.targetPath().contains(r.id())));
    }

    @Test
    void unchangedChunksReuseEmbeddings() {
        run("二分查找边界");
        int before = EMBEDS.get();
        run("二分查找如何检查边界");
        assertEquals(1, EMBEDS.get() - before);
        assertEquals(1, index.status(root).indexedQuestionCount());
    }

    @Test
    void updatesInvalidateVectorsAndHistoricalSources() {
        var old = run("二分查找边界");
        db.update("UPDATE question SET analysis='新的边界解释' WHERE id=?", question);
        assertTrue(service.get(UUID.fromString(old.id())).sources().getFirst().changed());
        run("二分查找边界");
        assertEquals(4, EMBEDS.get());
    }

    @Test
    void deletedQuestionsRemoveIndexButKeepHistoricalSnapshot() {
        var r = run("二分查找边界");
        questions.delete(question);
        var source = service.get(UUID.fromString(r.id())).sources().getFirst();
        assertFalse(source.available());
        assertTrue(source.excerpt().contains("边界"));
        assertEquals(
                0L,
                db.queryForObject(
                        "SELECT COUNT(*) FROM rag_chunk WHERE question_id=?",
                        Long.class,
                        question));
    }

    @Test
    void rejectsHallucinatedCitationsAndDoesNotAutomaticallyResend() {
        ANSWER.set(
                JSON.writeValueAsString(
                        Map.of(
                                "answer",
                                "答案 [S99]",
                                "citations",
                                List.of("S99"),
                                "insufficientEvidence",
                                false)));
        var r = run("二分查找边界");
        assertEquals("FAILED", r.status());
        assertEquals("RAG_INVALID_OUTPUT", r.errorCode());
        assertFalse(r.sources().isEmpty());
        assertEquals(1, CHATS.get());
        assertFalse(service.executeNext());
        assertEquals(40L, service.calls(UUID.fromString(r.id())).getFirst().outputTokens());
    }

    @Test
    void insufficientEvidenceUsesFixedServerAnswer() {
        ANSWER.set(
                JSON.writeValueAsString(
                        Map.of(
                                "answer",
                                "没有资料",
                                "citations",
                                List.of(),
                                "insufficientEvidence",
                                true)));
        var r = run("二分查找边界");
        assertEquals("NO_EVIDENCE", r.answerType());
        assertTrue(r.answer().contains("资料不足"));
        assertTrue(r.citations().isEmpty());
    }

    @Test
    void emptyRetrievalNeverCallsGeneration() {
        var r = run("quantumxyz");
        assertEquals("NO_EVIDENCE", r.answerType());
        assertEquals(0, CHATS.get());
        assertTrue(r.sources().isEmpty());
    }

    @Test
    void embeddingFailureFallsBackToActualKeywordSources() {
        EMBED_FAIL.set(true);
        var r = run("二分查找边界");
        assertEquals("SUCCEEDED", r.status());
        assertEquals("KEYWORD", r.retrievalMethod());
        assertFalse(r.warnings().isEmpty());
        assertEquals(question, r.sources().getFirst().questionId());
    }

    @Test
    void exhaustedSharedQuotaPreventsHttp() {
        db.update(
                "INSERT INTO ai_daily_usage(business_date,call_count) VALUES (?,20)",
                LocalDate.now(clock));
        var r = run("二分查找边界");
        assertEquals("AI_DAILY_LIMIT", r.errorCode());
        assertEquals(0, CHATS.get() + EMBEDS.get());
        assertTrue(service.calls(UUID.fromString(r.id())).isEmpty());
    }

    @Test
    void stableRequestIdReturnsSameRequestAndRejectsChangedInput() {
        UUID id = ask("二分查找边界");
        assertEquals(
                id.toString(),
                service.ask(id, session, new RagDtos.Ask("二分查找边界", root, null, null, false)).id());
        assertEquals(
                "RAG_IDEMPOTENCY_CONFLICT",
                assertThrows(
                                AiException.class,
                                () ->
                                        service.ask(
                                                id,
                                                session,
                                                new RagDtos.Ask("其他问题", root, null, null, false)))
                        .getCode());
        assertEquals(
                "RAG_SESSION_BUSY", assertThrows(AiException.class, () -> ask("另一个问题")).getCode());
    }

    @Test
    void validatesScopesBeforeAcceptingWork() {
        assertThrows(
                AiException.class,
                () ->
                        service.ask(
                                UUID.randomUUID(),
                                session,
                                new RagDtos.Ask("边界", root, "OTHER", null, false)));
        assertThrows(
                AiException.class,
                () ->
                        service.ask(
                                UUID.randomUUID(),
                                session,
                                new RagDtos.Ask("边界", root, null, Long.MAX_VALUE, false)));
        UUID id = UUID.randomUUID();
        service.ask(id, session, new RagDtos.Ask("边界", root, "MASTERED", null, false));
        service.executeNext();
        assertTrue(service.get(id).sources().isEmpty());
        assertEquals(0, CHATS.get());
    }

    @Test
    void cancellationDiscardsLateAnswerAndTracksSentCall() throws Exception {
        UUID id = ask("二分查找边界");
        STARTED.set(new CountDownLatch(1));
        RELEASE.set(new CountDownLatch(1));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = executor.submit(service::executeNext);
            assertTrue(STARTED.get().await(8, TimeUnit.SECONDS));
            service.cancel(id);
            RELEASE.get().countDown();
            pending.get(8, TimeUnit.SECONDS);
            assertEquals("CANCELLED", service.get(id).status());
            assertNull(service.get(id).answer());
            assertEquals("CANCELLED", service.calls(id).getFirst().status());
        } finally {
            RELEASE.get().countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void changedSourceDuringGenerationPreventsPublishingAnswer() throws Exception {
        UUID id = ask("二分查找边界");
        STARTED.set(new CountDownLatch(1));
        RELEASE.set(new CountDownLatch(1));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = executor.submit(service::executeNext);
            assertTrue(STARTED.get().await(8, TimeUnit.SECONDS));
            db.update("UPDATE question SET analysis='修改后的解析' WHERE id=?", question);
            RELEASE.get().countDown();
            pending.get(8, TimeUnit.SECONDS);
            assertEquals("RAG_SOURCE_CHANGED", service.get(id).errorCode());
            assertNull(service.get(id).answer());
            assertEquals("SUCCEEDED", service.calls(id).getFirst().status());
        } finally {
            RELEASE.get().countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void recoveryRequiresManualRetryAndUsesObservedRetryCount() {
        UUID id = ask("二分查找边界");
        db.update("UPDATE rag_request SET status='RUNNING' WHERE id=?", id.toString());
        service.recoverInterrupted();
        assertEquals("RAG_PROCESS_INTERRUPTED", service.get(id).errorCode());
        assertFalse(service.executeNext());
        service.retry(id, 0);
        service.retry(id, 0);
        assertEquals(1, service.get(id).retryCount());
        service.executeNext();
        assertEquals("SUCCEEDED", service.get(id).status());
    }

    @Test
    void explicitFollowUpUsesPreviousQuestionAsRetrievalContext() {
        run("二分查找边界");
        var id = UUID.randomUUID();
        service.ask(id, session, new RagDtos.Ask("如何避免这种错误", root, null, null, true));
        service.executeNext();
        assertTrue(LAST_CHAT.get().contains("上一问题：二分查找边界"));
        assertTrue(LAST_CHAT.get().contains("追问：如何避免这种错误"));
    }

    @Test
    void manualIndexTaskBuildsVectorsWithoutGenerating() {
        var id = UUID.randomUUID();
        indexIds.add(id);
        service.build(id, root);
        service.executeNext();
        assertEquals("INDEX", service.get(id).answerType());
        assertEquals(1, EMBEDS.get());
        assertEquals(0, CHATS.get());
        assertEquals(1, index.status(root).embeddedChunkCount());
    }

    @Test
    void parserRejectsMissingInlineCitationsAndBadVectors() {
        var source =
                new RagDtos.Source(
                        "S1",
                        question,
                        0,
                        "题目",
                        "科目",
                        List.of(),
                        "内容",
                        1,
                        "KEYWORD",
                        "hash",
                        LocalDateTime.now(),
                        true,
                        false);
        assertThrows(
                AiException.class,
                () ->
                        model.parseAnswer(
                                "{\"answer\":\"无引用的回答\",\"citations\":[\"S1\"],\"insufficientEvidence\":false}",
                                List.of(source),
                                10L,
                                20L));
        assertThrows(AiException.class, () -> model.parseVector(JSON.readTree("[0,0]")));
        assertThrows(AiException.class, () -> model.parseVector(JSON.readTree("[1,\"oops\"]")));
    }

    @Test
    void apiValidatesUuidAndEmptyQuestions() throws Exception {
        mvc.perform(
                        post("/api/rag/sessions/" + session + "/requests")
                                .header("Idempotency-Key", UUID.randomUUID())
                                .contentType("application/json")
                                .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/rag/requests/not-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/rag/sessions").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentDuplicateSubmissionsCreateOneRequest() throws Exception {
        UUID id = UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<RagDtos.Request>> pending = new ArrayList<>();
            for (int i = 0; i < 6; i++)
                pending.add(
                        pool.submit(
                                () ->
                                        service.ask(
                                                id,
                                                session,
                                                new RagDtos.Ask(
                                                        "二分查找边界", root, null, null, false))));
            for (var result : pending)
                assertEquals(id.toString(), result.get(8, TimeUnit.SECONDS).id());
            assertEquals(1, service.requests(session, 0, 20).totalElements());
            assertTrue(service.executeNext());
            assertFalse(service.executeNext());
            assertEquals(1, CHATS.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentWorkersNeverExceedTheSharedDailyLimit() throws Exception {
        var other = UUID.randomUUID();
        service.createSession(other);
        var pool = Executors.newFixedThreadPool(2);
        try {
            db.update(
                    "INSERT INTO ai_daily_usage(business_date,call_count) VALUES (?,19)",
                    LocalDate.now(clock));
            UUID first = ask("二分查找边界"), second = UUID.randomUUID();
            service.ask(second, other, new RagDtos.Ask("二分查找边界", root, null, null, false));
            var a = pool.submit(service::executeNext);
            var b = pool.submit(service::executeNext);
            assertTrue(a.get(10, TimeUnit.SECONDS));
            assertTrue(b.get(10, TimeUnit.SECONDS));
            assertEquals(20, service.configuration().callsToday());
            assertEquals(1, EMBEDS.get());
            assertEquals(0, CHATS.get());
            assertEquals("AI_DAILY_LIMIT", service.get(first).errorCode());
            assertEquals("AI_DAILY_LIMIT", service.get(second).errorCode());
        } finally {
            pool.shutdownNow();
            db.update("DELETE FROM rag_session WHERE id=?", other.toString());
        }
    }

    @Test
    void idempotentLookupSurvivesSourceDeletion() {
        var id = UUID.randomUUID();
        var input = new RagDtos.Ask("二分查找边界", root, null, question, false);
        service.ask(id, session, input);
        service.executeNext();
        questions.delete(question);
        assertEquals(id.toString(), service.ask(id, session, input).id());
        assertFalse(service.ask(id, session, input).sources().getFirst().available());
    }

    @Test
    void partialIndexRespectsTheBatchBudgetAndReportsRemainingVectors() {
        db.update(
                "UPDATE question SET"
                    + " question_text=?,analysis=?,wrong_answer=?,correct_answer=?,error_reason=?"
                    + " WHERE id=?",
                "二分查找边界".repeat(1600),
                "二分查找边界".repeat(1600),
                "错误边界".repeat(1000),
                "正确边界".repeat(1000),
                "边界".repeat(900),
                question);
        extraQuestions.add(
                questions
                        .create(
                                new CreateQuestionRequest(
                                        "另一道二分查找边界题",
                                        "错误",
                                        "正确",
                                        "二分查找边界".repeat(1600),
                                        "边界遗漏",
                                        List.of(child)))
                        .id());
        var id = UUID.randomUUID();
        indexIds.add(id);
        service.build(id, root);
        service.executeNext();
        assertEquals("SUCCEEDED", service.get(id).status());
        assertEquals(3, EMBEDS.get());
        assertEquals(48, index.status(root).embeddedChunkCount());
        assertTrue(index.status(root).chunkCount() > 48);
        assertTrue(service.get(id).warnings().stream().anyMatch(note -> note.contains("尚未建立向量")));
    }
}
