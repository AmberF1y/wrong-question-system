package com.wrongquestion.backend.ai;

import static com.wrongquestion.backend.ai.AiDtos.*;

import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.dto.CreateQuestionRequest;
import com.wrongquestion.backend.question.dto.QuestionDetailResponse;
import com.wrongquestion.backend.question.image.service.QuestionImageService;
import com.wrongquestion.backend.question.service.QuestionService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.math.*;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

@Service
public class AiTaskService {
    private static final String ALL_CALLS =
            """
SELECT c.*,t.file_name,'IMAGE' kind,'RECOGNITION' purpose FROM ai_call_record c JOIN ai_task t ON t.id=c.task_id
UNION ALL
SELECT c.id,c.request_id task_id,c.attempt,c.provider,c.model,c.status,c.error_code,c.input_tokens,c.output_tokens,c.duration_ms,c.estimated_cost,c.created_at,c.completed_at,
LEFT(r.question_text,100) file_name,'RAG' kind,c.purpose FROM rag_call_record c JOIN rag_request r ON r.id=c.request_id
""";
    private static final Set<String> STATUSES =
            Set.of(
                    "QUEUED",
                    "RUNNING",
                    "RETRY_WAIT",
                    "SUCCEEDED",
                    "FAILED",
                    "CANCELLED",
                    "APPLIED");
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final AiProperties config;
    private final AiImageStorage images;
    private final AiVisionClient client;
    private final KnowledgePointRepository knowledge;
    private final QuestionService questions;
    private final QuestionImageService questionImages;
    private final Clock clock;

    record Row(
            String id,
            String status,
            String imagePath,
            String hash,
            String fileName,
            String mime,
            long size,
            Long rootId,
            String context,
            String provider,
            String model,
            int attempts,
            int roundAttempts,
            int retries,
            String draft,
            String errorCode,
            String errorMessage,
            Instant available,
            Instant created,
            Instant updated,
            Instant applied,
            Long questionId) {}

    public AiTaskService(
            JdbcTemplate db,
            TransactionTemplate tx,
            ObjectMapper json,
            AiProperties config,
            AiImageStorage images,
            AiVisionClient client,
            KnowledgePointRepository knowledge,
            QuestionService questions,
            QuestionImageService questionImages,
            Clock clock) {
        this.db = db;
        this.tx = tx;
        this.json = json;
        this.config = config;
        this.images = images;
        this.client = client;
        this.knowledge = knowledge;
        this.questions = questions;
        this.questionImages = questionImages;
        this.clock = clock;
    }

    public Configuration configuration() {
        Integer count =
                db.query(
                        "SELECT call_count FROM ai_daily_usage WHERE business_date=?",
                        rs -> rs.next() ? rs.getInt(1) : 0,
                        LocalDate.now(clock));
        return new Configuration(
                client.available(),
                "demo".equals(config.mode()),
                config.model(),
                config.dailyCallLimit(),
                count,
                config.concurrency(),
                config.maxAttempts(),
                config.maxManualRetries());
    }

