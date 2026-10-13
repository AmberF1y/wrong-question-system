package com.wrongquestion.backend.rag;

import static com.wrongquestion.backend.rag.RagDtos.*;

import com.wrongquestion.backend.ai.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

@Service
public class RagService {
    private static final String NO_EVIDENCE = "当前错题资料不足以回答这个问题。请补充相关题目、答案或解析，或调整检索范围。";
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final RagProperties config;
    private final AiProperties ai;
    private final RagIndexService index;
    private final RagRetriever retriever;
    private final RagModelClient client;
    private final AiUsageService usage;
    private final Clock clock;

    public RagService(
            JdbcTemplate db,
            TransactionTemplate tx,
            ObjectMapper json,
            RagProperties config,
            AiProperties ai,
            RagIndexService index,
            RagRetriever retriever,
            RagModelClient client,
            AiUsageService usage,
            Clock clock) {
        this.db = db;
        this.tx = tx;
        this.json = json;
        this.config = config;
        this.ai = ai;
        this.index = index;
        this.retriever = retriever;
        this.client = client;
        this.usage = usage;
        this.clock = clock;
    }

    public Configuration configuration() {
        int count =
                db.query(
                        "SELECT call_count FROM ai_daily_usage WHERE business_date=?",
                        rs -> rs.next() ? rs.getInt(1) : 0,
                        LocalDate.now(clock));
        return new Configuration(
                client.generationEnabled(),
                "demo".equals(ai.mode()),
                config.model(),
                client.embeddingEnabled(),
                config.embeddingModel(),
                config.topK(),
                count,
                ai.dailyCallLimit(),
                ai.maxManualRetries());
    }

    public Session createSession(UUID id) {
        return tx.execute(
                s -> {
                    db.update(
                            "INSERT INTO rag_session(id,title,created_at,updated_at) VALUES"
                                    + " (?,'新的学习对话',?,?) ON DUPLICATE KEY UPDATE id=rag_session.id",
                            id.toString(),
                            now(),
                            now());
                    return session(id.toString(), false);
                });
    }

    public SessionPage sessions(int page, int size) {
        page(page, size);
        long total = count("SELECT COUNT(*) FROM rag_session");
        return new SessionPage(
                db.query(
                        "SELECT * FROM rag_session ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET"
                                + " ?",
                        this::sessionRow,
                        size,
                        (long) page * size),
                page,
                size,
                total,
                (total + size - 1) / size);
    }

    private Session session(String id, boolean lock) {
        var rows =
                db.query(
                        "SELECT * FROM rag_session WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                        this::sessionRow,
                        id);
        if (rows.isEmpty()) throw new AiException("RAG_SESSION_NOT_FOUND", "学习对话不存在", 404);
        return rows.getFirst();
    }

