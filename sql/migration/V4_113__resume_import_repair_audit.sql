-- Encrypted, reversible audit trail for bounded historical resume-import repair batches.
-- The application refuses execution when RESUME_IMPORT_REPAIR_AUDIT_KEY is unavailable.

CREATE TABLE IF NOT EXISTS `resume_import_repair_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `repair_batch_id` VARCHAR(64) NOT NULL,
    `analysis_record_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `resume_id` BIGINT DEFAULT NULL,
    `actor_user_id` BIGINT DEFAULT NULL,
    `operation` VARCHAR(16) NOT NULL COMMENT 'REPAIR/ROLLBACK',
    `status` VARCHAR(48) NOT NULL DEFAULT 'RUNNING',
    `before_snapshot_ciphertext` MEDIUMTEXT DEFAULT NULL,
    `after_snapshot_ciphertext` MEDIUMTEXT DEFAULT NULL,
    `before_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `after_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `before_validation_status` VARCHAR(32) DEFAULT NULL,
    `after_validation_status` VARCHAR(32) DEFAULT NULL,
    `reason_code` VARCHAR(64) NOT NULL,
    `note` VARCHAR(1000) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resume_import_repair_batch_record_op` (`repair_batch_id`, `analysis_record_id`, `operation`, `deleted`),
    KEY `idx_resume_import_repair_record` (`analysis_record_id`, `created_at`),
    KEY `idx_resume_import_repair_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bounded historical resume import repair audit';
