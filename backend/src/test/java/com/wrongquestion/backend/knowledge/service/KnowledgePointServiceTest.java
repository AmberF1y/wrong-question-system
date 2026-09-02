package com.wrongquestion.backend.knowledge.service;

import com.wrongquestion.backend.common.dto.MessageResponse;
import com.wrongquestion.backend.knowledge.dto.CreateKnowledgePointRequest;
import com.wrongquestion.backend.knowledge.dto.KnowledgePointResponse;
import com.wrongquestion.backend.knowledge.dto.KnowledgePointTreeNodeResponse;
import com.wrongquestion.backend.knowledge.dto.UpdateKnowledgePointRequest;
import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointConflictException;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointNotFoundException;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointValidationException;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgePointServiceTest {

    @Mock
    private KnowledgePointRepository knowledgePointRepository;

    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private KnowledgePointService knowledgePointService;

    @Test
    void shouldCreateRootKnowledgePoint() {
        when(knowledgePointRepository.existsByParentIsNullAndName("408"))
                .thenReturn(false);
        stubSavedId(1L);

        KnowledgePointResponse response = knowledgePointService.create(
                new CreateKnowledgePointRequest("408", null)
        );

        assertEquals(1L, response.id());
        assertEquals("408", response.name());
        assertNull(response.parentId());
    }

    @Test
    void shouldCreateChildKnowledgePoint() {
        KnowledgePoint parent = knowledgePoint(1L, "计算机网络", null);
        when(knowledgePointRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(knowledgePointRepository.existsByParent_IdAndName(1L, "TCP"))
                .thenReturn(false);
        stubSavedId(2L);

        KnowledgePointResponse response = knowledgePointService.create(
                new CreateKnowledgePointRequest("TCP", 1L)
        );

        assertEquals(2L, response.id());
        assertEquals("TCP", response.name());
        assertEquals(1L, response.parentId());
    }

    @Test
    void shouldTrimNameBeforeCreate() {
        when(knowledgePointRepository.existsByParentIsNullAndName("408"))
                .thenReturn(false);
        stubSavedId(1L);

        KnowledgePointResponse response = knowledgePointService.create(
                new CreateKnowledgePointRequest("  408  ", null)
        );

        assertEquals("408", response.name());
    }

    @Test
    void shouldRejectRootNameLongerThanFiftyCharacters() {
        String name = "根".repeat(51);

        KnowledgePointValidationException exception = assertThrows(
                KnowledgePointValidationException.class,
                () -> knowledgePointService.create(
                        new CreateKnowledgePointRequest(name, null)
                )
        );

        assertEquals("根节点名称不能超过50个字符", exception.getMessage());
        verify(knowledgePointRepository, never()).save(any());
    }

    @Test
    void shouldRejectKnowledgePointNameLongerThanOneHundredCharacters() {
        String name = "知".repeat(101);

        assertThrows(
                KnowledgePointValidationException.class,
                () -> knowledgePointService.create(
                        new CreateKnowledgePointRequest(name, 1L)
                )
        );

        verify(knowledgePointRepository, never()).findById(any());
    }

    @Test
    void shouldRejectDuplicateRootName() {
        when(knowledgePointRepository.existsByParentIsNullAndName("408"))
                .thenReturn(true);

        KnowledgePointConflictException exception = assertThrows(
                KnowledgePointConflictException.class,
                () -> knowledgePointService.create(
                        new CreateKnowledgePointRequest("408", null)
                )
        );

        assertEquals("KNOWLEDGE_POINT_NAME_CONFLICT", exception.getCode());
    }

    @Test
    void shouldRejectDuplicateSiblingName() {
        KnowledgePoint parent = knowledgePoint(1L, "计算机网络", null);
        when(knowledgePointRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(knowledgePointRepository.existsByParent_IdAndName(1L, "TCP"))
                .thenReturn(true);

        KnowledgePointConflictException exception = assertThrows(
                KnowledgePointConflictException.class,
                () -> knowledgePointService.create(
                        new CreateKnowledgePointRequest("TCP", 1L)
                )
        );

        assertEquals("KNOWLEDGE_POINT_NAME_CONFLICT", exception.getCode());
    }

    @Test
    void shouldAllowSameNameUnderDifferentParents() {
        KnowledgePoint secondParent = knowledgePoint(2L, "操作系统", null);
        when(knowledgePointRepository.findById(2L))
                .thenReturn(Optional.of(secondParent));
        when(knowledgePointRepository.existsByParent_IdAndName(2L, "概述"))
                .thenReturn(false);
        stubSavedId(3L);

        KnowledgePointResponse response = knowledgePointService.create(
                new CreateKnowledgePointRequest("概述", 2L)
        );

        assertEquals("概述", response.name());
        assertEquals(2L, response.parentId());
    }

    @Test
    void shouldRejectMissingParentWhenCreateChild() {
        when(knowledgePointRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                KnowledgePointNotFoundException.class,
                () -> knowledgePointService.create(
                        new CreateKnowledgePointRequest("TCP", 99L)
                )
        );
    }

    @Test
    void shouldMoveChildWithinSameTree() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        KnowledgePoint oldParent = knowledgePoint(2L, "计算机网络", root);
        KnowledgePoint newParent = knowledgePoint(3L, "传输层", root);
        KnowledgePoint target = knowledgePoint(4L, "TCP", oldParent);
        when(knowledgePointRepository.findById(4L)).thenReturn(Optional.of(target));
        when(knowledgePointRepository.findById(3L)).thenReturn(Optional.of(newParent));
        when(knowledgePointRepository.existsByParent_IdAndNameAndIdNot(
                3L,
                "TCP",
                4L
        )).thenReturn(false);
        stubSavedEntity();

        KnowledgePointResponse response = knowledgePointService.update(
                4L,
                new UpdateKnowledgePointRequest("TCP", 3L)
        );

        assertSame(newParent, target.getParent());
        assertEquals(3L, response.parentId());
    }

    @Test
    void shouldRejectSelfParent() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        KnowledgePoint target = knowledgePoint(2L, "TCP", root);
        when(knowledgePointRepository.findById(2L)).thenReturn(Optional.of(target));

        KnowledgePointConflictException exception = assertThrows(
                KnowledgePointConflictException.class,
                () -> knowledgePointService.update(
                        2L,
                        new UpdateKnowledgePointRequest("TCP", 2L)
                )
        );

        assertEquals("KNOWLEDGE_POINT_SELF_PARENT", exception.getCode());
    }

    @Test
    void shouldRejectCycle() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        KnowledgePoint target = knowledgePoint(2L, "TCP", root);
        KnowledgePoint descendant = knowledgePoint(3L, "拥塞控制", target);
        when(knowledgePointRepository.findById(2L)).thenReturn(Optional.of(target));
        when(knowledgePointRepository.findById(3L))
                .thenReturn(Optional.of(descendant));

        KnowledgePointConflictException exception = assertThrows(
                KnowledgePointConflictException.class,
                () -> knowledgePointService.update(
                        2L,
                        new UpdateKnowledgePointRequest("TCP", 3L)
                )
        );

        assertEquals("KNOWLEDGE_POINT_CYCLE", exception.getCode());
    }

    @Test
    void shouldRejectCrossTreeMove() {
        KnowledgePoint firstRoot = knowledgePoint(1L, "408", null);
        KnowledgePoint secondRoot = knowledgePoint(2L, "数学", null);
        KnowledgePoint target = knowledgePoint(3L, "TCP", firstRoot);
        when(knowledgePointRepository.findById(3L)).thenReturn(Optional.of(target));
        when(knowledgePointRepository.findById(2L))
                .thenReturn(Optional.of(secondRoot));

        KnowledgePointConflictException exception = assertThrows(
                KnowledgePointConflictException.class,
                () -> knowledgePointService.update(
                        3L,
                        new UpdateKnowledgePointRequest("TCP", 2L)
                )
        );

        assertEquals(
                "KNOWLEDGE_POINT_CROSS_TREE_MOVE_FORBIDDEN",
                exception.getCode()
        );
    }

    @Test
    void shouldRejectMovingRootUnderAnotherNode() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        when(knowledgePointRepository.findById(1L)).thenReturn(Optional.of(root));

        KnowledgePointConflictException exception = assertThrows(
                KnowledgePointConflictException.class,
                () -> knowledgePointService.update(
                        1L,
                        new UpdateKnowledgePointRequest("408", 2L)
                )
        );

        assertEquals("KNOWLEDGE_POINT_ROOT_CHANGE_FORBIDDEN", exception.getCode());
    }

    @Test
    void shouldRejectPromotingChildToRoot() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        KnowledgePoint child = knowledgePoint(2L, "TCP", root);
        when(knowledgePointRepository.findById(2L)).thenReturn(Optional.of(child));

        KnowledgePointConflictException exception = assertThrows(
                KnowledgePointConflictException.class,
                () -> knowledgePointService.update(
                        2L,
                        new UpdateKnowledgePointRequest("TCP", null)
                )
        );

        assertEquals("KNOWLEDGE_POINT_ROOT_CHANGE_FORBIDDEN", exception.getCode());
    }

    @Test
    void shouldSynchronizeQuestionSubjectWhenRenameRoot() {
        KnowledgePoint root = knowledgePoint(1L, "408", null);
        Question question = question("408");
        when(knowledgePointRepository.findById(1L)).thenReturn(Optional.of(root));
        when(knowledgePointRepository.existsByParentIsNullAndNameAndIdNot(
                "计算机专业基础",
                1L
        )).thenReturn(false);
        when(questionRepository.findAllBySubject("408")).thenReturn(List.of(question));
        stubSavedEntity();

        knowledgePointService.update(
                1L,
                new UpdateKnowledgePointRequest("计算机专业基础", null)
        );

        assertEquals("计算机专业基础", root.getName());
        assertEquals("计算机专业基础", question.getSubject());
        verify(questionRepository).findAllBySubject("408");
    }

    @Test
    void shouldDeleteUnusedLeaf() {
        KnowledgePoint leaf = knowledgePoint(1L, "TCP", null);
        when(knowledgePointRepository.findById(1L)).thenReturn(Optional.of(leaf));
        when(knowledgePointRepository.existsByParent_Id(1L)).thenReturn(false);
        when(questionRepository.existsByKnowledgePoints_Id(1L)).thenReturn(false);

        MessageResponse response = knowledgePointService.delete(1L);

        assertEquals("知识点删除成功", response.message());
        verify(knowledgePointRepository).delete(leaf);
    }

    @Test
    void shouldRejectDeleteWhenKnowledgePointHasChildren() {
        KnowledgePoint parent = knowledgePoint(1L, "计算机网络", null);
        when(knowledgePointRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(knowledgePointRepository.existsByParent_Id(1L)).thenReturn(true);

        KnowledgePointConflictException exception = assertThrows(
                KnowledgePointConflictException.class,
                () -> knowledgePointService.delete(1L)
        );

        assertEquals("KNOWLEDGE_POINT_HAS_CHILDREN", exception.getCode());
        verify(questionRepository, never()).existsByKnowledgePoints_Id(any());
    }

    @Test
    void shouldRejectDeleteWhenKnowledgePointIsUsedByQuestion() {
        KnowledgePoint leaf = knowledgePoint(1L, "TCP", null);
        when(knowledgePointRepository.findById(1L)).thenReturn(Optional.of(leaf));
        when(knowledgePointRepository.existsByParent_Id(1L)).thenReturn(false);
        when(questionRepository.existsByKnowledgePoints_Id(1L)).thenReturn(true);

        KnowledgePointConflictException exception = assertThrows(
                KnowledgePointConflictException.class,
                () -> knowledgePointService.delete(1L)
        );

        assertEquals("KNOWLEDGE_POINT_IN_USE", exception.getCode());
    }

    @Test
    void shouldRejectDeleteWhenKnowledgePointDoesNotExist() {
        when(knowledgePointRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                KnowledgePointNotFoundException.class,
                () -> knowledgePointService.delete(99L)
        );
    }

    @Test
    void shouldBuildTreeAndSortEveryLevelById() {
        KnowledgePoint firstRoot = knowledgePoint(1L, "408", null);
        KnowledgePoint secondRoot = knowledgePoint(5L, "数学", null);
        KnowledgePoint firstChild = knowledgePoint(2L, "计算机网络", firstRoot);
        KnowledgePoint secondChild = knowledgePoint(4L, "操作系统", firstRoot);
        KnowledgePoint leaf = knowledgePoint(3L, "TCP", firstChild);
        when(knowledgePointRepository.findAllWithParentOrderByIdAsc())
                .thenReturn(List.of(secondChild, secondRoot, leaf, firstRoot, firstChild));

        List<KnowledgePointTreeNodeResponse> tree = knowledgePointService.getTree();

        assertEquals(List.of(1L, 5L), tree.stream().map(
                KnowledgePointTreeNodeResponse::id
        ).toList());
        assertEquals(List.of(2L, 4L), tree.getFirst().children().stream().map(
                KnowledgePointTreeNodeResponse::id
        ).toList());
        assertEquals(3L, tree.getFirst().children().getFirst().children().getFirst().id());
        assertTrue(tree.get(1).children().isEmpty());
    }

    private KnowledgePoint knowledgePoint(
            Long id,
            String name,
            KnowledgePoint parent
    ) {
        KnowledgePoint knowledgePoint = new KnowledgePoint(name, parent);
        ReflectionTestUtils.setField(knowledgePoint, "id", id);
        return knowledgePoint;
    }

    private Question question(String subject) {
        return new Question(
                "测试题目",
                "错误答案",
                "正确答案",
                "测试解析",
                "测试错误原因",
                subject
        );
    }

    private void stubSavedId(Long id) {
        when(knowledgePointRepository.save(any(KnowledgePoint.class)))
                .thenAnswer(invocation -> {
                    KnowledgePoint knowledgePoint = invocation.getArgument(0);
                    ReflectionTestUtils.setField(knowledgePoint, "id", id);
                    return knowledgePoint;
                });
    }

    private void stubSavedEntity() {
        when(knowledgePointRepository.save(any(KnowledgePoint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
