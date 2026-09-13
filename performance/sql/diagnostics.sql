-- Read-only diagnostic examples for the dedicated performance database only.
-- Do not source this file against wrong_question_system or wrong_question_system_test.
-- Replace IDs only with values from performance/.local/perf-environment.json.

EXPLAIN ANALYZE
SELECT id
FROM question
ORDER BY id DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT id
FROM question
ORDER BY id DESC
LIMIT 20 OFFSET 500;

EXPLAIN ANALYZE
SELECT id
FROM question
WHERE subject = 'PERF-SUBJECT-01'
ORDER BY id DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT q.id
FROM question q
JOIN question_review_state s ON s.question_id = q.id
WHERE s.review_status = 'ACTIVE'
ORDER BY q.id DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT COUNT(*)
FROM question_review_state
WHERE review_status = 'ACTIVE'
  AND next_review_date <= CURRENT_DATE;

EXPLAIN ANALYZE
SELECT s.question_id
FROM question_review_state s
JOIN question q ON q.id = s.question_id
WHERE s.review_status = 'ACTIVE'
  AND s.next_review_date <= CURRENT_DATE
ORDER BY s.next_review_date ASC, s.question_id ASC
LIMIT 1;

EXPLAIN ANALYZE
SELECT kp.id, kp.name, kp.parent_id
FROM knowledge_point kp
LEFT JOIN knowledge_point parent ON parent.id = kp.parent_id
ORDER BY kp.id;
