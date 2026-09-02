package com.wrongquestion.backend.knowledge.service;

import com.wrongquestion.backend.knowledge.dto.CreateKnowledgePointRequest;
import com.wrongquestion.backend.knowledge.dto.KnowledgePointResponse;
import com.wrongquestion.backend.knowledge.dto.KnowledgePointTreeNodeResponse;
import com.wrongquestion.backend.knowledge.dto.MessageResponse;
import com.wrongquestion.backend.knowledge.dto.UpdateKnowledgePointRequest;
import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointConflictException;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointNotFoundException;
import com.wrongquestion.backend.knowledge.exception.KnowledgePointValidationException;
import com.wrongquestion.backend.knowledge.repository.KnowledgePointRepository;
import com.wrongquestion.backend.question.entity.Question;
import com.wrongquestion.backend.question.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KnowledgePointService {

    private static final int ROOT_NAME_MAX_LENGTH = 50;
    private static final int KNOWLEDGE_POINT_NAME_MAX_LENGTH = 100;
    private static final String DELETE_SUCCESS_MESSAGE = "知识点删除成功";

    private final KnowledgePointRepository knowledgePointRepository;
    private final QuestionRepository questionRepository;

    public KnowledgePointService(
            KnowledgePointRepository knowledgePointRepository,
            QuestionRepository questionRepository
    ) {
        this.knowledgePointRepository = knowledgePointRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public List<KnowledgePointTreeNodeResponse> getTree() {
        List<KnowledgePoint> knowledgePoints = new ArrayList<>(
                knowledgePointRepository.findAllWithParentOrderByIdAsc()
        );
        knowledgePoints.sort(Comparator.comparing(KnowledgePoint::getId));

        Map<Long, MutableTreeNode> nodesById = new LinkedHashMap<>();
        for (KnowledgePoint knowledgePoint : knowledgePoints) {
            Long parentId = knowledgePoint.getParent() == null
                    ? null
                    : knowledgePoint.getParent().getId();
            nodesById.put(
                    knowledgePoint.getId(),
                    new MutableTreeNode(
                            knowledgePoint.getId(),
                            knowledgePoint.getName(),
                            parentId
                    )
            );
        }

        List<MutableTreeNode> roots = new ArrayList<>();
        for (KnowledgePoint knowledgePoint : knowledgePoints) {
            MutableTreeNode currentNode = nodesById.get(knowledgePoint.getId());
            KnowledgePoint parent = knowledgePoint.getParent();

            if (parent == null) {
                roots.add(currentNode);
                continue;
            }

            MutableTreeNode parentNode = nodesById.get(parent.getId());
            if (parentNode == null) {
                throw new IllegalStateException("知识点父节点数据不完整");
            }
            parentNode.children.add(currentNode);
        }

        return roots.stream()
                .map(MutableTreeNode::toResponse)
                .toList();
    }

    @Transactional
    public KnowledgePointResponse create(CreateKnowledgePointRequest request) {
        String normalizedName = normalizeName(request.name());

        KnowledgePoint parent = null;
        if (request.parentId() == null) {
            validateRootName(normalizedName);
            ensureRootNameAvailable(normalizedName, null);
        } else {
            parent = findKnowledgePoint(request.parentId(), "父知识点不存在");
            ensureSiblingNameAvailable(parent.getId(), normalizedName, null);
        }

        KnowledgePoint savedKnowledgePoint = knowledgePointRepository.save(
                new KnowledgePoint(normalizedName, parent)
        );
        return toResponse(savedKnowledgePoint);
    }

    @Transactional
    public KnowledgePointResponse update(
            Long id,
            UpdateKnowledgePointRequest request
    ) {
        KnowledgePoint knowledgePoint = findKnowledgePoint(id, "知识点不存在");
        String normalizedName = normalizeName(request.name());

        if (knowledgePoint.getParent() == null) {
            updateRoot(knowledgePoint, normalizedName, request.parentId());
        } else {
            updateChild(knowledgePoint, normalizedName, request.parentId());
        }

        return toResponse(knowledgePointRepository.save(knowledgePoint));
    }

    @Transactional
    public MessageResponse delete(Long id) {
        KnowledgePoint knowledgePoint = findKnowledgePoint(id, "知识点不存在");

        if (knowledgePointRepository.existsByParent_Id(id)) {
            throw conflict(
                    "KNOWLEDGE_POINT_HAS_CHILDREN",
                    "知识点存在子节点，不能删除"
            );
        }

        if (questionRepository.existsByKnowledgePoints_Id(id)) {
            throw conflict(
                    "KNOWLEDGE_POINT_IN_USE",
                    "知识点已被错题引用，不能删除"
            );
        }

        knowledgePointRepository.delete(knowledgePoint);
        return new MessageResponse(DELETE_SUCCESS_MESSAGE);
    }

    private void updateRoot(
            KnowledgePoint knowledgePoint,
            String normalizedName,
            Long requestedParentId
    ) {
        if (requestedParentId != null) {
            throw conflict(
                    "KNOWLEDGE_POINT_ROOT_CHANGE_FORBIDDEN",
                    "根节点不能变成其他节点的子节点"
            );
        }

        validateRootName(normalizedName);
        ensureRootNameAvailable(normalizedName, knowledgePoint.getId());

        String oldName = knowledgePoint.getName();
        if (!oldName.equals(normalizedName)) {
            List<Question> relatedQuestions = questionRepository.findAllBySubject(oldName);
            relatedQuestions.forEach(question -> question.setSubject(normalizedName));
            knowledgePoint.setName(normalizedName);
        }
    }

    private void updateChild(
            KnowledgePoint knowledgePoint,
            String normalizedName,
            Long requestedParentId
    ) {
        if (requestedParentId == null) {
            throw conflict(
                    "KNOWLEDGE_POINT_ROOT_CHANGE_FORBIDDEN",
                    "普通节点不能升级为根节点"
            );
        }

        if (Objects.equals(knowledgePoint.getId(), requestedParentId)) {
            throw conflict(
                    "KNOWLEDGE_POINT_SELF_PARENT",
                    "知识点不能把自己设为父节点"
            );
        }

        KnowledgePoint newParent = findKnowledgePoint(
                requestedParentId,
                "父知识点不存在"
        );

        if (!Objects.equals(
                findRoot(knowledgePoint).getId(),
                findRoot(newParent).getId()
        )) {
            throw conflict(
                    "KNOWLEDGE_POINT_CROSS_TREE_MOVE_FORBIDDEN",
                    "知识点不能跨根节点移动"
            );
        }

        ensureNotDescendant(knowledgePoint, newParent);
        ensureSiblingNameAvailable(
                newParent.getId(),
                normalizedName,
                knowledgePoint.getId()
        );

        knowledgePoint.setName(normalizedName);
        knowledgePoint.setParent(newParent);
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new KnowledgePointValidationException("知识点名称不能为空");
        }

        String normalizedName = name.strip();
        if (normalizedName.isBlank()) {
            throw new KnowledgePointValidationException("知识点名称不能为空");
        }

        if (normalizedName.length() > KNOWLEDGE_POINT_NAME_MAX_LENGTH) {
            throw new KnowledgePointValidationException(
                    "知识点名称不能超过100个字符"
            );
        }

        return normalizedName;
    }

    private void validateRootName(String name) {
        if (name.length() > ROOT_NAME_MAX_LENGTH) {
            throw new KnowledgePointValidationException(
                    "根节点名称不能超过50个字符"
            );
        }
    }

    private void ensureRootNameAvailable(String name, Long excludedId) {
        boolean exists = excludedId == null
                ? knowledgePointRepository.existsByParentIsNullAndName(name)
                : knowledgePointRepository.existsByParentIsNullAndNameAndIdNot(
                        name,
                        excludedId
                );

        if (exists) {
            throw conflict(
                    "KNOWLEDGE_POINT_NAME_CONFLICT",
                    "已存在同名根节点"
            );
        }
    }

    private void ensureSiblingNameAvailable(
            Long parentId,
            String name,
            Long excludedId
    ) {
        boolean exists = excludedId == null
                ? knowledgePointRepository.existsByParent_IdAndName(parentId, name)
                : knowledgePointRepository.existsByParent_IdAndNameAndIdNot(
                        parentId,
                        name,
                        excludedId
                );

        if (exists) {
            throw conflict(
                    "KNOWLEDGE_POINT_NAME_CONFLICT",
                    "同一父节点下已存在同名知识点"
            );
        }
    }

    private void ensureNotDescendant(
            KnowledgePoint knowledgePoint,
            KnowledgePoint newParent
    ) {
        KnowledgePoint current = newParent;
        while (current != null) {
            if (Objects.equals(current.getId(), knowledgePoint.getId())) {
                throw conflict(
                        "KNOWLEDGE_POINT_CYCLE",
                        "知识点不能移动到自己的后代节点下"
                );
            }
            current = current.getParent();
        }
    }

    private KnowledgePoint findRoot(KnowledgePoint knowledgePoint) {
        KnowledgePoint current = knowledgePoint;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    private KnowledgePoint findKnowledgePoint(Long id, String message) {
        return knowledgePointRepository.findById(id)
                .orElseThrow(() -> new KnowledgePointNotFoundException(message));
    }

    private KnowledgePointConflictException conflict(
            String code,
            String message
    ) {
        return new KnowledgePointConflictException(code, message);
    }

    private KnowledgePointResponse toResponse(KnowledgePoint knowledgePoint) {
        Long parentId = knowledgePoint.getParent() == null
                ? null
                : knowledgePoint.getParent().getId();
        return new KnowledgePointResponse(
                knowledgePoint.getId(),
                knowledgePoint.getName(),
                parentId
        );
    }

    private static final class MutableTreeNode {

        private final Long id;
        private final String name;
        private final Long parentId;
        private final List<MutableTreeNode> children = new ArrayList<>();

        private MutableTreeNode(Long id, String name, Long parentId) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
        }

        private KnowledgePointTreeNodeResponse toResponse() {
            return new KnowledgePointTreeNodeResponse(
                    id,
                    name,
                    parentId,
                    children.stream()
                            .map(MutableTreeNode::toResponse)
                            .toList()
            );
        }
    }
}
