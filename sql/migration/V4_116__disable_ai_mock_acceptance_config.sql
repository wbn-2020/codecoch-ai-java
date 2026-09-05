-- Retire the legacy operator-facing record. AI runtime Mock mode is controlled
-- only by codecoachai.ai.mock-enabled from Nacos/Spring configuration; this
-- database key must never imply it can toggle runtime behavior.
UPDATE system_config
SET config_value = 'false',
    value_type = 'BOOLEAN',
    description = 'DEPRECATED: this legacy database record does not control runtime Mock mode; configure codecoachai.ai.mock-enabled in Nacos or Spring runtime configuration',
    status = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE config_key = 'ai.mock.enabled';

INSERT INTO system_config (
    config_key,
    config_value,
    value_type,
    description,
    status,
    created_at,
    updated_at
)
SELECT
    'ai.mock.enabled',
    'false',
    'BOOLEAN',
    'DEPRECATED: this legacy database record does not control runtime Mock mode; configure codecoachai.ai.mock-enabled in Nacos or Spring runtime configuration',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM system_config
    WHERE config_key = 'ai.mock.enabled'
);
