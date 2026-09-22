ALTER TABLE `review_record`
    DROP CHECK `chk_review_record_event_consistency`;

ALTER TABLE `review_record`
    MODIFY COLUMN `event_type` VARCHAR(20) NOT NULL
        COMMENT 'EVALUATION、REACTIVATION或SPOT_CHECK',
    ADD COLUMN `spot_check_business_date` DATE
        GENERATED ALWAYS AS (
            CASE
                WHEN `event_type` = 'SPOT_CHECK' THEN `business_date`
                ELSE NULL
            END
        ) STORED
        COMMENT '每日抽查唯一键，其他事件为空',
    ADD UNIQUE KEY `uk_review_record_spot_check_date`
        (`spot_check_business_date`),
    ADD KEY `idx_review_record_event_date_question`
        (`event_type`, `business_date`, `question_id`),
    ADD CONSTRAINT `chk_review_record_event_consistency`
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
            OR
            (
                `event_type` = 'SPOT_CHECK'
                AND `rating` IS NOT NULL
                AND `rating` IN (
                    'NOT_KNOWN',
                    'FUZZY',
                    'BASICALLY_MASTERED',
                    'PROFICIENT'
                )
                AND `scheduled_review_date` IS NULL
            )
        );
