package com.wrongquestion.backend.rag;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;

/** Bounded exact retrieval for the local corpus; no approximate-vector-service dependency. */
@Component
public class RagRetriever {
    public record Document(
            long id,
            long questionId,
            int chunkNumber,
            String content,
            String documentHash,
            double[] vector) {}

    public record Hit(Document document, double score, String method) {}

    private static final Pattern TERMS =
            Pattern.compile("[\\p{IsHan}]+|[\\p{L}\\p{N}_]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> STOP =
            Set.of(
                    "的", "了", "我", "你", "这", "那", "是", "在", "一个", "什么", "为什么", "怎么", "如何", "请问",
                    "可以", "帮我", "一下", "题目", "答案", "解析", "科目", "知识点", "错误答案", "正确答案");

    public List<String> tokenize(String text) {
        List<String> result = new ArrayList<>();
        var matcher = TERMS.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            int[] points = word.codePoints().toArray();
            if (Character.UnicodeScript.of(points[0]) == Character.UnicodeScript.HAN) {
                if (points.length == 1) {
                    if (!STOP.contains(word)) result.add(word);
                }
                for (int i = 0; i + 1 < points.length; i++) {
                    String token = new String(points, i, 2);
                    if (!STOP.contains(token)) result.add(token);
                }
            } else if (!STOP.contains(word)) result.add(word);
        }
        return result;
    }

    public List<Hit> retrieve(
            String query,
            List<Document> documents,
            double[] queryVector,
            int topK,
            double minimumSimilarity,
            Long focusId) {
        if (documents.isEmpty()) return List.of();
        Set<String> queryTerms = new HashSet<>(tokenize(query));
        Map<Long, Map<String, Integer>> terms = new HashMap<>();
        Map<String, Integer> frequency = new HashMap<>();
        Map<Long, Integer> lengths = new HashMap<>();
        double average = 0;
        for (Document doc : documents) {
            var tokens = tokenize(doc.content());
            Map<String, Integer> counts = new HashMap<>();
            tokens.forEach(t -> counts.merge(t, 1, Integer::sum));
            terms.put(doc.id(), counts);
            lengths.put(doc.id(), tokens.size());
            average += tokens.size();
            counts.keySet().forEach(t -> frequency.merge(t, 1, Integer::sum));
        }
        average = Math.max(1, average / documents.size());
        Map<Long, Double> lexical = new HashMap<>(), semantic = new HashMap<>();
        for (Document doc : documents) {
            double score = 0;
            for (String term : queryTerms) {
                int tf = terms.get(doc.id()).getOrDefault(term, 0);
                if (tf == 0) continue;
                double idf =
                        Math.log(
                                1
                                        + (documents.size() - frequency.get(term) + 0.5)
                                                / (frequency.get(term) + 0.5));
                score +=
                        idf
                                * tf
                                * 2.2
                                / (tf + 1.2 * (0.25 + 0.75 * lengths.get(doc.id()) / average));
            }
            if (score > 0) lexical.put(doc.id(), score);
            if (queryVector != null && doc.vector() != null) {
                double cosine = cosine(queryVector, doc.vector());
                if (cosine >= minimumSimilarity) semantic.put(doc.id(), cosine);
            }
        }
        Map<Long, Double> fused = new HashMap<>();
        rank(lexical, fused);
        rank(semantic, fused);
        List<Document> ordered = new ArrayList<>(documents);
        ordered.sort(
                Comparator.<Document>comparingDouble(
                                d ->
                                        Objects.equals(focusId, d.questionId())
                                                ? 1 + fused.getOrDefault(d.id(), 0d)
                                                : fused.getOrDefault(d.id(), 0d))
                        .reversed()
                        .thenComparing(Document::questionId, Comparator.reverseOrder())
                        .thenComparingInt(Document::chunkNumber));
        List<Hit> result = new ArrayList<>();
        Map<Long, Integer> perQuestion = new HashMap<>();
        for (Document doc : ordered) {
            boolean focus = Objects.equals(focusId, doc.questionId());
            double score = fused.getOrDefault(doc.id(), 0d);
            if ((score <= 0 && !focus) || perQuestion.getOrDefault(doc.questionId(), 0) >= 2)
                continue;
            String method =
                    focus
                            ? "FOCUS"
                            : lexical.containsKey(doc.id()) && semantic.containsKey(doc.id())
                                    ? "HYBRID"
                                    : semantic.containsKey(doc.id()) ? "SEMANTIC" : "KEYWORD";
            result.add(new Hit(doc, focus ? 1 + score : score, method));
            perQuestion.merge(doc.questionId(), 1, Integer::sum);
            if (result.size() == topK) break;
        }
        return result;
    }

    private void rank(Map<Long, Double> scores, Map<Long, Double> fused) {
        var entries = new ArrayList<>(scores.entrySet());
        entries.sort(
                Map.Entry.<Long, Double>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()));
        for (int i = 0; i < Math.min(50, entries.size()); i++)
            fused.merge(entries.get(i).getKey(), 1.0 / (60 + i + 1), Double::sum);
    }

    public double cosine(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) return -1;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            aa += a[i] * a[i];
            bb += b[i] * b[i];
        }
        return aa == 0 || bb == 0 ? -1 : dot / Math.sqrt(aa * bb);
    }
}
