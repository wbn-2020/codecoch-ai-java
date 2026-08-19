-- Private recommendation drafts are owned by one user and never enter the public question bank.
-- Their practice records are linked by recommendation_item_id; question_id remains null.

SET @practice_question_id_nullable = (
    SELECT IF(
        EXISTS(
            SELECT 1
              FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'practice_record'
               AND column_name = 'question_id'
               AND is_nullable = 'NO'
        ),
        'ALTER TABLE `practice_record` MODIFY COLUMN `question_id` BIGINT DEFAULT NULL',
        'SELECT 1'
    )
);
PREPARE practice_question_id_stmt FROM @practice_question_id_nullable;
EXECUTE practice_question_id_stmt;
DEALLOCATE PREPARE practice_question_id_stmt;
