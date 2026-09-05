-- V4_123: preserve full producer correlation IDs for async task and dead-letter records.
-- Some producer IDs combine a business type, business ID, and a 32-character trace ID,
-- which can exceed the original 64-character columns. Do not truncate correlation IDs:
-- task lookup, retry, and dead-letter recovery require exact equality.

DROP PROCEDURE IF EXISTS widen_async_message_id_capacity;
DELIMITER //
CREATE PROCEDURE widen_async_message_id_capacity()
BEGIN
    DECLARE async_task_column_count INT DEFAULT 0;
    DECLARE async_task_length BIGINT DEFAULT 0;
    DECLARE dead_letter_column_count INT DEFAULT 0;
    DECLARE dead_letter_length BIGINT DEFAULT 0;

    SELECT COUNT(1), COALESCE(MAX(character_maximum_length), 0)
      INTO async_task_column_count, async_task_length
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'async_task'
       AND column_name = 'message_id';

    SELECT COUNT(1), COALESCE(MAX(character_maximum_length), 0)
      INTO dead_letter_column_count, dead_letter_length
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'message_dead_letter'
       AND column_name = 'message_id';

    IF async_task_column_count = 0 OR dead_letter_column_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_123 requires async_task and message_dead_letter message_id columns from V3_007';
    END IF;

    IF async_task_length < 128 THEN
        ALTER TABLE `async_task`
            MODIFY COLUMN `message_id` VARCHAR(128) NOT NULL
                COMMENT 'MQ message ID (messageId), unique';
    END IF;

    IF dead_letter_length < 128 THEN
        ALTER TABLE `message_dead_letter`
            MODIFY COLUMN `message_id` VARCHAR(128) NOT NULL
                COMMENT 'Original MQ message ID';
    END IF;
END//
DELIMITER ;

CALL widen_async_message_id_capacity();
DROP PROCEDURE IF EXISTS widen_async_message_id_capacity;
