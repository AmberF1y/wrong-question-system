-- Deterministic small performance dataset.
-- Safety is enforced by Initialize-PerformanceDataset.ps1 before this file is sourced.
-- This script intentionally contains no DROP TABLE, TRUNCATE, DELETE, or database selection.

CREATE TABLE performance_dataset_metadata (
    dataset_id VARCHAR(80) NOT NULL,
    scale_name VARCHAR(20) NOT NULL,
    prepared_at DATETIME(6) NOT NULL,
    subject_count INT NOT NULL,
    knowledge_point_count INT NOT NULL,
    question_count INT NOT NULL,
    relation_count INT NOT NULL,
    review_state_count INT NOT NULL,
    review_record_count INT NOT NULL,
    image_question_count INT NOT NULL,
    PRIMARY KEY (dataset_id)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci
COMMENT = '专用性能测试数据集元数据';

CREATE TABLE performance_question_fixture (
    question_id BIGINT NOT NULL,
    fixture_role VARCHAR(40) NOT NULL,
    PRIMARY KEY (question_id, fixture_role),
    CONSTRAINT fk_perf_fixture_question
        FOREIGN KEY (question_id)
        REFERENCES question (id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci
COMMENT = '专用性能测试题目角色';

DELIMITER $$

CREATE PROCEDURE performance_seed_small()
BEGIN
    DECLARE v_subject_index INT DEFAULT 1;
    DECLARE v_knowledge_index INT DEFAULT 1;
    DECLARE v_question_index INT DEFAULT 1;
    DECLARE v_root_id BIGINT;
    DECLARE v_root_name VARCHAR(50);
    DECLARE v_question_id BIGINT;
    DECLARE v_first_knowledge_id BIGINT;
    DECLARE v_second_knowledge_id BIGINT;
    DECLARE v_first_offset INT;
    DECLARE v_second_offset INT;
    DECLARE v_bucket INT;
    DECLARE v_image_path VARCHAR(500);

    WHILE v_subject_index <= 5 DO
        SET v_root_name = CONCAT(
                'PERF-SUBJECT-',
                LPAD(v_subject_index, 2, '0')
        );
        INSERT INTO knowledge_point (name, parent_id)
        VALUES (v_root_name, NULL);
        SET v_root_id = LAST_INSERT_ID();

        SET v_knowledge_index = 1;
        WHILE v_knowledge_index <= 20 DO
            INSERT INTO knowledge_point (name, parent_id)
            VALUES (
                CONCAT(
                        v_root_name,
                        '-KP-',
                        LPAD(v_knowledge_index, 2, '0')
                ),
                v_root_id
            );
            SET v_knowledge_index = v_knowledge_index + 1;
        END WHILE;

        SET v_subject_index = v_subject_index + 1;
    END WHILE;

    SET v_question_index = 1;
    WHILE v_question_index <= 1000 DO
        SET v_subject_index = MOD(v_question_index - 1, 5) + 1;
        SET v_root_name = CONCAT(
                'PERF-SUBJECT-',
                LPAD(v_subject_index, 2, '0')
        );
        SELECT id
        INTO v_root_id
        FROM knowledge_point
        WHERE parent_id IS NULL
          AND name = v_root_name;

        INSERT INTO question (
            question_text,
            wrong_answer,
            correct_answer,
            analysis,
            error_reason,
            subject,
            image_path
        )
        VALUES (
            CONCAT(
                    '[PERF small-1000-v1 #',
                    LPAD(v_question_index, 4, '0'),
                    '] 当 $x^2+',
                    v_question_index,
                    '=0$ 时，分析该测试题的主要条件与结论。'
            ),
            CONCAT(
                    '性能夹具错误答案 ',
                    v_question_index,
                    '：忽略了题目中的边界条件。'
            ),
            CONCAT(
                    '性能夹具正确答案 ',
                    v_question_index,
                    '：应先整理条件，再完成计算。'
            ),
            CONCAT(
                    '性能夹具解析 ',
                    v_question_index,
                    '。本段包含稳定长度的中文说明，用于模拟日常错题详情读取；',
                    '先确定定义域，再按步骤推导，最后检查结果。'
            ),
            CONCAT(
                    '性能夹具错误原因 ',
                    v_question_index,
                    '：概念边界不清。'
            ),
            v_root_name,
            NULL
        );
        SET v_question_id = LAST_INSERT_ID();

        SET v_first_offset = MOD(v_question_index - 1, 20);
        SET v_second_offset = MOD(v_first_offset + 7, 20);

        SELECT id
        INTO v_first_knowledge_id
        FROM knowledge_point
        WHERE parent_id = v_root_id
        ORDER BY id
        LIMIT v_first_offset, 1;

        SELECT id
        INTO v_second_knowledge_id
        FROM knowledge_point
        WHERE parent_id = v_root_id
        ORDER BY id
        LIMIT v_second_offset, 1;

        INSERT INTO question_knowledge_point (
            question_id,
            knowledge_point_id
        )
        VALUES
            (v_question_id, v_first_knowledge_id),
            (v_question_id, v_second_knowledge_id);

        SET v_bucket = MOD(v_question_index - 1, 100);
        IF v_bucket < 35 THEN
            INSERT INTO question_review_state (
                question_id,
                review_status,
                next_review_date,
                consecutive_proficient_count,
                last_reviewed_at,
                version
            )
            VALUES (
                v_question_id,
                'ACTIVE',
                DATE_SUB(
                        CURRENT_DATE,
                        INTERVAL MOD(v_question_index, 30) DAY
                ),
                MOD(v_question_index, 2),
                IF(
                        MOD(v_question_index, 2) = 1,
                        DATE_SUB(
                                UTC_TIMESTAMP(6),
                                INTERVAL v_question_index SECOND
                        ),
                        NULL
                ),
                MOD(v_question_index, 2)
            );
        ELSEIF v_bucket < 80 THEN
            INSERT INTO question_review_state (
                question_id,
                review_status,
                next_review_date,
                consecutive_proficient_count,
                last_reviewed_at,
                version
            )
            VALUES (
                v_question_id,
                'ACTIVE',
                DATE_ADD(
                        CURRENT_DATE,
                        INTERVAL MOD(v_question_index, 30) + 1 DAY
                ),
                MOD(v_question_index, 2),
                IF(
                        MOD(v_question_index, 2) = 1,
                        DATE_SUB(
                                UTC_TIMESTAMP(6),
                                INTERVAL v_question_index SECOND
                        ),
                        NULL
                ),
                MOD(v_question_index, 2)
            );
        ELSE
            INSERT INTO question_review_state (
                question_id,
                review_status,
                next_review_date,
                consecutive_proficient_count,
                last_reviewed_at,
                version
            )
            VALUES (
                v_question_id,
                'MASTERED',
                NULL,
                2,
                DATE_SUB(
                        UTC_TIMESTAMP(6),
                        INTERVAL v_question_index SECOND
                ),
                2
            );
        END IF;

        IF MOD(v_question_index, 2) = 0 THEN
            INSERT INTO review_record (
                question_id,
                event_type,
                rating,
                business_date,
                occurred_at,
                scheduled_review_date,
                resulting_status,
                resulting_next_review_date,
                resulting_proficient_count
            )
            VALUES (
                v_question_id,
                'EVALUATION',
                'NOT_KNOWN',
                DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY),
                DATE_SUB(
                        UTC_TIMESTAMP(6),
                        INTERVAL v_question_index + 2000 SECOND
                ),
                DATE_SUB(CURRENT_DATE, INTERVAL 2 DAY),
                'ACTIVE',
                DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY),
                0
            );
        END IF;

        IF MOD(v_question_index, 10) = 0 THEN
            SET v_image_path = CONCAT(
                    'questions/',
                    v_question_id,
                    '/00000000-0000-4000-8000-',
                    LOWER(LPAD(HEX(v_question_id), 12, '0')),
                    '.png'
            );
            UPDATE question
            SET image_path = v_image_path
            WHERE id = v_question_id;

            INSERT INTO performance_question_fixture (
                question_id,
                fixture_role
            )
            VALUES (
                v_question_id,
                IF(
                        MOD(v_question_index, 100) = 0,
                        'IMAGE_LARGE',
                        'IMAGE_SMALL'
                )
            );
        END IF;

        IF MOD(v_question_index, 10) = 1 THEN
            INSERT INTO performance_question_fixture (
                question_id,
                fixture_role
            )
            VALUES (v_question_id, 'DETAIL');
        END IF;

        IF v_question_index BETWEEN 36 AND 55 THEN
            INSERT INTO performance_question_fixture (
                question_id,
                fixture_role
            )
            VALUES (v_question_id, 'WRITE_UPDATE');
        END IF;

        IF v_question_index BETWEEN 1 AND 20 THEN
            INSERT INTO performance_question_fixture (
                question_id,
                fixture_role
            )
            VALUES (v_question_id, 'REVIEW_CONCURRENT');
        END IF;

        SET v_question_index = v_question_index + 1;
    END WHILE;

    INSERT INTO performance_dataset_metadata (
        dataset_id,
        scale_name,
        prepared_at,
        subject_count,
        knowledge_point_count,
        question_count,
        relation_count,
        review_state_count,
        review_record_count,
        image_question_count
    )
    SELECT
        'small-1000-v1',
        'small',
        UTC_TIMESTAMP(6),
        (
            SELECT COUNT(*)
            FROM knowledge_point
            WHERE parent_id IS NULL
        ),
        (SELECT COUNT(*) FROM knowledge_point),
        (SELECT COUNT(*) FROM question),
        (SELECT COUNT(*) FROM question_knowledge_point),
        (SELECT COUNT(*) FROM question_review_state),
        (SELECT COUNT(*) FROM review_record),
        (
            SELECT COUNT(*)
            FROM question
            WHERE image_path IS NOT NULL
        );
END$$

DELIMITER ;

CALL performance_seed_small();
DROP PROCEDURE performance_seed_small;
