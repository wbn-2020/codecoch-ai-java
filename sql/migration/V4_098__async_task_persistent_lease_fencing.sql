-- Persist the RUNNING task owner independently from the lease freshness timestamp.
ALTER TABLE `async_task`
    ADD COLUMN `lease_token` VARCHAR(64) NULL
        COMMENT 'Persistent owner/fencing token for a RUNNING task lease'
        AFTER `status`;

-- Existing active workers need a fence before the new application version starts.
UPDATE `async_task`
   SET `lease_token` = REPLACE(UUID(), '-', '')
 WHERE `deleted` = 0
   AND `status` = 'RUNNING'
   AND (`lease_token` IS NULL OR `lease_token` = '');
