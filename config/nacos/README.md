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

## Suggested Environment Variables

```powershell
[Environment]::SetEnvironmentVariable("MYSQL_PASSWORD", "your-local-password", "User")
[Environment]::SetEnvironmentVariable("CODECOACHAI_INTERNAL_SECRET", "change-me-in-private-nacos", "User")
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-xxxxxxxx", "User")
[Environment]::SetEnvironmentVariable("CODECOACHAI_AI_CRYPTO_SECRET_KEY", "change-me-at-least-16-chars", "User")
[Environment]::SetEnvironmentVariable("OSS_BUCKET", "your-oss-bucket", "User")
[Environment]::SetEnvironmentVariable("OSS_AK", "your-oss-access-key-id", "User")
[Environment]::SetEnvironmentVariable("OSS_SK", "your-oss-access-key-secret", "User")
[Environment]::SetEnvironmentVariable("OSS_STS_ROLE_ARN", "acs:ram::xxx:role/yyy", "User")
```

Restart the terminal or IDE after setting user-level environment variables.
