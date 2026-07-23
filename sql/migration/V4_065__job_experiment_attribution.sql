CREATE TABLE IF NOT EXISTS job_experiment_hypothesis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    legacy_experiment_id BIGINT NULL,
    name VARCHAR(128) NOT NULL,
    statement VARCHAR(1000) NOT NULL,
    primary_metric VARCHAR(40) NOT NULL DEFAULT 'INTERVIEW',
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    attribution_window_days INT NOT NULL DEFAULT 14,
    min_sample_per_variant INT NOT NULL DEFAULT 10,
    allocation_salt VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jeh_user_legacy (user_id, legacy_experiment_id),
    KEY idx_jeh_user_status (user_id, status, deleted, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='D1/D2 job search experiment hypothesis';

CREATE TABLE IF NOT EXISTS job_experiment_variant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    hypothesis_id BIGINT NOT NULL,
    variant_code VARCHAR(40) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NULL,
    treatment_json TEXT NULL,
    allocation_weight INT NOT NULL DEFAULT 1,
    control_flag TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jev_hypothesis_code (hypothesis_id, variant_code, deleted),
    KEY idx_jev_user_hypothesis (user_id, hypothesis_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='D1/D2 experiment variant';

CREATE TABLE IF NOT EXISTS job_experiment_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    hypothesis_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    assignment_key VARCHAR(160) NOT NULL,
    assignment_method VARCHAR(32) NOT NULL DEFAULT 'STABLE_HASH',
    assigned_at DATETIME NOT NULL,
    job_family VARCHAR(100) NOT NULL,
    channel VARCHAR(100) NOT NULL,
    time_bucket DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_jea_hypothesis_application (hypothesis_id, application_id, deleted),
    KEY idx_jea_user_hypothesis (user_id, hypothesis_id, assigned_at, deleted),
    KEY idx_jea_variant_strata (variant_id, job_family, channel, time_bucket, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Stable application assignment with attribution strata snapshot';

CREATE TABLE IF NOT EXISTS job_experiment_cohort (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    hypothesis_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    job_family VARCHAR(100) NULL,
    channel VARCHAR(100) NULL,
    window_start DATETIME NOT NULL,
    window_end DATETIME NOT NULL,
    outcome_type VARCHAR(40) NOT NULL,
    min_sample_per_variant INT NOT NULL DEFAULT 10,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_jec_user_hypothesis (user_id, hypothesis_id, deleted),
    KEY idx_jec_window (hypothesis_id, window_start, window_end, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Comparable experiment cohort definition';

CREATE TABLE IF NOT EXISTS job_experiment_attribution (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    hypothesis_id BIGINT NOT NULL,
    cohort_id BIGINT NOT NULL,
    as_of DATETIME NOT NULL,
    method VARCHAR(64) NOT NULL,
    comparable_flag TINYINT NOT NULL DEFAULT 0,
    sample_count INT NOT NULL DEFAULT 0,
    common_strata_count INT NOT NULL DEFAULT 0,
    incomparable_reasons_json TEXT NULL,
    limitations_json TEXT NULL,
    result_json MEDIUMTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_jeat_cohort_asof (user_id, cohort_id, as_of, deleted),
    KEY idx_jeat_hypothesis (hypothesis_id, created_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Auditable stratified corrected attribution snapshot';
