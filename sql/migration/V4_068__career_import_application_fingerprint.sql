ALTER TABLE job_application
    ADD COLUMN import_fingerprint VARCHAR(64) NULL
        COMMENT 'Stable fingerprint for atomically deduplicating imported application rows'
        AFTER note,
    ADD UNIQUE KEY uk_job_application_import_fingerprint
        (user_id, import_fingerprint, deleted);
