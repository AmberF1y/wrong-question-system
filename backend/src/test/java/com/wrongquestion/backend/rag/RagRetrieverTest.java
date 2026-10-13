package com.wrongquestion.backend.rag;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class RagRetrieverTest {
    private final RagRetriever retriever = new RagRetriever();

    private RagRetriever.Document doc(long id, long question, String content, double[] vector) {
        return new RagRetriever.Document(id, question, 0, content, "hash", vector);
    }

    @Test
    void findsChineseBigramsAndLatinCodeTerms() {
        assertTrue(
                retriever
                        .tokenize("二分查找的 left_bound")
                        .containsAll(List.of("二分", "分查", "查找", "left_bound")));
    }

    @Test
    void lexicalResultsAreRankedAndUnrelatedDocumentsAreExcluded() {
        var result =
                retriever.retrieve(
                        "二分查找边界",
                        List.of(doc(1, 1, "二分查找边界 off by one", null), doc(2, 2, "操作系统页表内存", null)),
                        null,
                        6,
                        .45,
                        null);
        assertEquals(List.of(1L), result.stream().map(h -> h.document().questionId()).toList());
    }

    @Test
    void semanticRetrievalCanFindALexicallyUnrelatedDocument() {
        var result =
                retriever.retrieve(
                        "boundary",
                        List.of(
                                doc(1, 1, "二分查找", new double[] {1, 0}),
                                doc(2, 2, "数据库", new double[] {0, 1})),
                        new double[] {1, 0},
                        6,
                        .45,
                        null);
        assertEquals("SEMANTIC", result.getFirst().method());
        assertEquals(1, result.size());
    }

    @Test
    void combinesRanksFromBothChannels() {
        var result =
                retriever.retrieve(
                        "边界",
                        List.of(doc(1, 1, "边界", new double[] {1, 0})),
                        new double[] {1, 0},
                        6,
                        .45,
                        null);
        assertEquals("HYBRID", result.getFirst().method());
        assertEquals(2.0 / 61, result.getFirst().score(), 1e-10);
    }

    @Test
    void limitsEachQuestionToTwoChunksAndUsesStableTies() {
        var docs =
                List.of(
                        doc(1, 1, "边界", null),
                        doc(2, 1, "边界", null),
                        doc(3, 1, "边界", null),
                        doc(4, 2, "边界", null));
        var a = retriever.retrieve("边界", docs, null, 6, .45, null);
        assertEquals(3, a.size());
        assertEquals(a, retriever.retrieve("边界", docs, null, 6, .45, null));
    }

    @Test
    void rejectsWrongDimensionsAndZeroVectors() {
        assertEquals(-1, retriever.cosine(new double[] {1}, new double[] {1, 2}));
        assertEquals(-1, retriever.cosine(new double[] {0}, new double[] {1}));
    }

    @Test
    void explicitlyFocusedQuestionIsIncludedWithoutLexicalOverlap() {
        var result =
                retriever.retrieve("说明这道题", List.of(doc(1, 42, "递归出口", null)), null, 6, .45, 42L);
        assertEquals("FOCUS", result.getFirst().method());
    }

    @Test
    void noEvidenceMeansAnEmptyResult() {
        assertTrue(
                retriever
                        .retrieve(
                                "quantumxyz", List.of(doc(1, 1, "二分查找", null)), null, 6, .45, null)
                        .isEmpty());
    }
}
