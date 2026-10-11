package com.wrongquestion.backend.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

class AiVisionClientTest {
    private AiProperties properties(String mode, String url, String key) {
        return new AiProperties(
                mode, url, key, "vision", 2, 1, 3, 2, 20, false, 300, "json_object", null, null);
    }

    private final AiVisionClient client =
            new AiVisionClient(properties("demo", "", ""), new ObjectMapper());

    @Test
    void shouldPreserveLatexAndEmptyAnswersAndIgnorePersonalFields() {
        var draft =
                client.parseDraft(
                        """
{"questionText":"求 $x^2$","correctAnswer":"","analysis":"","knowledgePointIds":[1,1],"warnings":[],
"wrongAnswer":"模型伪造的个人答案","errorReason":"模型伪造的个人原因"}
""");
        assertEquals("求 $x^2$", draft.questionText());
        assertEquals("", draft.correctAnswer());
        assertEquals(List.of(1L), draft.knowledgePointIds());
        assertFalse(draft.warnings().isEmpty());
    }

    @Test
    void shouldRejectMissingOrWronglyTypedFields() {
        assertThrows(AiException.class, () -> client.parseDraft("{}"));
        assertThrows(
                AiException.class,
                () ->
                        client.parseDraft(
                                "{\"questionText\":\"题目\",\"correctAnswer\":\"\",\"analysis\":\"\",\"knowledgePointIds\":[\"1\"],\"warnings\":[]}"));
    }

    @Test
    void shouldRejectOversizedTextAndUnreadableQuestion() {
        String tail =
                "\",\"correctAnswer\":\"\",\"analysis\":\"\",\"knowledgePointIds\":[],\"warnings\":[]}";
        assertEquals(
                "AI_NO_TEXT",
                assertThrows(
                                AiException.class,
                                () -> client.parseDraft("{\"questionText\":\"" + tail))
                        .getCode());
        assertThrows(
                AiException.class,
                () -> client.parseDraft("{\"questionText\":\"" + "a".repeat(10001) + tail));
    }

    @Test
    void shouldKeepDemoClearlyIdentifiedAndNeverClaimImageWasRecognized() {
        var result = client.recognize(new byte[] {1}, "image/png", "[]");
        assertTrue(result.draft().questionText().contains("演示"));
        assertTrue(result.draft().warnings().getFirst().contains("未识别"));
        assertNull(result.inputTokens());
        assertNull(result.outputTokens());
    }

    @Test
    void shouldDisableIncompleteConfigurationAndRejectInsecureRemoteUrl() {
        assertFalse(
                new AiVisionClient(properties("disabled", "", ""), new ObjectMapper()).available());
        assertFalse(
                new AiVisionClient(
                                properties("compatible", "https://example.com/v1", ""),
                                new ObjectMapper())
                        .available());
        assertFalse(
                new AiVisionClient(
                                properties("compatible", "http://example.com/v1", "key"),
                                new ObjectMapper())
                        .available());
        assertTrue(
                new AiVisionClient(
                                properties("compatible", "http://127.0.0.1:1234/v1", "key"),
                                new ObjectMapper())
                        .available());
    }
}
