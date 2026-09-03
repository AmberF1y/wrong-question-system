CREATE TABLE `question_review_state` (
    `question_id` BIGINT NOT NULL COMMENT '错题ID，同时作为复习状态主键',
    `review_status` VARCHAR(20) NOT NULL COMMENT 'ACTIVE或MASTERED',
    `next_review_date` DATE NULL COMMENT '下一次复习业务日期',
    `consecutive_proficient_count` INT NOT NULL COMMENT '连续熟练次数',
    `last_reviewed_at` DATETIME(6) NULL COMMENT '最后一次实际评价时刻，UTC语义',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA乐观锁版本',

    PRIMARY KEY (`question_id`),

    KEY `idx_qrs_queue`
        (`review_status`, `next_review_date`, `question_id`),

    CONSTRAINT `fk_qrs_question`
        FOREIGN KEY (`question_id`)
        REFERENCES `question` (`id`)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,

    CONSTRAINT `chk_qrs_state_consistency`
        CHECK (
            (
                `review_status` = 'ACTIVE'
                AND `next_review_date` IS NOT NULL
                AND `consecutive_proficient_count` IN (0, 1)
            )
            OR
            (
                `review_status` = 'MASTERED'
                AND `next_review_date` IS NULL
                AND `consecutive_proficient_count` = 2
                AND `last_reviewed_at` IS NOT NULL
            )
        ),

    CONSTRAINT `chk_qrs_version_non_negative`
        CHECK (`version` >= 0)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci
COMMENT = '错题当前复习状态表';


CREATE TABLE `review_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '复习历史主键',
    `question_id` BIGINT NOT NULL COMMENT '错题ID',
    `event_type` VARCHAR(20) NOT NULL COMMENT 'EVALUATION或REACTIVATION',
    `rating` VARCHAR(30) NULL COMMENT '复习评价，重新加入事件为空',
    `business_date` DATE NOT NULL COMMENT '事件所属业务日期',
    `occurred_at` DATETIME(6) NOT NULL COMMENT '事件实际发生时刻，UTC语义',
    `scheduled_review_date` DATE NULL COMMENT '事件发生前计划复习日期',
    `resulting_status` VARCHAR(20) NOT NULL COMMENT '事件后的复习状态',
    `resulting_next_review_date` DATE NULL COMMENT '事件后的下一次复习日期',
    `resulting_proficient_count` INT NOT NULL COMMENT '事件后的连续熟练次数',

    PRIMARY KEY (`id`),

    KEY `idx_review_record_question_time`
        (`question_id`, `occurred_at`, `id`),

    CONSTRAINT `fk_review_record_question`
        FOREIGN KEY (`question_id`)
        REFERENCES `question` (`id`)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,

    CONSTRAINT `chk_review_record_event_consistency`
        CHECK (
            (
                `event_type` = 'EVALUATION'
                AND `rating` IS NOT NULL
                AND `rating` IN (
                    'NOT_KNOWN',
                    'FUZZY',
                    'BASICALLY_MASTERED',
                    'PROFICIENT'
                )
                AND `scheduled_review_date` IS NOT NULL
            )
            OR
            (
                `event_type` = 'REACTIVATION'
                AND `rating` IS NULL
                AND `scheduled_review_date` IS NULL
            )
        ),

    CONSTRAINT `chk_review_record_result_consistency`
        CHECK (
            (
                `resulting_status` = 'ACTIVE'
                AND `resulting_next_review_date` IS NOT NULL
                AND `resulting_proficient_count` IN (0, 1)
            )
            OR
            (
                `resulting_status` = 'MASTERED'
                AND `resulting_next_review_date` IS NULL
                AND `resulting_proficient_count` = 2
            )
        )
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci
COMMENT = '复习事件历史表';


CREATE INDEX `idx_question_subject`
    ON `question` (`subject`);


INSERT INTO `question_review_state` (
    `question_id`,
    `review_status`,
    `next_review_date`,
    `consecutive_proficient_count`,
    `last_reviewed_at`,
    `version`
)
SELECT
    `id`,
    'ACTIVE',
    DATE_ADD(DATE(`created_time`), INTERVAL 1 DAY),
    0,
    NULL,
    0
FROM `question`;