    private Session sessionRow(ResultSet rs, int i) throws SQLException {
        return new Session(
                rs.getString("id"),
                rs.getString("title"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    public Request ask(UUID id, UUID sessionId, Ask input) {
        if (input.question() == null
                || input.question().isBlank()
                || input.question().length() > 2000)
            throw new AiException("RAG_INVALID_QUESTION", "请输入 1～2000 字的问题", 400);
        Request existing = find(id.toString(), false);
        if (existing != null) {
            matching(existing, sessionId.toString(), input);
            return live(existing);
        }
        var questions = index.questions(input.rootId(), input.reviewStatus());
        if (input.focusQuestionId() != null
                && questions.stream().noneMatch(q -> q.id() == input.focusQuestionId()))
            throw new AiException("RAG_FOCUS_NOT_FOUND", "指定错题不存在或不在当前范围内", 400);
        try {
            return tx.execute(
                    s -> {
                        session(sessionId.toString(), true);
                        Request old = find(id.toString(), false);
                        if (old != null) {
                            matching(old, sessionId.toString(), input);
                            return live(old);
                        }
                        if (count(
                                        "SELECT COUNT(*) FROM rag_request WHERE session_id=? AND"
                                                + " status IN ('QUEUED','RUNNING')",
                                        sessionId.toString())
                                > 0) throw new AiException("RAG_SESSION_BUSY", "当前对话已有问题正在处理", 409);
                        queueLimit();
                        var now = now();
                        String question = input.question().strip();
                        db.update(
                                "INSERT INTO"
                                    + " rag_request(id,session_id,kind,question_text,root_id,review_status,focus_question_id,follow_up,status,phase,created_at,updated_at)"
                                    + " VALUES (?,?,'CHAT',?,?,?,?,?,'QUEUED','QUEUED',?,?)",
                                id.toString(),
                                sessionId.toString(),
                                question,
                                input.rootId(),
                                input.reviewStatus(),
                                input.focusQuestionId(),
                                input.followUp(),
                                now,
                                now);
                        db.update(
                                "UPDATE rag_session SET"
                                    + " title=IF(title='新的学习对话',?,title),updated_at=? WHERE id=?",
                                question.substring(0, Math.min(80, question.length())),
                                now,
                                sessionId.toString());
                        return get(id);
                    });
        } catch (DuplicateKeyException e) {
            var old = required(id.toString(), false);
            matching(old, sessionId.toString(), input);
            return live(old);
        }
    }

    public Request build(UUID id, Long rootId) {
        Request existing = find(id.toString(), false);
        if (existing != null) {
            if (!existing.kind().equals("INDEX") || !Objects.equals(existing.rootId(), rootId))
                throw new AiException("RAG_IDEMPOTENCY_CONFLICT", "该请求编号已用于其他内容", 409);
            return live(existing);
        }
        index.subject(rootId);
        try {
            return tx.execute(
                    s -> {
                        Request old = find(id.toString(), false);
                        if (old != null) {
                            if (!old.kind().equals("INDEX")
                                    || !Objects.equals(old.rootId(), rootId))
                                throw new AiException(
                                        "RAG_IDEMPOTENCY_CONFLICT", "该请求编号已用于其他内容", 409);
                            return live(old);
                        }
                        queueLimit();
                        if (count(
                                        "SELECT COUNT(*) FROM rag_request WHERE kind='INDEX' AND"
                                                + " status IN ('QUEUED','RUNNING')")
                                > 0) throw new AiException("RAG_INDEX_BUSY", "已有索引任务正在处理", 409);
                        db.update(
                                "INSERT INTO"
                                    + " rag_request(id,kind,question_text,root_id,status,phase,created_at,updated_at)"
                                    + " VALUES (?,'INDEX','更新错题索引',?,'QUEUED','QUEUED',?,?)",
                                id.toString(),
                                rootId,
                                now(),
                                now());
                        return get(id);
                    });
        } catch (DuplicateKeyException e) {
            var old = get(id);
            if (!old.kind().equals("INDEX") || !Objects.equals(old.rootId(), rootId))
                throw new AiException("RAG_IDEMPOTENCY_CONFLICT", "该请求编号已用于其他内容", 409);
            return old;
        }
    }

    private void queueLimit() {
        if (count("SELECT COUNT(*) FROM rag_request WHERE status IN ('QUEUED','RUNNING')") >= 100)
            throw new AiException("RAG_QUEUE_FULL", "学习助手队列已满，请稍后重试", 429);
    }

    private void matching(Request old, String session, Ask input) {
        if (!old.kind().equals("CHAT")
                || !Objects.equals(old.sessionId(), session)
                || !old.question().equals(input.question().strip())
                || !Objects.equals(old.rootId(), input.rootId())
                || !Objects.equals(old.reviewStatus(), input.reviewStatus())
                || !Objects.equals(old.focusQuestionId(), input.focusQuestionId())
                || old.followUp() != input.followUp())
            throw new AiException("RAG_IDEMPOTENCY_CONFLICT", "该请求编号已用于其他问题", 409);
    }

    public Request get(UUID id) {
        var r = find(id.toString(), false);
        if (r == null) throw new AiException("RAG_REQUEST_NOT_FOUND", "学习请求不存在", 404);
        return live(r);
    }

    public RequestPage requests(UUID sessionId, int page, int size) {
        page(page, size);
        session(sessionId.toString(), false);
        long total =
                count("SELECT COUNT(*) FROM rag_request WHERE session_id=?", sessionId.toString());
        var rows =
                db.query(
                        "SELECT * FROM rag_request WHERE session_id=? ORDER BY created_at DESC,id"
                                + " DESC LIMIT ? OFFSET ?",
                        this::row,
                        sessionId.toString(),
                        size,
                        (long) page * size);
        return new RequestPage(
                rows.stream().map(this::live).toList(),
                page,
                size,
                total,
                (total + size - 1) / size);
    }

    public void deleteSession(UUID id) {
        tx.executeWithoutResult(
                s -> {
                    session(id.toString(), true);
                    if (count(
                                    "SELECT COUNT(*) FROM rag_request r WHERE session_id=? AND"
                                        + " (status IN ('QUEUED','RUNNING') OR EXISTS(SELECT 1 FROM"
                                        + " rag_call_record c WHERE c.request_id=r.id AND"
                                        + " c.status='RUNNING'))",
                                    id.toString())
                            > 0) throw new AiException("RAG_DELETE_ACTIVE", "请等待处理结束后删除对话", 409);
                    db.update("DELETE FROM rag_session WHERE id=?", id.toString());
                });
    }

    public Request cancel(UUID id) {
        return tx.execute(
                s -> {
                    var r = required(id.toString(), true);
                    if (!Set.of("QUEUED", "RUNNING").contains(r.status()))
                        throw new AiException("RAG_CANCEL_NOT_ALLOWED", "该请求已经结束", 409);
                    db.update(
                            "UPDATE rag_request SET"
                                    + " status='CANCELLED',phase='DONE',completed_at=?,updated_at=?"
                                    + " WHERE id=?",
                            now(),
                            now(),
                            id.toString());
                    return get(id);
                });
    }

    public Request retry(UUID id, int expected) {
        return tx.execute(
                s -> {
                    var before = required(id.toString(), false);
                    if (before.sessionId() != null) session(before.sessionId(), true);
                    var r = required(id.toString(), true);
                    if (r.retryCount() != expected) return live(r);
                    if (!r.status().equals("FAILED")
                            || count(
                                            "SELECT COUNT(*) FROM rag_call_record WHERE"
                                                    + " request_id=? AND status='RUNNING'",
                                            r.id())
                                    > 0)
                        throw new AiException("RAG_RETRY_NOT_ALLOWED", "请求尚未结束或无法重试", 409);
                    if (expected >= ai.maxManualRetries())
                        throw new AiException("RAG_RETRY_LIMIT", "人工重试次数已用完", 409);
                    queueLimit();
                    if (r.sessionId() != null) {
                        if (count(
                                        "SELECT COUNT(*) FROM rag_request WHERE session_id=? AND"
                                                + " status IN ('QUEUED','RUNNING')",
                                        r.sessionId())
                                > 0) throw new AiException("RAG_SESSION_BUSY", "当前对话已有问题正在处理", 409);
                    }
                    db.update(
                            "UPDATE rag_request SET"
                                + " status='QUEUED',phase='QUEUED',retry_count=retry_count+1,error_code=NULL,error_message=NULL,answer=NULL,answer_type=NULL,citations_json=NULL,sources_json=NULL,warnings_json=NULL,completed_at=NULL,updated_at=?"
                                + " WHERE id=?",
                            now(),
                            r.id());
                    return get(id);
                });
    }

    public List<AiDtos.Call> calls(UUID id) {
        required(id.toString(), false);
        return db.query(
                "SELECT * FROM rag_call_record WHERE request_id=? ORDER BY attempt DESC",
                (rs, i) ->
                        new AiDtos.Call(
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
                                instant(rs, "completed_at")),
                id.toString());
    }

    public boolean executeNext() {
        Request task =
                tx.execute(
                        s -> {
                            var rows =
                                    db.query(
                                            "SELECT * FROM rag_request WHERE status='QUEUED' ORDER"
                                                    + " BY created_at,id LIMIT 1 FOR UPDATE SKIP"
                                                    + " LOCKED",
                                            this::row);
                            if (rows.isEmpty()) return null;
                            var r = rows.getFirst();
                            db.update(
                                    "UPDATE rag_request SET"
                                        + " status='RUNNING',phase='INDEXING',updated_at=? WHERE"
                                        + " id=?",
                                    now(),
                                    r.id());
                            return required(r.id(), false);
                        });
        if (task == null) return false;
        try {
            process(task);
        } catch (AiException e) {
            fail(task, e);
        } catch (RuntimeException e) {
            fail(task, new AiException("RAG_EXECUTION_FAILED", "学习请求处理失败，请检查配置或人工重试", 500));
        }
        return true;
    }

    private void process(Request task) {
        check(task, "INDEXING");
        var questions = index.questions(task.rootId(), task.reviewStatus());
        index.refresh(questions);
        List<String> warnings = new ArrayList<>();
        if (task.focusQuestionId() != null
                && questions.stream().noneMatch(q -> q.id() == task.focusQuestionId()))
            throw new AiException("RAG_FOCUS_NOT_FOUND", "指定错题已删除或离开当前范围", 409);
        if (client.embeddingEnabled()) {
            for (int batch = 0; batch < config.embeddingBatchesPerRequest(); batch++) {
                var missing = index.missing(questions);
                if (missing.isEmpty()) break;
                try {
                    var vectors =
                            call(
                                    task,
                                    "EMBEDDING",
                                    () ->
                                            client.embed(
                                                    missing.stream()
                                                            .map(RagIndexService.Chunk::content)
                                                            .toList()));
                    check(task, "INDEXING");
                    index.saveVectors(missing, vectors.values());
                } catch (AiException e) {
                    if ("RAG_CANCELLED".equals(e.getCode())) throw e;
                    warnings.add("向量索引未完成（" + e.getCode() + "），本轮继续使用已有向量和关键词检索。");
                    break;
                }
            }
            if (!index.missing(questions).isEmpty())
                warnings.add("部分片段尚未建立向量索引，可再次更新索引；关键词检索仍可使用。");
        }
        if (task.kind().equals("INDEX")) {
            var state = index.status(task.rootId());
            finish(
                    task,
                    new Result(
                            "索引已更新："
                                    + state.indexedQuestionCount()
                                    + " 道错题，"
                                    + state.chunkCount()
                                    + " 个片段。",
                            "INDEX",
                            List.of(),
                            List.of(),
                            warnings,
                            "KEYWORD"));
            return;
        }
        String query = task.question();
        if (task.followUp() && task.sessionId() != null) {
            var previous =
                    db.query(
                            "SELECT question_text FROM rag_request WHERE session_id=? AND"
                                + " kind='CHAT' AND status='SUCCEEDED' AND created_at<? AND"
                                + " root_id<=>? AND review_status<=>? ORDER BY created_at DESC,id"
                                + " DESC LIMIT 1",
                            (rs, i) -> rs.getString(1),
                            task.sessionId(),
                            time(task.createdAt()),
                            task.rootId(),
                            task.reviewStatus());
            if (!previous.isEmpty()) query = "上一问题：" + previous.getFirst() + "\n追问：" + query;
        }
        double[] queryVector = null;
        if (client.embeddingEnabled() && !questions.isEmpty()) {
            String input = query;
            try {
                queryVector =
                        call(task, "EMBEDDING", () -> client.embed(List.of(input)))
                                .values()
                                .getFirst();
            } catch (AiException e) {
                if ("RAG_CANCELLED".equals(e.getCode())) throw e;
                warnings.add("问题向量获取失败（" + e.getCode() + "），已使用关键词检索。");
            }
        }
        check(task, "RETRIEVING");
        questions = index.questions(task.rootId(), task.reviewStatus());
        var hits =
                retriever.retrieve(
                        query,
                        index.documents(questions),
                        queryVector,
                        config.topK(),
                        config.minimumSimilarity(),
                        task.focusQuestionId());
        Map<Long, RagIndexService.Question> byId = new HashMap<>();
        questions.forEach(q -> byId.put(q.id(), q));
        List<Source> sources = new ArrayList<>();
        for (var hit : hits) {
            var d = hit.document();
            var q = byId.get(d.questionId());
            String preview = q.text().substring(q.text().indexOf("题目：") + 3);
            preview = preview.substring(0, Math.min(100, preview.length()));
            sources.add(
                    new Source(
                            "S" + (sources.size() + 1),
                            q.id(),
                            d.chunkNumber(),
                            preview,
                            q.subject(),
                            q.knowledge(),
                            d.content(),
                            hit.score(),
                            hit.method(),
                            q.hash(),
                            q.updated(),
                            true,
                            false));
        }
        String method =
                sources.stream().anyMatch(s -> Set.of("HYBRID", "SEMANTIC").contains(s.method()))
                        ? "HYBRID"
                        : "KEYWORD";
        persistSources(task, sources, warnings, method);
        if (sources.isEmpty()) {
            finish(
                    task,
                    new Result(NO_EVIDENCE, "NO_EVIDENCE", List.of(), sources, warnings, method));
            return;
        }
        if (!client.generationEnabled()) {
            warnings.add(
                    "demo".equals(ai.mode()) ? "演示模式：未调用模型，以下为真实错题检索结果。" : "尚未配置回答模型，以下为真实错题检索结果。");
            finish(
                    task,
                    new Result(
                            "已找到 "
                                    + sources.stream().map(Source::questionId).distinct().count()
                                    + " 道相关错题。请查看下方原文与解析。",
                            "RETRIEVAL_ONLY",
                            List.of(),
                            sources,
                            warnings,
                            method));
            return;
        }
        check(task, "GENERATING");
        String question = query;
        var answer = call(task, "GENERATION", () -> client.answer(question, sources));
        if (index.live(sources).stream().anyMatch(s -> !s.available() || s.changed()))
            throw new AiException("RAG_SOURCE_CHANGED", "回答期间引用错题已修改或删除，请核对来源后人工重试", 409);
        finish(
                task,
                new Result(
                        answer.insufficient() ? NO_EVIDENCE : answer.text(),
                        answer.insufficient() ? "NO_EVIDENCE" : "GENERATED",
                        answer.insufficient() ? List.of() : answer.citations(),
                        sources,
                        warnings,
                        method));
    }

    private <T> T call(Request task, String purpose, Supplier<T> operation) {
        int attempt =
                tx.execute(
                        s -> {
                            check(task, purpose.equals("GENERATION") ? "GENERATING" : "EMBEDDING");
                            usage.reserve();
                            int n =
                                    (int)
                                                    count(
                                                            "SELECT COUNT(*) FROM rag_call_record"
                                                                    + " WHERE request_id=?",
                                                            task.id())
                                            + 1;
                            db.update(
                                    "INSERT INTO"
                                        + " rag_call_record(request_id,attempt,retry_count,purpose,provider,model,status,created_at)"
                                        + " VALUES (?,?,?,?,?,?,'RUNNING',?)",
                                    task.id(),
                                    n,
                                    task.retryCount(),
                                    purpose,
                                    ai.mode(),
                                    purpose.equals("EMBEDDING")
                                            ? config.embeddingModel()
                                            : config.model(),
                                    now());
                            return n;
                        });
        long started = System.nanoTime();
        T result = null;
        AiException failure = null;
        try {
            result = operation.get();
        } catch (AiException e) {
            failure = e;
        } catch (RuntimeException e) {
            failure = new AiException("RAG_PROVIDER_FAILED", "模型处理失败", 502);
        }
        Long input = null, output = null;
        if (result instanceof RagModelClient.Answer a) {
            input = a.input();
            output = a.output();
        } else if (result instanceof RagModelClient.Vectors v) {
            input = v.input();
            output = v.output();
        } else if (failure != null) {
            input = failure.getInputTokens();
            output = failure.getOutputTokens();
        }
        Long in = input, out = output;
        AiException error = failure;
        BigDecimal cost =
                AiUsageService.estimate(
                        in,
                        out,
                        purpose.equals("EMBEDDING")
                                ? config.embeddingPricePerMillion()
                                : config.inputPricePerMillion(),
                        purpose.equals("EMBEDDING")
                                ? BigDecimal.ZERO
                                : config.outputPricePerMillion());
        boolean active =
                Boolean.TRUE.equals(
                        tx.execute(
                                s -> {
                                    var current = find(task.id(), true);
                                    boolean valid =
                                            current != null
                                                    && current.status().equals("RUNNING")
                                                    && current.retryCount() == task.retryCount();
                                    db.update(
                                            "UPDATE rag_call_record SET"
                                                + " status=?,error_code=?,input_tokens=?,output_tokens=?,estimated_cost=?,duration_ms=?,completed_at=?"
                                                + " WHERE request_id=? AND attempt=? AND"
                                                + " status='RUNNING'",
                                            valid
                                                    ? (error == null ? "SUCCEEDED" : "FAILED")
                                                    : "CANCELLED",
                                            error == null ? null : error.getCode(),
                                            in,
                                            out,
                                            cost,
                                            (System.nanoTime() - started) / 1_000_000,
                                            now(),
                                            task.id(),
                                            attempt);
                                    if (valid)
                                        db.update(
                                                "UPDATE rag_request SET updated_at=? WHERE id=?",
                                                now(),
                                                task.id());
                                    return valid;
                                }));
        if (!active) throw new AiException("RAG_CANCELLED", "请求已取消或中断", 409);
        if (failure != null) throw failure;
        return result;
    }

    private void check(Request task, String phase) {
        var current = required(task.id(), true);
        if (!current.status().equals("RUNNING") || current.retryCount() != task.retryCount())
            throw new AiException("RAG_CANCELLED", "请求已取消或中断", 409);
        db.update(
                "UPDATE rag_request SET phase=?,updated_at=? WHERE id=?", phase, now(), task.id());
    }

    private void persistSources(
            Request task, List<Source> sources, List<String> warnings, String method) {
        tx.executeWithoutResult(
                s -> {
                    check(task, "GENERATING");
                    db.update(
                            "UPDATE rag_request SET"
                                + " sources_json=?,warnings_json=?,retrieval_method=? WHERE id=?",
                            json.writeValueAsString(sources),
                            json.writeValueAsString(warnings),
                            method,
                            task.id());
                });
    }

    private void finish(Request task, Result result) {
        tx.executeWithoutResult(
                s -> {
                    if (task.sessionId() != null) {
                        var sessions =
                                db.query(
                                        "SELECT id FROM rag_session WHERE id=? FOR UPDATE",
                                        (rs, i) -> rs.getString(1),
                                        task.sessionId());
                        if (sessions.isEmpty()) return;
                    }
                    var r = find(task.id(), true);
                    if (r == null
                            || !r.status().equals("RUNNING")
                            || r.retryCount() != task.retryCount()) return;
                    if ("GENERATED".equals(result.type())) {
                        var current = index.questions(task.rootId(), task.reviewStatus());
                        Map<Long, String> hashes = new HashMap<>();
                        current.forEach(q -> hashes.put(q.id(), q.hash()));
                        if (result.sources().stream()
                                .anyMatch(
                                        source ->
                                                !source.documentHash()
                                                        .equals(hashes.get(source.questionId()))))
                            throw new AiException(
                                    "RAG_SOURCE_CHANGED", "回答期间引用错题已修改、删除或离开当前范围，请核对来源后人工重试", 409);
                    }
                    db.update(
                            "UPDATE rag_request SET"
                                + " status='SUCCEEDED',phase='DONE',answer=?,answer_type=?,citations_json=?,sources_json=?,warnings_json=?,retrieval_method=?,updated_at=?,completed_at=?"
                                + " WHERE id=?",
                            result.answer(),
                            result.type(),
                            json.writeValueAsString(result.citations()),
                            json.writeValueAsString(result.sources()),
                            json.writeValueAsString(result.warnings()),
                            result.method(),
                            now(),
                            now(),
                            task.id());
                    if (r.sessionId() != null)
                        db.update(
                                "UPDATE rag_session SET updated_at=? WHERE id=?",
                                now(),
                                r.sessionId());
                });
    }

    private void fail(Request task, AiException error) {
        tx.executeWithoutResult(
                s -> {
                    var r = find(task.id(), true);
                    if (r != null
                            && r.status().equals("RUNNING")
                            && r.retryCount() == task.retryCount())
                        db.update(
                                "UPDATE rag_request SET"
                                    + " status='FAILED',phase='DONE',error_code=?,error_message=?,updated_at=?,completed_at=?"
                                    + " WHERE id=?",
                                error.getCode(),
                                error.getMessage(),
                                now(),
                                now(),
                                task.id());
                });
    }

    public void recoverInterrupted() {
        tx.executeWithoutResult(
                s -> {
                    db.update(
                            "UPDATE rag_call_record SET"
                                + " status='INTERRUPTED',error_code='RAG_PROCESS_INTERRUPTED',completed_at=?"
                                + " WHERE status='RUNNING'",
                            now());
                    db.update(
                            "UPDATE rag_request SET"
                                + " status='FAILED',phase='DONE',error_code='RAG_PROCESS_INTERRUPTED',error_message='后端重启中断了处理，调用可能已计费，请人工决定是否重试',updated_at=?,completed_at=?"
                                + " WHERE status='RUNNING'",
                            now(),
                            now());
                });
    }

    public void recoverExpired() {
        tx.executeWithoutResult(
                s -> {
                    var rows =
                            db.query(
                                    "SELECT * FROM rag_request WHERE status='RUNNING' AND"
                                            + " updated_at<? FOR UPDATE",
                                    this::row,
                                    time(clock.instant().minusSeconds(ai.timeoutSeconds() + 60L)));
                    for (var r : rows) {
                        db.update(
                                "UPDATE rag_call_record SET"
                                    + " status='INTERRUPTED',error_code='RAG_TASK_STALLED',completed_at=?"
                                    + " WHERE request_id=? AND status='RUNNING'",
                                now(),
                                r.id());
                        fail(
                                r,
                                new AiException(
                                        "RAG_TASK_STALLED", "处理已中断，调用可能已计费，请人工决定是否重试", 503));
                    }
                });
    }

    private Request find(String id, boolean lock) {
        var rows =
                db.query(
                        "SELECT * FROM rag_request WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                        this::row,
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Request required(String id, boolean lock) {
        var r = find(id, lock);
        if (r == null) throw new AiException("RAG_REQUEST_NOT_FOUND", "学习请求不存在", 404);
        return r;
    }

    private Request live(Request r) {
        return new Request(
                r.id(),
                r.sessionId(),
                r.kind(),
                r.question(),
                r.rootId(),
                r.reviewStatus(),
                r.focusQuestionId(),
                r.followUp(),
                r.status(),
                r.phase(),
                r.retryCount(),
                r.answer(),
                r.answerType(),
                r.citations(),
                index.live(r.sources()),
                r.warnings(),
                r.retrievalMethod(),
                r.errorCode(),
                r.errorMessage(),
                r.createdAt(),
                r.updatedAt(),
                r.completedAt());
    }

    private Request row(ResultSet rs, int i) throws SQLException {
        return new Request(
                rs.getString("id"),
                rs.getString("session_id"),
                rs.getString("kind"),
                rs.getString("question_text"),
                nullableLong(rs, "root_id"),
                rs.getString("review_status"),
                nullableLong(rs, "focus_question_id"),
                rs.getBoolean("follow_up"),
                rs.getString("status"),
                rs.getString("phase"),
                rs.getInt("retry_count"),
                rs.getString("answer"),
                rs.getString("answer_type"),
                list(rs.getString("citations_json")),
                sources(rs.getString("sources_json")),
                list(rs.getString("warnings_json")),
                rs.getString("retrieval_method"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "completed_at"));
    }

    private List<String> list(String text) {
        return text == null ? List.of() : List.of(json.readValue(text, String[].class));
    }

    private List<Source> sources(String text) {
        return text == null ? List.of() : List.of(json.readValue(text, Source[].class));
    }

    private long count(String sql, Object... args) {
        return db.queryForObject(sql, Long.class, args);
    }

    private void page(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50)
            throw new AiException("RAG_INVALID_PAGE", "分页范围不正确", 400);
    }

    private Long nullableLong(ResultSet rs, String c) throws SQLException {
        return rs.getObject(c) == null ? null : rs.getLong(c);
    }

    private Instant instant(ResultSet rs, String c) throws SQLException {
        var value = rs.getObject(c, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime now() {
        return time(clock.instant());
    }

    private LocalDateTime time(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
