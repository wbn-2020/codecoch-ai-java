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
            - ${CODECOACHAI_GATEWAY_TO_CORE_SIGNING_SECRET}
          permissions:
            - GET /inner/auth/token-info
          forward-user-context: true
        codecoachai-ai:
          secrets:
            - ${CODECOACHAI_CALLER_AI_SIGNING_SECRET}
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

For the consolidated topology, the permitted call edges are:

- Gateway -> Core, AI, and Search uses the three directional
  `CODECOACHAI_GATEWAY_TO_*_SIGNING_SECRET` values.
- Core -> AI uses `CODECOACHAI_CALLER_CORE_SIGNING_SECRET`.
- Core -> Search uses the same Core outbound key only where the reviewed Search
  ACL explicitly permits that edge.
- The old standalone Task, User, Resume, Interview, Question, File, and System
  caller identities are not deployed. Those modules are libraries inside Core or
  AI and must not be registered as independent callers.

Do not put real keys in Git. Resolve them from private Nacos configuration,
environment variables, or the deployment secret manager.

## Multipart Integrity Boundary

The V2 signature binds normal request bodies to
`X-Internal-Body-Sha256`. Selected multipart upload endpoints use the explicit
`STREAMING-UNSIGNED-PAYLOAD` marker so Gateway and Feign can preserve streaming
without consuming the Servlet multipart stream before `getParts()` runs. For
those requests, HMAC authenticates the caller, method, path, query, timestamp,
nonce, and forwarded user context, but it does not authenticate file bytes or
multipart form fields.

Treat this as a transport security dependency, not as end-to-end body signing:

- Keep the exact-path multipart allowlist minimal; never use wildcard paths.
- Bind application ports to loopback or an isolated container network and do
  not admit untrusted workloads to that network.
- Require authenticated TLS for Gateway-to-Core/AI traffic before any
  cross-host, shared-network, or production deployment. Use mTLS when certificate
  automation is available.
- Do not replace the marker by reading multipart data in
  `TrustedRequestVerifier`. Its current repeatable-body wrapper does not
  implement Servlet `Part` replay and would break
  `StandardServletMultipartResolver`.

Implementing HMAC protection for raw multipart bytes is a separate project. It
requires bounded Gateway disk spooling plus a multipart-aware downstream parser,
disk quotas, cancellation cleanup, and embedded-container contract tests.

For Docker Compose, store each caller's source key as
the matching directional variable. Map that value to
`CODECOACHAI_INTERNAL_OUTBOUND_SECRET` in the caller container and reference
the same variable in each receiver's Nacos ring. Do not introduce a second
`*_KEY_CURRENT` alias.
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
- Non-multipart request body tampering is rejected by the receiver.
- Every unsigned multipart route is reviewed as an exact path, and its
  Gateway-to-service transport remains inside the approved isolated network or
  uses authenticated TLS.
