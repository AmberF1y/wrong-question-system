package com.wrongquestion.backend.knowledge.repository;

import com.wrongquestion.backend.knowledge.entity.KnowledgePoint;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
class KnowledgePointRepositoryTest {

    @Autowired
    private KnowledgePointRepository knowledgePointRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndLoadKnowledgePointWithParent() {

        // 1. 创建根知识点
        KnowledgePoint root =
                new KnowledgePoint("408测试根节点", null);

        knowledgePointRepository.save(root);

        // 2. 创建子知识点，并关联根节点
        KnowledgePoint child =
                new KnowledgePoint("计算机网络测试节点", root);

        knowledgePointRepository.save(child);

        // 3. 强制将当前修改写入数据库
        entityManager.flush();

        Long rootId = root.getId();
        Long childId = child.getId();

        assertNotNull(rootId);
        assertNotNull(childId);

        // 4. 清空当前 JPA 缓存
        // 确保下面的数据重新从数据库读取
        entityManager.clear();

        // 5. 重新查询子知识点
        KnowledgePoint savedChild = knowledgePointRepository
                .findById(childId)
                .orElseThrow();

        // 6. 验证子知识点自身
        assertEquals(
                "计算机网络测试节点",
                savedChild.getName()
        );

        // 7. 验证 parent 自关联
        assertNotNull(savedChild.getParent());

        assertEquals(
                rootId,
                savedChild.getParent().getId()
        );

        assertEquals(
                "408测试根节点",
                savedChild.getParent().getName()
        );

        // 8. 再查询根节点，验证根节点没有父节点
        KnowledgePoint savedRoot = knowledgePointRepository
                .findById(rootId)
                .orElseThrow();

        assertEquals(
                "408测试根节点",
                savedRoot.getName()
        );

        assertNull(savedRoot.getParent());
    }
}