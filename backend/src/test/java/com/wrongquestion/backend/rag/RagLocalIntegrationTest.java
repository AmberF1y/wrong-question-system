package com.wrongquestion.backend.rag;

import static org.junit.jupiter.api.Assertions.*;

import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.dto.CreateQuestionRequest;
import com.wrongquestion.backend.question.service.QuestionService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

@SpringBootTest
class RagLocalIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired RagService service;
    @Autowired RagIndexService index;
    @Autowired QuestionService questions;
    @Autowired KnowledgePointRepository knowledge;

    @Test
    void searchesRealLocalTextWithoutPretendingToGenerateOrChargingQuota() {
        var root = knowledge.saveAndFlush(new KnowledgePoint("RAG本地-" + UUID.randomUUID(), null));
        var question =
                questions.create(
                        new CreateQuestionRequest(
                                "链表反转的指针顺序",
                                "丢失 next",
                                "先保存 next",
                                "避免丢失后续节点",
                                "覆盖指针",
                                List.of(root.getId())));
        var session = UUID.randomUUID();
        service.createSession(session);
        int usage = service.configuration().callsToday();
        try {
            var id = UUID.randomUUID();
            service.ask(id, session, new RagDtos.Ask("链表反转", root.getId(), null, null, false));
            service.executeNext();
            var r = service.get(id);
            assertEquals("RETRIEVAL_ONLY", r.answerType());
            assertEquals(question.id(), r.sources().getFirst().questionId());
            String storedTime =
                    db.queryForObject(
                            "SELECT DATE_FORMAT(updated_time,'%Y-%m-%dT%H:%i:%s') FROM question"
                                    + " WHERE id=?",
                            String.class, question.id());
            assertEquals(
                    java.time.LocalDateTime.parse(storedTime), r.sources().getFirst().updatedAt());
            assertTrue(r.sources().getFirst().excerpt().contains("先保存 next"));
            assertEquals(usage, service.configuration().callsToday());
            assertTrue(service.calls(id).isEmpty());
            assertFalse(service.configuration().generationEnabled());
        } finally {
            service.deleteSession(session);
            questions.delete(question.id());
            knowledge.deleteById(root.getId());
        }
    }

    @Test
    void splittingPreservesUnicodeAndHasAnOverlap() {
        String text = "数学公式 $x^2$ 😀".repeat(180);
        var chunks = index.split(text);
        assertTrue(chunks.size() > 1);
        for (var part : chunks) {
            assertTrue(part.length() <= 900);
            assertFalse(Character.isHighSurrogate(part.charAt(part.length() - 1)));
            assertFalse(Character.isLowSurrogate(part.charAt(0)));
        }
        assertTrue(
                chunks.getFirst().endsWith(chunks.get(1).substring(0, 100))
                        || chunks.getFirst().endsWith(chunks.get(1).substring(0, 99)));
    }
}
