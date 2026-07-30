# Internal Auth Caller Key Ring Migration

## Problem

The V2 signature payload already includes `serviceName`, but all services currently
share `codecoachai.internal.auth.secret`. Any service that knows the shared secret
can therefore sign a request while claiming another trusted service name.

The receiver now supports a key ring per caller. During an explicit compatibility
window, a mapped caller may be verified with both its key ring and the legacy
shared secret. Outside that window, the legacy secret is not accepted.

## Configuration Contract

```yaml
codecoachai:
  internal:
    auth:
      enabled: true

      # The current service uses this key for outbound Feign/internal signatures.
      # Give every service a different value.
      secret: ${CODECOACHAI_INTERNAL_OUTBOUND_SECRET}

      # Compatibility switch for callers not yet present in caller-key-rings.
      legacy-shared-secret: ${CODECOACHAI_INTERNAL_LEGACY_SHARED_SECRET}
      legacy-shared-secret-enabled: false
      legacy-shared-secret-callers: []

      # Configure only callers that are allowed to call this receiver.
      caller-key-rings:
        codecoachai-gateway:
          secrets:
            - ${CODECOACHAI_CALLER_GATEWAY_KEY_CURRENT}
            - ${CODECOACHAI_CALLER_GATEWAY_KEY_PREVIOUS:}
          permissions:
            - GET /inner/auth/token-info
          forward-user-context: true
        codecoachai-task:
          secrets:
            - ${CODECOACHAI_CALLER_TASK_KEY_CURRENT}
          permissions:
            - POST /inner/questions/reviews/save-drafts
          forward-user-context: true
        codecoachai-ai:
          secrets:
            - ${CODECOACHAI_CALLER_AI_KEY_CURRENT}
          permissions:
            - GET /inner/practice-records/users/{userId}/{recordId}/agent-evidence
          forward-user-context: true

      allowed-clock-skew-seconds: 300
      nonce-ttl-seconds: 300
      max-signed-body-bytes: 1048576
```

`secret` remains the outbound key because `common-feign`, gateway signing, and
other existing signers already read that property. `legacy-shared-secret` keeps
the previous shared inbound key available during a rolling migration; when it is
omitted, the code falls back to `secret` for old configuration compatibility.
Each receiver maps a caller name to the same key configured as that caller's
outbound `secret`. Every secret must contain at least 32 bytes, be unique across
callers, and differ from the legacy shared secret. Each caller also has explicit
HTTP method/path permissions and a separate user-context forwarding permission.

Do not put real keys in Git. Resolve them from private Nacos configuration,
environment variables, or the deployment secret manager.

For Docker Compose, store each caller's source key as
`CODECOACHAI_CALLER_<SERVICE>_SIGNING_SECRET`. Map that value to
`CODECOACHAI_INTERNAL_OUTBOUND_SECRET` in the caller container and to
`CODECOACHAI_CALLER_<SERVICE>_KEY_CURRENT` in each receiver container.
Do not use per-service names such as `CODECOACHAI_INTERNAL_AUTH_SECRET`.
Spring relaxed environment binding interprets that exact name as
`codecoachai.internal.auth.secret`, so an `env_file` can silently override the
Nacos property in every container.

## Rollout

1. Publish caller rings, exact permissions, and the explicit legacy caller allowlist.
   Keep every sender on the old shared outbound key.
2. Upgrade all receivers while the allowlist accepts the old sender key.
3. Stop all application containers, switch every sender to its unique outbound key,
   and start the applications together. Do not leave old and new senders mixed.
4. Verify every call edge, then set `legacy-shared-secret-enabled: false` and clear
   the allowlist.
5. Remove previous rotation keys only after the maximum clock-skew and nonce TTL
   windows have elapsed and all caller instances use the current key.

## Acceptance Checks

- A request signed with caller A's key and header `X-Service-Name: caller-a` passes.
- The same key with header `X-Service-Name: caller-b` is rejected with HTTP 403.
- A mapped caller can authenticate with the legacy key only while it is explicitly
  allowlisted and legacy compatibility is enabled.
- An unmapped caller is rejected after legacy compatibility is disabled.
- A valid caller key is rejected from unlisted method/path combinations.
- Forwarded user context is accepted only when `forward-user-context` is enabled.