    public Task create(UUID id, MultipartFile file, Long rootId) {
        var input = images.validate(file);
        Row existing = find(id.toString(), false, false);
        if (existing != null) return matching(existing, input, rootId);
        client.assertAvailable();
        String key = images.newKey(input);
        try {
            return tx.execute(
                    status -> {
                        Row repeated = find(id.toString(), false, false);
                        if (repeated != null) return matching(repeated, input, rootId);
                        if (count(
                                        "SELECT COUNT(*) FROM ai_task WHERE status IN"
                                                + " ('QUEUED','RUNNING','RETRY_WAIT')")
                                >= 50)
                            throw new AiException("AI_QUEUE_FULL", "识别任务较多，请等待已有任务完成后再提交", 429);
                        String context = json.writeValueAsString(knowledgeSnapshot(rootId, true));
                        Instant now = clock.instant();
                        db.update(
                                """
INSERT INTO ai_task(id,status,image_path,image_sha256,file_name,content_type,image_size,root_id,
knowledge_context,provider,model,available_at,created_at,updated_at)
VALUES (?,'QUEUED',?,?,?,?,?,?,?,?,?,?,?,?)
""",
                                id.toString(),
                                key,
                                input.sha256(),
                                input.fileName(),
                                input.contentType(),
                                input.bytes().length,
                                rootId,
                                context,
                                config.mode(),
                                config.model(),
                                time(now),
                                time(now),
                                time(now));
                        images.store(key, input.bytes());
                        return view(find(id.toString(), false, true), true);
                    });
        } catch (DuplicateKeyException e) {
            deleteQuietly(key);
            return matching(find(id.toString(), false, true), input, rootId);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    private Task matching(Row task, AiImageStorage.Input input, Long rootId) {
        if (!task.hash().equals(input.sha256()) || !Objects.equals(task.rootId(), rootId))
            throw conflict("AI_IDEMPOTENCY_CONFLICT", "同一请求标识已用于另一张图片或科目范围");
        return view(task, true);
    }

    public Task get(UUID id) {
        return view(find(id.toString(), false, true), true);
    }

    public TaskPage list(int page, int size, String status) {
        if (page < 0 || page > 100000 || size < 1 || size > 50)
            throw new AiException("AI_INVALID_PAGE", "分页范围不正确", 400);
        if (status != null && !STATUSES.contains(status))
            throw new AiException("AI_INVALID_STATUS", "任务状态不正确", 400);
        String where = status == null ? "" : " WHERE status=?";
        long total =
                status == null
                        ? count("SELECT COUNT(*) FROM ai_task")
                        : count("SELECT COUNT(*) FROM ai_task" + where, status);
        List<Row> rows =
                status == null
                        ? db.query(
                                "SELECT * FROM ai_task ORDER BY created_at DESC,id DESC LIMIT ?"
                                        + " OFFSET ?",
                                this::row,
                                size,
                                (long) page * size)
                        : db.query(
                                "SELECT * FROM ai_task"
                                        + where
                                        + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                                this::row,
                                status,
                                size,
                                (long) page * size);
        return new TaskPage(
                rows.stream().map(r -> view(r, false)).toList(),
                page,
                size,
                total,
                (total + size - 1) / size);
    }

    public List<Call> calls(UUID id) {
        find(id.toString(), false, true);
        return db.query(
                "SELECT * FROM ai_call_record WHERE task_id=? ORDER BY attempt DESC",
                this::call,
                id.toString());
    }

    public CallPage allCalls(int page, int size, String status) {
        if (page < 0 || page > 100000 || size < 1 || size > 50)
            throw new AiException("AI_INVALID_PAGE", "分页范围不正确", 400);
        if (status != null
                && !Set.of("RUNNING", "SUCCEEDED", "FAILED", "INTERRUPTED", "CANCELLED")
                        .contains(status))
            throw new AiException("AI_INVALID_STATUS", "调用状态不正确", 400);
        String where = status == null ? "" : " WHERE c.status=?";
        long total =
                status == null
                        ? count("SELECT COUNT(*) FROM (" + ALL_CALLS + ") c")
                        : count("SELECT COUNT(*) FROM (" + ALL_CALLS + ") c" + where, status);
        String sql =
                "SELECT c.* FROM ("
                        + ALL_CALLS
                        + ") c"
                        + where
                        + " ORDER BY c.created_at DESC,c.kind,c.id DESC LIMIT ? OFFSET ?";
        org.springframework.jdbc.core.RowMapper<CallEntry> mapper =
                (rs, index) ->
                        new CallEntry(
                                rs.getString("task_id"),
                                rs.getString("file_name"),
                                call(rs, index),
                                rs.getString("kind"),
                                "RAG".equals(rs.getString("kind"))
                                        ? "/learning-assistant?request=" + rs.getString("task_id")
                                        : "/ai/tasks/" + rs.getString("task_id"),
                                rs.getString("purpose"));
        List<CallEntry> items =
                status == null
                        ? db.query(sql, mapper, size, (long) page * size)
                        : db.query(sql, mapper, status, size, (long) page * size);
        return new CallPage(items, page, size, total, (total + size - 1) / size);
    }

    private Call call(ResultSet rs, int index) throws SQLException {
        return new Call(
                rs.getLong("id"),
                rs.getInt("attempt"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("status"),
                rs.getString("error_code"),
                nullableLong(rs, "input_tokens"),
                nullableLong(rs, "output_tokens"),
                nullableLong(rs, "duration_ms"),
                rs.getBigDecimal("estimated_cost"),
                instant(rs, "created_at"),
                instant(rs, "completed_at"));
    }

    public Statistics statistics() {
        var aggregate =
                db.queryForMap(
                        """
SELECT COUNT(*) calls,SUM(status IN ('FAILED','INTERRUPTED')) failures,
SUM(input_tokens) input_tokens,SUM(output_tokens) output_tokens,
AVG(duration_ms) duration_ms,SUM(estimated_cost) estimated_cost FROM (
"""
                                + ALL_CALLS
                                + ") c");
        return new Statistics(
                count("SELECT COUNT(*) FROM ai_task"),
                count(
                        "SELECT COUNT(*) FROM ai_task WHERE status IN"
                                + " ('QUEUED','RUNNING','RETRY_WAIT')"),
                count("SELECT COUNT(*) FROM ai_task WHERE status IN ('SUCCEEDED','APPLIED')"),
                count("SELECT COUNT(*) FROM ai_task WHERE status='FAILED'"),
                number(aggregate.get("calls")),
                number(aggregate.get("failures")),
                optionalNumber(aggregate.get("input_tokens")),
                optionalNumber(aggregate.get("output_tokens")),
                aggregate.get("duration_ms") == null
                        ? null
                        : ((Number) aggregate.get("duration_ms")).doubleValue(),
                (BigDecimal) aggregate.get("estimated_cost"),
                configuration());
    }

    public Task retry(UUID id) {
        return retry(id, get(id).retryCount());
    }

    public Task retry(UUID id, int expectedRetryCount) {
        client.assertAvailable();
        return tx.execute(
                status -> {
                    Row task = find(id.toString(), true, true);
                    if (task.retries() != expectedRetryCount)
                        throw conflict("AI_RETRY_CONCURRENT_MODIFICATION", "任务已被其他请求重试，请刷新任务状态");
                    if (!Set.of("FAILED", "CANCELLED").contains(task.status()))
                        throw conflict("AI_RETRY_NOT_ALLOWED", "只有失败或取消的任务可以重新识别");
                    if (task.retries() >= config.maxManualRetries())
                        throw conflict("AI_RETRY_LIMIT", "该任务已达到人工重试上限，请核对图片或配置后新建任务");
                    if (configuration().callsToday() >= config.dailyCallLimit())
                        throw new AiException("AI_DAILY_LIMIT", "今日 AI 执行额度已用完，请明天重试", 429);
                    images.read(task.imagePath());
                    String context =
                            json.writeValueAsString(knowledgeSnapshot(task.rootId(), true));
                    var now = time(clock.instant());
                    db.update(
                            "UPDATE ai_task SET"
                                + " status='QUEUED',retry_count=retry_count+1,round_attempt_count=0,knowledge_context=?,error_code=NULL,error_message=NULL,available_at=?,updated_at=?"
                                + " WHERE id=?",
                            context,
                            now,
                            now,
                            task.id());
                    return view(find(task.id(), false, true), true);
                });
    }

    public Task cancel(UUID id) {
        return tx.execute(
                status -> {
                    Row task = find(id.toString(), true, true);
                    if (!Set.of("QUEUED", "RUNNING", "RETRY_WAIT").contains(task.status()))
                        throw conflict("AI_CANCEL_NOT_ALLOWED", "该任务已经结束");
                    db.update(
                            "UPDATE ai_task SET"
                                + " status='CANCELLED',updated_at=?,error_code=NULL,error_message=NULL"
                                + " WHERE id=?",
                            time(clock.instant()),
                            task.id());
                    return view(find(task.id(), false, true), true);
                });
    }

    public QuestionDetailResponse apply(UUID id, CreateQuestionRequest request) {
        return tx.execute(
                status -> {
                    Row task = find(id.toString(), true, true);
                    if ("APPLIED".equals(task.status())) {
                        if (task.questionId() == null)
                            throw conflict("AI_APPLIED_QUESTION_DELETED", "此草稿已保存过，其题目现已删除");
                        return questions.getById(task.questionId());
                    }
                    if (!"SUCCEEDED".equals(task.status()))
                        throw conflict("AI_DRAFT_NOT_READY", "识别完成后才能确认保存草稿");
                    byte[] image = images.read(task.imagePath());
                    var detail = questions.create(request);
                    questionImages.uploadOrReplace(
                            detail.id(), new ImageFile(image, task.fileName(), task.mime()));
                    var now = time(clock.instant());
                    db.update(
                            "UPDATE ai_task SET"
                                + " status='APPLIED',question_id=?,applied_at=?,updated_at=? WHERE"
                                + " id=?",
                            detail.id(),
                            now,
                            now,
                            task.id());
                    return questions.getById(detail.id());
                });
    }

    public void delete(UUID id) {
        String key =
                tx.execute(
                        status -> {
                            Row task = find(id.toString(), true, true);
                            if (Set.of("QUEUED", "RUNNING", "RETRY_WAIT").contains(task.status()))
                                throw conflict("AI_DELETE_ACTIVE", "请先取消正在处理的任务");
                            db.update("DELETE FROM ai_task WHERE id=?", task.id());
                            return task.imagePath();
                        });
        deleteQuietly(key);
    }

    public record Image(byte[] bytes, String contentType) {}

    public Image image(UUID id) {
        Row task = find(id.toString(), false, true);
        return new Image(images.read(task.imagePath()), task.mime());
    }

    /** Single-instance recovery: never silently resubmit a possibly billed interrupted request. */
    public void recoverInterrupted() {
        tx.executeWithoutResult(
                status -> {
                    var now = time(clock.instant());
                    db.update(
                            "UPDATE ai_call_record SET"
                                + " status='INTERRUPTED',error_code='AI_PROCESS_INTERRUPTED',completed_at=?"
                                + " WHERE status='RUNNING'",
                            now);
                    db.update(
                            "UPDATE ai_task SET"
                                + " status='FAILED',error_code='AI_PROCESS_INTERRUPTED',error_message='后端重启中断了识别，调用可能已计费，请人工决定是否重试',updated_at=?"
                                + " WHERE status='RUNNING'",
                            now);
                });
    }

    public void recoverExpired() {
        tx.executeWithoutResult(
                status -> {
                    var expired =
                            db.query(
                                    "SELECT * FROM ai_task WHERE status='RUNNING' AND updated_at<?"
                                            + " ORDER BY id FOR UPDATE",
                                    this::row,
                                    time(
                                            clock.instant()
                                                    .minusSeconds(config.timeoutSeconds() + 60L)));
                    for (Row task : expired) {
                        db.update(
                                "UPDATE ai_call_record SET"
                                    + " status='INTERRUPTED',error_code='AI_TASK_STALLED',completed_at=?"
                                    + " WHERE task_id=? AND status='RUNNING'",
                                time(clock.instant()),
                                task.id());
                        failWithoutCall(
                                task,
                                new AiException(
                                        "AI_TASK_STALLED", "识别处理已中断，调用可能已计费，请人工决定是否重试", 503));
                    }
                });
    }

    public boolean executeNext() {
        Row task =
                tx.execute(
                        status -> {
                            var candidates =
                                    db.query(
                                            """
SELECT * FROM ai_task WHERE status IN ('QUEUED','RETRY_WAIT') AND available_at<=?
ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
""",
                                            this::row,
                                            time(clock.instant()));
                            if (candidates.isEmpty()) return null;
                            Row selected = candidates.getFirst();
                            try {
                                client.assertAvailable();
                            } catch (AiException e) {
                                failWithoutCall(selected, e);
                                return null;
                            }
                            LocalDate today = LocalDate.now(clock);
                            // Acquire an exclusive quota-row lock immediately. INSERT IGNORE takes
                            // a shared
                            // duplicate-key lock, whose later upgrade can deadlock concurrent
                            // workers.
                            db.update(
                                    "INSERT INTO ai_daily_usage(business_date,call_count) VALUES"
                                            + " (?,0) ON DUPLICATE KEY UPDATE"
                                            + " call_count=ai_daily_usage.call_count",
                                    today);
                            if (db.update(
                                            "UPDATE ai_daily_usage SET call_count=call_count+1"
                                                    + " WHERE business_date=? AND call_count<?",
                                            today,
                                            config.dailyCallLimit())
                                    == 0) {
                                failWithoutCall(
                                        selected,
                                        new AiException(
                                                "AI_DAILY_LIMIT", "今日 AI 执行额度已用完，请明天重试", 429));
                                return null;
                            }
                            int attempt = selected.attempts() + 1;
                            var now = time(clock.instant());
                            db.update(
                                    "UPDATE ai_task SET"
                                        + " status='RUNNING',attempt_count=?,round_attempt_count=round_attempt_count+1,provider=?,model=?,updated_at=?,error_code=NULL,error_message=NULL"
                                        + " WHERE id=?",
                                    attempt,
                                    config.mode(),
                                    config.model(),
                                    now,
                                    selected.id());
                            db.update(
                                    "INSERT INTO"
                                        + " ai_call_record(task_id,attempt,provider,model,status,created_at)"
                                        + " VALUES (?,?,?,?,'RUNNING',?)",
                                    selected.id(),
                                    attempt,
                                    config.mode(),
                                    config.model(),
                                    now);
                            return find(selected.id(), false, true);
                        });
        if (task == null) return false;
        long started = System.nanoTime();
        AiVisionClient.Result result = null;
        AiException failure = null;
        try {
            result = client.recognize(images.read(task.imagePath()), task.mime(), task.context());
        } catch (AiException e) {
            failure = e;
        } catch (RuntimeException e) {
            failure = new AiException("AI_EXECUTION_FAILED", "识别处理失败，请检查服务配置或人工重试", 500);
        }
        finish(task, result, failure, (System.nanoTime() - started) / 1_000_000);
        return true;
    }

    private void finish(
            Row task, AiVisionClient.Result result, AiException failure, long duration) {
        tx.executeWithoutResult(
                status -> {
                    Row current = find(task.id(), true, false);
                    if (current == null) return;
                    boolean cancelled =
                            !"RUNNING".equals(current.status())
                                    || current.attempts() != task.attempts();
                    var now = time(clock.instant());
                    Long input =
                            result != null
                                    ? result.inputTokens()
                                    : failure == null ? null : failure.getInputTokens();
                    Long output =
                            result != null
                                    ? result.outputTokens()
                                    : failure == null ? null : failure.getOutputTokens();
                    BigDecimal cost = estimate(input, output);
                    db.update(
                            """
UPDATE ai_call_record SET status=?,error_code=?,input_tokens=?,output_tokens=?,duration_ms=?,estimated_cost=?,completed_at=?
WHERE task_id=? AND attempt=? AND status='RUNNING'
""",
                            cancelled ? "CANCELLED" : failure == null ? "SUCCEEDED" : "FAILED",
                            failure == null ? null : failure.getCode(),
                            input,
                            output,
                            duration,
                            cost,
                            now,
                            task.id(),
                            task.attempts());
                    if (cancelled) return;
                    if (failure == null) {
                        Draft valid = validateSuggestions(result.draft(), task);
                        db.update(
                                "UPDATE ai_task SET status='SUCCEEDED',draft_json=?,updated_at=?"
                                        + " WHERE id=?",
                                json.writeValueAsString(valid),
                                now,
                                task.id());
                    } else {
                        int roundAttempt = task.roundAttempts();
                        boolean again =
                                failure.isRetryable() && roundAttempt < config.maxAttempts();
                        Instant available =
                                clock.instant()
                                        .plusSeconds(
                                                again
                                                        ? Math.min(
                                                                60, 5L * Math.max(1, roundAttempt))
                                                        : 0);
                        db.update(
                                "UPDATE ai_task SET"
                                    + " status=?,error_code=?,error_message=?,available_at=?,updated_at=?"
                                    + " WHERE id=?",
                                again ? "RETRY_WAIT" : "FAILED",
                                failure.getCode(),
                                failure.getMessage(),
                                time(available),
                                now,
                                task.id());
                    }
                });
    }

    private Draft validateSuggestions(Draft draft, Row task) {
        var snapshot = json.readTree(task.context());
        Set<Long> supplied = new HashSet<>();
        for (var node : snapshot) supplied.add(node.path("id").asLong());
        var current = knowledgeSnapshot(null, false);
        Map<Long, Knowledge> byId = new HashMap<>();
        current.forEach(k -> byId.put(k.id(), k));
        List<Long> accepted = new ArrayList<>();
        Long root = task.rootId();
        for (Long id : draft.knowledgePointIds()) {
            Knowledge item = byId.get(id);
            if (item == null || !supplied.contains(id)) continue;
            if (root == null) root = item.rootId();
            if (root.equals(item.rootId()) && accepted.size() < 5) accepted.add(id);
        }
        List<String> warnings = new ArrayList<>(draft.warnings());
        if (accepted.size() != draft.knowledgePointIds().size())
            warnings.add("部分推荐知识点已失效、不在候选范围或跨科目，已移除，请重新核对知识点。");
        return new Draft(
                draft.questionText(), draft.correctAnswer(), draft.analysis(), accepted, warnings);
    }

    private List<Knowledge> knowledgeSnapshot(Long rootId, boolean bounded) {
        var nodes = knowledge.findAllWithParentOrderByIdAsc();
        if (rootId != null
                && nodes.stream().noneMatch(n -> n.getId().equals(rootId) && n.getParent() == null))
            throw new AiException("AI_INVALID_SUBJECT", "所选科目根节点不存在，请刷新知识树", 400);
        List<Knowledge> result = new ArrayList<>();
        for (var node : nodes) {
            var current = node;
            List<String> path = new ArrayList<>();
            while (current != null) {
                path.addFirst(current.getName());
                if (current.getParent() == null) break;
                current = current.getParent();
            }
            if (rootId == null || rootId.equals(current.getId()))
                result.add(new Knowledge(node.getId(), current.getId(), String.join(" / ", path)));
        }
        if (bounded && result.size() > config.maxKnowledgePoints())
            throw new AiException("AI_KNOWLEDGE_LIMIT", "候选知识点较多，请缩小科目范围或调整服务端候选上限", 400);
        return result;
    }

    private void failWithoutCall(Row task, AiException e) {
        db.update(
                "UPDATE ai_task SET status='FAILED',error_code=?,error_message=?,updated_at=? WHERE"
                        + " id=?",
                e.getCode(),
                e.getMessage(),
                time(clock.instant()),
                task.id());
    }

    private BigDecimal estimate(Long input, Long output) {
        if (input == null
                || output == null
                || config.inputPricePerMillion() == null
                || config.outputPricePerMillion() == null) return null;
        return config.inputPricePerMillion()
                .multiply(BigDecimal.valueOf(input))
                .add(config.outputPricePerMillion().multiply(BigDecimal.valueOf(output)))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
    }

    private Row find(String id, boolean lock, boolean required) {
        var rows =
                db.query(
                        "SELECT * FROM ai_task WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                        this::row,
                        id);
        if (rows.isEmpty()) {
            if (required) throw new AiException("AI_TASK_NOT_FOUND", "识别任务不存在", 404);
            return null;
        }
        return rows.getFirst();
    }

    private Row row(ResultSet rs, int index) throws SQLException {
        return new Row(
                rs.getString("id"),
                rs.getString("status"),
                rs.getString("image_path"),
                rs.getString("image_sha256"),
                rs.getString("file_name"),
                rs.getString("content_type"),
                rs.getLong("image_size"),
                nullableLong(rs, "root_id"),
                rs.getString("knowledge_context"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getInt("attempt_count"),
                rs.getInt("round_attempt_count"),
                rs.getInt("retry_count"),
                rs.getString("draft_json"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                instant(rs, "available_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "applied_at"),
                nullableLong(rs, "question_id"));
    }

    private Task view(Row row, boolean includeDraft) {
        Draft draft =
                includeDraft && row.draft() != null
                        ? json.readValue(row.draft(), Draft.class)
                        : null;
        return new Task(
                row.id(),
                row.status(),
                row.fileName(),
                row.mime(),
                row.size(),
                row.rootId(),
                row.provider(),
                row.model(),
                row.attempts(),
                row.retries(),
                draft,
                row.errorCode(),
                row.errorMessage(),
                row.created(),
                row.updated(),
                row.available(),
                row.questionId(),
                row.applied());
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column) == null ? null : rs.getLong(column);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime time(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private long count(String sql, Object... args) {
        return db.queryForObject(sql, Long.class, args);
    }

    private long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private Long optionalNumber(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private AiException conflict(String code, String message) {
        return new AiException(code, message, 409);
    }

    private void deleteQuietly(String key) {
        try {
            images.delete(key);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn(
                            "AI input image cleanup failed; retained orphan can be removed during"
                                    + " maintenance");
        }
    }

    private record ImageFile(byte[] bytes, String name, String mime) implements MultipartFile {
        public String getName() {
            return "file";
        }

        public String getOriginalFilename() {
            return name;
        }

        public String getContentType() {
            return mime;
        }

        public boolean isEmpty() {
            return bytes.length == 0;
        }

        public long getSize() {
            return bytes.length;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        public void transferTo(File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }

        public void transferTo(Path dest) throws IOException {
            java.nio.file.Files.write(dest, bytes);
        }
    }
}
