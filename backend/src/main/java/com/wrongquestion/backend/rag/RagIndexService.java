package com.wrongquestion.backend.rag;

import com.wrongquestion.backend.ai.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class RagIndexService {
    public record Question(
            long id,
            String text,
            String subject,
            List<String> knowledge,
            String hash,
            LocalDateTime updated,
            String status) {}

    public record Chunk(
            long id,
            long questionId,
            int number,
            String content,
            String hash,
            String documentHash) {}

    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final RagProperties config;
    private final AiProperties ai;
    private final ObjectMapper json;
    private final RagModelClient client;
    private final Clock clock;

    public RagIndexService(
            JdbcTemplate db,
            TransactionTemplate tx,
            RagProperties config,
            AiProperties ai,
            ObjectMapper json,
            RagModelClient client,
            Clock clock) {
        this.db = db;
        this.tx = tx;
        this.config = config;
        this.ai = ai;
        this.json = json;
        this.client = client;
        this.clock = clock;
    }

    public String subject(Long rootId) {
        if (rootId == null) return null;
        var names =
                db.query(
                        "SELECT name FROM knowledge_point WHERE id=? AND parent_id IS NULL",
                        (rs, i) -> rs.getString(1),
                        rootId);
        if (names.isEmpty()) throw new AiException("RAG_INVALID_SUBJECT", "所选科目不存在，请重新选择", 400);
        return names.getFirst();
    }

    public List<Question> questions(Long rootId, String reviewStatus) {
        String subject = subject(rootId);
        if (reviewStatus != null && !Set.of("ACTIVE", "MASTERED").contains(reviewStatus))
            throw new AiException("RAG_INVALID_STATUS", "掌握状态不正确", 400);
        List<Object> args = new ArrayList<>();
        String where = " WHERE 1=1";
        if (subject != null) {
            where += " AND q.subject=?";
            args.add(subject);
        }
        if (reviewStatus != null) {
            where += " AND s.review_status=?";
            args.add(reviewStatus);
        }
        args.add(config.maxQuestions() + 1);
        return load(where + " ORDER BY q.id LIMIT ?", args);
    }

    private List<Question> load(String clause, List<Object> args) {
        var rows =
                db.query(
                        "SELECT q.*,s.review_status FROM question q LEFT JOIN question_review_state"
                                + " s ON s.question_id=q.id"
                                + clause,
                        (rs, i) -> {
                            long id = rs.getLong("id");
                            return new String[] {
                                String.valueOf(id),
                                rs.getString("question_text"),
                                rs.getString("subject"),
                                rs.getString("wrong_answer"),
                                rs.getString("correct_answer"),
                                rs.getString("analysis"),
                                rs.getString("error_reason"),
                                rs.getString("review_status"),
                                rs.getObject("updated_time", LocalDateTime.class).toString()
                            };
                        },
                        args.toArray());
        if (rows.size() > config.maxQuestions())
            throw new AiException("RAG_CORPUS_LIMIT", "当前范围错题过多，请缩小科目范围或调整索引上限", 413);
        if (rows.isEmpty()) return List.of();
        // One metadata query instead of a query per question. Names are ordered for stable hashes.
        Map<Long, List<String>> points = new HashMap<>();
        db.query(
                "SELECT p.question_id,k.name FROM question_knowledge_point p JOIN knowledge_point k"
                        + " ON k.id=p.knowledge_point_id WHERE p.question_id IN ("
                        + String.join(",", Collections.nCopies(rows.size(), "?"))
                        + ") ORDER BY p.question_id,k.id",
                rs -> {
                    points.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                            .add(rs.getString(2));
                },
                rows.stream().map(r -> Long.valueOf(r[0])).toArray());
        return rows.stream()
                .map(
                        r -> {
                            long id = Long.parseLong(r[0]);
                            List<String> kp = List.copyOf(points.getOrDefault(id, List.of()));
                            String content =
                                    "科目："
                                            + r[2]
                                            + "\n知识点："
                                            + String.join("、", kp)
                                            + "\n题目："
                                            + r[1]
                                            + "\n我的错误答案："
                                            + r[3]
                                            + "\n正确答案："
                                            + r[4]
                                            + "\n解析："
                                            + r[5]
                                            + "\n错误原因："
                                            + r[6];
                            return new Question(
                                    id,
                                    content,
                                    r[2],
                                    kp,
                                    hash(content),
                                    LocalDateTime.parse(r[8]),
                                    r[7]);
                        })
                .toList();
    }

    public void refresh(List<Question> questions) {
        Map<Long, String> indexed = new HashMap<>();
        Map<Long, List<String>> chunkHashes = new HashMap<>();
        db.query(
                "SELECT question_id,content_hash FROM rag_chunk ORDER BY question_id,chunk_number",
                rs -> {
                    chunkHashes
                            .computeIfAbsent(rs.getLong(1), key -> new ArrayList<>())
                            .add(rs.getString(2));
                });
        db.query(
                "SELECT question_id,MIN(document_hash) FROM rag_chunk GROUP BY question_id",
                rs -> {
                    indexed.put(rs.getLong(1), rs.getString(2));
                });
        long existing = db.queryForObject("SELECT COUNT(*) FROM rag_chunk", Long.class);
        long next = existing;
        for (var q : questions)
            if (!q.hash().equals(indexed.get(q.id()))
                    || !split(q.text()).stream()
                            .map(RagIndexService::hash)
                            .toList()
                            .equals(chunkHashes.get(q.id()))) {
                next -=
                        db.queryForObject(
                                "SELECT COUNT(*) FROM rag_chunk WHERE question_id=?",
                                Long.class,
                                q.id());
                next += split(q.text()).size();
            }
        if (next > config.maxChunks())
            throw new AiException("RAG_CHUNK_LIMIT", "索引片段超过上限，请调整片段上限或减少错题", 413);
        for (var q : questions) {
            if (q.hash().equals(indexed.get(q.id()))
                    && split(q.text()).stream()
                            .map(RagIndexService::hash)
                            .toList()
                            .equals(chunkHashes.get(q.id()))) continue;
            tx.executeWithoutResult(
                    s -> {
                        // Lock the source row: deletion cannot leave orphaned chunks during this
                        // write.
                        var current =
                                db.query(
                                        "SELECT id FROM question WHERE id=? FOR UPDATE",
                                        (rs, i) -> rs.getLong(1),
                                        q.id());
                        if (current.isEmpty()) return;
                        db.update("DELETE FROM rag_chunk WHERE question_id=?", q.id());
                        int number = 0;
                        for (String text : split(q.text()))
                            db.update(
                                    "INSERT INTO"
                                        + " rag_chunk(question_id,chunk_number,content,content_hash,document_hash,updated_at)"
                                        + " VALUES (?,?,?,?,?,?)",
                                    q.id(),
                                    number++,
                                    text,
                                    hash(text),
                                    q.hash(),
                                    time(clock.instant()));
                    });
        }
    }

    public List<String> split(String text) {
        int overlap = Math.min(config.chunkOverlap(), config.chunkSize() / 3);
        List<String> result = new ArrayList<>();
        for (int start = 0; start < text.length(); ) {
            int end = Math.min(text.length(), start + config.chunkSize());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            result.add(text.substring(start, end));
            if (end == text.length()) break;
            start = end - overlap;
            if (start > 0 && Character.isLowSurrogate(text.charAt(start))) start++;
        }
        return result;
    }

    public String fingerprint() {
        return hash(ai.baseUrl().replaceAll("/+$", "") + "|" + config.embeddingModel());
    }

    public List<Chunk> missing(List<Question> questions) {
        Set<Long> allowed = new HashSet<>();
        questions.forEach(q -> allowed.add(q.id()));
        return db
                .query(
                        "SELECT * FROM rag_chunk WHERE embedding_fingerprint IS NULL OR"
                                + " embedding_fingerprint<>? ORDER BY id",
                        (rs, i) ->
                                new Chunk(
                                        rs.getLong("id"),
                                        rs.getLong("question_id"),
                                        rs.getInt("chunk_number"),
                                        rs.getString("content"),
                                        rs.getString("content_hash"),
                                        rs.getString("document_hash")),
                        fingerprint())
                .stream()
                .filter(c -> allowed.contains(c.questionId()))
                .limit(config.embeddingBatchSize())
                .toList();
    }

    public void saveVectors(List<Chunk> chunks, List<double[]> vectors) {
        tx.executeWithoutResult(
                s -> {
                    for (int i = 0; i < chunks.size(); i++) {
                        var c = chunks.get(i);
                        db.update(
                                "UPDATE rag_chunk SET embedding_json=?,embedding_fingerprint=?"
                                        + " WHERE id=? AND content_hash=? AND document_hash=?",
                                json.writeValueAsString(vectors.get(i)),
                                fingerprint(),
                                c.id(),
                                c.hash(),
                                c.documentHash());
                    }
                });
    }

    public List<RagRetriever.Document> documents(List<Question> questions) {
        Map<Long, Question> allowed = new HashMap<>();
        questions.forEach(q -> allowed.put(q.id(), q));
        String fingerprint = fingerprint();
        return db
                .query(
                        "SELECT * FROM rag_chunk ORDER BY id",
                        (rs, i) -> {
                            var q = allowed.get(rs.getLong("question_id"));
                            if (q == null || !q.hash().equals(rs.getString("document_hash")))
                                return null;
                            double[] vector = null;
                            String data = rs.getString("embedding_json");
                            if (client.embeddingEnabled()
                                    && data != null
                                    && fingerprint.equals(rs.getString("embedding_fingerprint"))) {
                                try {
                                    vector = client.parseVector(json.readTree(data));
                                } catch (AiException ignored) {
                                    /* A damaged vector falls back to lexical retrieval. */
                                }
                            }
                            return new RagRetriever.Document(
                                    rs.getLong("id"),
                                    q.id(),
                                    rs.getInt("chunk_number"),
                                    rs.getString("content"),
                                    q.hash(),
                                    vector);
                        })
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public RagDtos.IndexStatus status(Long rootId) {
        var source = questions(rootId, null);
        Map<Long, String> indexed = new HashMap<>();
        Map<Long, Long> chunks = new HashMap<>(), embedded = new HashMap<>();
        db.query(
                "SELECT question_id,MIN(document_hash),COUNT(*),SUM(embedding_fingerprint=?) FROM"
                        + " rag_chunk GROUP BY question_id",
                rs -> {
                    indexed.put(rs.getLong(1), rs.getString(2));
                    chunks.put(rs.getLong(1), rs.getLong(3));
                    embedded.put(rs.getLong(1), rs.getLong(4));
                },
                fingerprint());
        long current = 0, total = 0, vectors = 0;
        for (var q : source) {
            total += chunks.getOrDefault(q.id(), 0L);
            if (q.hash().equals(indexed.get(q.id()))) {
                current++;
                vectors += embedded.getOrDefault(q.id(), 0L);
            }
        }
        return new RagDtos.IndexStatus(
                source.size(),
                current,
                source.size() - current,
                total,
                vectors,
                client.embeddingEnabled(),
                config.embeddingModel(),
                config.maxQuestions(),
                config.maxChunks());
    }

    public List<RagDtos.Source> live(List<RagDtos.Source> sources) {
        if (sources.isEmpty()) return List.of();
        List<Object> ids =
                new ArrayList<>(
                        sources.stream().map(RagDtos.Source::questionId).distinct().toList());
        Map<Long, Question> current = new HashMap<>();
        load(" WHERE q.id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")", ids)
                .forEach(q -> current.put(q.id(), q));
        // Historical snapshots survive source deletion; freshness is checked against current text.
        List<RagDtos.Source> result = new ArrayList<>();
        for (var s : sources) {
            var question = current.get(s.questionId());
            boolean available = question != null,
                    changed = available && !s.documentHash().equals(question.hash());
            result.add(
                    new RagDtos.Source(
                            s.reference(),
                            s.questionId(),
                            s.chunkNumber(),
                            s.questionPreview(),
                            s.subject(),
                            s.knowledgePoints(),
                            s.excerpt(),
                            s.score(),
                            s.method(),
                            s.documentHash(),
                            s.updatedAt(),
                            available,
                            changed));
        }
        return List.copyOf(result);
    }

    public static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private LocalDateTime time(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
