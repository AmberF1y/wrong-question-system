package com.wrongquestion.backend.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void shouldApplyMigrationsThroughVersionTwo() {
        assertNotNull(flyway.info().current());
        assertEquals(
                "2",
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

        assertEquals(List.of("1", "2"), successfulVersions);
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
}
