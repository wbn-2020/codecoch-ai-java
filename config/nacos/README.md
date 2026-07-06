# Nacos Legacy Templates

This directory keeps older/manual Nacos templates for reference. The preferred import source is `docs/nacos/`, and both `scripts/nacos/import-nacos-config.ps1` and `scripts/nacos/import-nacos-config.sh` read from `docs/nacos/` by default.

When uploading configs manually, prefer `docs/nacos/` first and keep any `config/nacos/` edits aligned with it.

## Required Data IDs

| Data ID | File | Required |
|---|---|---|
| `codecoachai-common-dev.yml` | `codecoachai-common-dev.yml` | Yes |
| `codecoachai-gateway-dev.yml` | `codecoachai-gateway-dev.yml` | Yes |
| `codecoachai-file-dev.yml` | `codecoachai-file-dev.yml` | Yes |
| `codecoachai-ai-dev.yml` | `codecoachai-ai-dev.yml` | Yes for AI service |
| `codecoachai-task-dev.yml` | `codecoachai-task-dev.yml` | Yes when task service starts |
| `codecoachai-search-dev.yml` | `codecoachai-search-dev.yml` | Yes when search service starts |

## Security And Provider Defaults

- `codecoachai.gateway.cors.allowed-origin-patterns` and gateway `globalcors` must list trusted frontend origins explicitly. Do not use `*` together with `allowCredentials=true`.
- `codecoachai.internal.auth.secret` must come from `CODECOACHAI_INTERNAL_SECRET` or private Nacos config before services exposing `/inner/**` start.
- `codecoachai.file.storage.provider` is `ALIYUN_OSS` for the current dev acceptance path.
- `codecoachai.oss.enabled` is `true`; set `OSS_BUCKET`, `OSS_AK`, `OSS_SK`, and `OSS_STS_ROLE_ARN` in local env/private Nacos before starting file service.
- `codecoachai.ai.mock-enabled` is `false`; set `DEEPSEEK_API_KEY` before starting AI service.
- `codecoachai.ai.crypto.secret-key` is required before saving AI model API keys from the admin UI.

## Suggested Process-Level Environment Variables

Use process-level variables in the terminal that starts services. Do not persist placeholder secrets with user-level or machine-level environment variables, and do not commit real secrets into Nacos templates.

```text
$env:MYSQL_PASSWORD = "<local database password>"
$env:CODECOACHAI_INTERNAL_SECRET = "<strong random 32+ byte value>"
$env:DEEPSEEK_API_KEY = "<runtime DeepSeek API key>"
$env:CODECOACHAI_AI_CRYPTO_SECRET_KEY = "<strong random 32+ byte value>"
$env:OSS_BUCKET = "<runtime OSS bucket>"
$env:OSS_AK = "<runtime OSS access key id>"
$env:OSS_SK = "<runtime OSS access key secret>"
$env:OSS_STS_ROLE_ARN = "<runtime OSS STS role ARN>"
```

Generate strong random values outside the repository, inject them only for the current process or via a private secret manager, and rotate them if they were ever copied into a shared document.
