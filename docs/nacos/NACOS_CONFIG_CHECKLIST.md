# Nacos Config Checklist

## Current Four-Service Topology

The refactored runtime has four deployable services:

| Service | Nacos Data ID | Default port | Runtime role |
|---|---|---:|---|
| Gateway | `codecoachai-gateway-dev.yml` | 8080 | Public API gateway and routing |
| Core | `codecoachai-core-dev.yml` | 9200 | Consolidated business API and optional MQ consumers |
| AI | `codecoachai-ai-dev.yml` | 9206 | Model calls and AI workflows |
| Search | `codecoachai-search-dev.yml` | 8091 | Elasticsearch-backed search |

The shared imports required by the four services are:

- `codecoachai-common-dev.yml`
- `codecoachai-redis-dev.yml`
- the service-specific Data ID for Gateway, Core, AI, or Search

The legacy `auth`, `user`, `resume`, `interview`, `question`, `file`, `system`,
and `task` Data IDs may remain available for rollback, but they must not be
treated as independently deployable services in the refactored topology.

## Configuration Checks

| Check | Static status | Test-environment status |
|---|---|---|
| Four service Data IDs exist | PASS | Pending |
| Common and Redis Data IDs exist | PASS | Pending |
| Core uses port `9200` and Gateway routes business traffic to Core | PASS | Pending |
| Directional Gateway and service outbound HMAC keys are configured | PASS | Pending |
| Legacy shared HMAC key is disabled in the four-service topology | PASS | Pending |
| Core MQ consumers are explicitly disabled during cutover | PASS | Pending |
| One dedicated non-`public` namespace is explicitly configured for Config, Discovery, and the initializer | PASS | Pending |
| Nacos token authentication and exact-tenant drift gate succeed | PASS | Pending |
| Core OSS and Core/AI Qdrant environment variables are non-empty and reachable | PASS | Pending |
| Nacos backup and configuration diff are recorded | N/A | Required before release |
| Four services load configuration and register healthy instances | N/A | Required acceptance test |
| Gateway critical APIs and `/inner/**` isolation are verified | N/A | Required acceptance test |

## Required Secret Mapping

Use distinct values for:

- `CODECOACHAI_GATEWAY_TO_CORE_SIGNING_SECRET`
- `CODECOACHAI_GATEWAY_TO_AI_SIGNING_SECRET`
- `CODECOACHAI_GATEWAY_TO_SEARCH_SIGNING_SECRET`
- `CODECOACHAI_CALLER_CORE_SIGNING_SECRET`
- `CODECOACHAI_CALLER_AI_SIGNING_SECRET`
- `CODECOACHAI_CALLER_SEARCH_SIGNING_SECRET`

Do not publish real values in this repository or in shared documentation.

## Verification Boundary

The workstation is intentionally not used to start Spring services, Docker,
Nacos, MySQL, Redis, RocketMQ, Elasticsearch, or AI workflows for this
refactor. Maven tests and release/container contract tests are static/code
verification only. Service startup, registration, MQ handover, API
integration, and business acceptance must be completed in the approved test
environment after publishing the release.
