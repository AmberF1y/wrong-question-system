-- F-001 to F-004 schema baseline.
-- This migration intentionally does not select or create a database.

CREATE TABLE `question` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '错题主键',

    `question_text` TEXT NOT NULL COMMENT '完整题目文字',
    `wrong_answer` TEXT NOT NULL COMMENT '用户的错误答案',
    `correct_answer` TEXT NOT NULL COMMENT '正确答案',
    `analysis` TEXT NOT NULL COMMENT '题目解析',
    `error_reason` TEXT NOT NULL COMMENT '错误原因',

    `subject` VARCHAR(50) NOT NULL COMMENT '所属科目',

    `image_path` VARCHAR(500) NULL COMMENT '原始题目图片相对路径',

    `created_time` DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间',

    `updated_time` DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '最近更新时间',

    PRIMARY KEY (`id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci
COMMENT = '错题表';


CREATE TABLE `knowledge_point` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识点主键',

    `name` VARCHAR(100) NOT NULL COMMENT '知识点名称',

    `parent_id` BIGINT NULL COMMENT '父知识点ID，NULL表示根节点',

    `created_time` DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间',

    `updated_time` DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '最近更新时间',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_knowledge_point_parent_name`
        (`parent_id`, `name`),

    KEY `idx_knowledge_point_parent_id`
        (`parent_id`),

    CONSTRAINT `fk_knowledge_point_parent`
        FOREIGN KEY (`parent_id`)
        REFERENCES `knowledge_point` (`id`)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci
COMMENT = '知识点表';


CREATE TABLE `question_knowledge_point` (
    `question_id` BIGINT NOT NULL COMMENT '错题ID',

    `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',

    PRIMARY KEY (`question_id`, `knowledge_point_id`),

    KEY `idx_qkp_knowledge_point_id`
        (`knowledge_point_id`),

    CONSTRAINT `fk_qkp_question`
        FOREIGN KEY (`question_id`)
        REFERENCES `question` (`id`)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,

    CONSTRAINT `fk_qkp_knowledge_point`
        FOREIGN KEY (`knowledge_point_id`)
        REFERENCES `knowledge_point` (`id`)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci
COMMENT = '错题与知识点关联表';
