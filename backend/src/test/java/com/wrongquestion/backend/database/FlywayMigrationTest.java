package com.wrongquestion.backend.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldUseDedicatedTestDatabase() {
        assertEquals(
                "wrong_question_system_test",
                jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)
        );
    }

    @Test
    void shouldApplyMigrationsThroughVersionThree() {
        assertNotNull(flyway.info().current());
        assertEquals(
                "3",
                flyway.info().current().getVersion().toString()
        );

        List<String> successfulVersions = jdbcTemplate.queryForList(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                  AND version IS NOT NULL
                ORDER BY installed_rank
                """,
                String.class
        );

        assertEquals(List.of("1", "2", "3"), successfulVersions);
    }

    @Test
    void shouldCreateRollingReviewTablesAndIndexes() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'question',
                      'knowledge_point',
                      'question_knowledge_point',
                      'question_review_state',
                      'review_record'
                  )
                """,
                Integer.class
        );

        String reviewStateIndexColumns = jdbcTemplate.queryForObject(
                """
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'question_review_state'
                  AND index_name = 'idx_qrs_queue'
                """,
                String.class
        );

        String reviewRecordIndexColumns = jdbcTemplate.queryForObject(
                """
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'review_record'
                  AND index_name = 'idx_review_record_question_time'
                """,
                String.class
        );

        String questionSubjectIndexColumns = jdbcTemplate.queryForObject(
                """
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'question'
                  AND index_name = 'idx_question_subject'
                """,
                String.class
        );

        assertEquals(5, tableCount);
        assertEquals(
                "review_status,next_review_date,question_id",
                reviewStateIndexColumns
        );
        assertEquals(
                "question_id,occurred_at,id",
                reviewRecordIndexColumns
        );
        assertEquals("subject", questionSubjectIndexColumns);
    }

    @Test
    void shouldCreateMasteredSpotCheckConstraintsAndIndexes() {
        String spotCheckIndexColumns = jdbcTemplate.queryForObject(
                """
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'review_record'
                  AND index_name = 'idx_review_record_event_date_question'
                """,
                String.class
        );
        String dailyUniqueColumns = jdbcTemplate.queryForObject(
                """
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'review_record'
                  AND index_name = 'uk_review_record_spot_check_date'
                  AND non_unique = 0
                """,
                String.class
        );
        String generationExpression = jdbcTemplate.queryForObject(
                """
                SELECT generation_expression
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'review_record'
                  AND column_name = 'spot_check_business_date'
                """,
                String.class
        );
        String eventConstraint = jdbcTemplate.queryForObject(
                """
                SELECT check_clause
                FROM information_schema.check_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_name = 'chk_review_record_event_consistency'
                """,
                String.class
        );

        assertEquals(
                "event_type,business_date,question_id",
                spotCheckIndexColumns
        );
        assertEquals("spot_check_business_date", dailyUniqueColumns);
        assertNotNull(generationExpression);
        assertTrue(generationExpression.contains("event_type"));
        assertTrue(generationExpression.contains("business_date"));
        assertTrue(generationExpression.contains("SPOT_CHECK"));
        assertNotNull(eventConstraint);
        assertTrue(eventConstraint.contains("SPOT_CHECK"));
    }
}
