package com.codecoachai.common.security.config;

import com.codecoachai.common.security.internal.TrustedServiceNames;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

@Data
@ConfigurationProperties(prefix = "codecoachai.internal.auth")
public class InternalAuthProperties {

    private static final int MIN_SECRET_BYTES = 32;
    private static final Pattern PERMISSION_PATTERN =
            Pattern.compile("^([A-Z]+)\\s+(/inner(?:/.*)?)$");
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private boolean enabled = true;

    private String secret = "";

    private String legacySharedSecret = "";

    private boolean legacySharedSecretEnabled = true;

    private Set<String> legacySharedSecretCallers = new LinkedHashSet<>();

    private Map<String, CallerKeyRing> callerKeyRings = new LinkedHashMap<>();

    private long allowedClockSkewSeconds = 300;

    private long nonceTtlSeconds = 300;

    private long maxSignedBodyBytes = 1024L * 1024L;

    @PostConstruct
    public void validate() {
        if (enabled) {
            validateSecret("codecoachai.internal.auth.secret", secret);
        }
        validateCallerKeyRings();
        validateLegacyCallers();
        if (allowedClockSkewSeconds < 1 || allowedClockSkewSeconds > 900) {
            throw new IllegalStateException(
                    "codecoachai.internal.auth.allowed-clock-skew-seconds must be between 1 and 900");
        }
        if (nonceTtlSeconds < 1 || nonceTtlSeconds > 3600) {
            throw new IllegalStateException(
                    "codecoachai.internal.auth.nonce-ttl-seconds must be between 1 and 3600");
        }
        if (maxSignedBodyBytes < 1 || maxSignedBodyBytes > 16L * 1024L * 1024L) {
            throw new IllegalStateException(
                    "codecoachai.internal.auth.max-signed-body-bytes must be between 1 and 16777216");
        }
    }

    public List<String> verificationSecretsFor(String callerServiceName) {
        if (!enabled || !StringUtils.hasText(callerServiceName)) {
            return List.of();
        }
        LinkedHashSet<String> secrets = new LinkedHashSet<>();
        CallerKeyRing keyRing = callerKeyRings == null ? null : callerKeyRings.get(callerServiceName);
        if (keyRing != null) {
            secrets.addAll(normalizedSecrets(keyRing.getSecrets()));
        }
        if (legacySharedSecretEnabled
                && legacyCallerAllowed(callerServiceName)
                && StringUtils.hasText(effectiveLegacySharedSecret())) {
            secrets.add(effectiveLegacySharedSecret());
        }
        return List.copyOf(secrets);
    }

    public boolean isRequestAllowed(String callerServiceName, String method, String path) {
        if (!enabled
                || !StringUtils.hasText(callerServiceName)
                || !StringUtils.hasText(method)
                || !StringUtils.hasText(path)) {
            return false;
        }
        CallerKeyRing keyRing = callerKeyRings == null ? null : callerKeyRings.get(callerServiceName);
        if (keyRing == null || keyRing.getPermissions() == null) {
            return false;
        }
        String normalizedMethod = method.toUpperCase(Locale.ROOT);
        for (String permission : keyRing.getPermissions()) {
            Matcher matcher = permissionMatcher(permission);
            if (matcher != null
                    && normalizedMethod.equals(matcher.group(1))
                    && PATH_MATCHER.match(matcher.group(2), path)) {
                return true;
            }
        }
        return false;
    }

    public boolean mayForwardUserContext(String callerServiceName) {
        if (!enabled || !StringUtils.hasText(callerServiceName)) {
            return false;
        }
        CallerKeyRing keyRing = callerKeyRings == null ? null : callerKeyRings.get(callerServiceName);
        return keyRing != null && keyRing.isForwardUserContext();
    }

    private void validateCallerKeyRings() {
        if (callerKeyRings == null) {
            callerKeyRings = new LinkedHashMap<>();
            return;
        }
        Set<String> configuredSecrets = new HashSet<>();
        String effectiveLegacySecret = effectiveLegacySharedSecret();
        callerKeyRings.forEach((caller, keyRing) -> {
            if (!TrustedServiceNames.contains(caller)) {
                throw new IllegalStateException(
                        "codecoachai.internal.auth.caller-key-rings contains unknown caller: " + caller);
            }
            List<String> secrets = keyRing == null
                    ? List.of()
                    : normalizedSecrets(keyRing.getSecrets());
            if (secrets.isEmpty()) {
                throw new IllegalStateException(
                        "codecoachai.internal.auth.caller-key-rings." + caller
                                + ".secrets must contain at least one secret");
            }
            for (String configuredSecret : secrets) {
                String property = "codecoachai.internal.auth.caller-key-rings."
                        + caller + ".secrets";
                validateSecret(property, configuredSecret);
                if (configuredSecret.equals(secret)) {
                    throw new IllegalStateException(
                            property + " must not reuse this service's outbound secret");
                }
                if (StringUtils.hasText(effectiveLegacySecret)
                        && configuredSecret.equals(effectiveLegacySecret)) {
                    throw new IllegalStateException(
                            property + " must not contain the legacy shared secret");
                }
                if (!configuredSecrets.add(configuredSecret)) {
                    throw new IllegalStateException(
                            "codecoachai.internal.auth caller secrets must be unique across callers");
                }
            }
            validatePermissions(caller, keyRing);
        });
    }

    private void validateLegacyCallers() {
        if (legacySharedSecretCallers == null) {
            legacySharedSecretCallers = new LinkedHashSet<>();
            return;
        }
        for (String caller : legacySharedSecretCallers) {
            if (!TrustedServiceNames.contains(caller)) {
                throw new IllegalStateException(
                        "codecoachai.internal.auth.legacy-shared-secret-callers contains unknown caller: "
                                + caller);
            }
        }
        if (legacySharedSecretEnabled && !legacySharedSecretCallers.isEmpty()) {
            validateSecret(
                    "codecoachai.internal.auth.legacy-shared-secret",
                    effectiveLegacySharedSecret());
        }
    }

    private boolean legacyCallerAllowed(String callerServiceName) {
        return legacySharedSecretCallers != null
                && legacySharedSecretCallers.contains(callerServiceName);
    }

    private String effectiveLegacySharedSecret() {
        return StringUtils.hasText(legacySharedSecret) ? legacySharedSecret : secret;
    }

    private List<String> normalizedSecrets(List<String> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return List.of();
        }
        return secrets.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private void validatePermissions(String caller, CallerKeyRing keyRing) {
        List<String> permissions = keyRing == null ? null : keyRing.getPermissions();
        if ((permissions == null || permissions.isEmpty())
                && (keyRing == null || !keyRing.isForwardUserContext())) {
            throw new IllegalStateException(
                    "codecoachai.internal.auth.caller-key-rings." + caller
                            + " must allow an internal request or user-context forwarding");
        }
        if (permissions == null) {
            return;
        }
        for (String permission : permissions) {
            Matcher matcher = permissionMatcher(permission);
            if (matcher == null) {
                throw new IllegalStateException(
                        "Invalid internal permission for " + caller + ": " + permission);
            }
            try {
                HttpMethod.valueOf(matcher.group(1));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Invalid HTTP method in internal permission for " + caller + ": "
                                + permission,
                        exception);
            }
        }
    }

    private Matcher permissionMatcher(String permission) {
        if (!StringUtils.hasText(permission) || !permission.equals(permission.trim())) {
            return null;
        }
        Matcher matcher = PERMISSION_PATTERN.matcher(permission);
        return matcher.matches() ? matcher : null;
    }

    private void validateSecret(String property, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(property + " must be configured");
        }
        if (!value.equals(value.trim()) || value.contains("${")) {
            throw new IllegalStateException(property + " contains whitespace or an unresolved placeholder");
        }
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(property + " must contain at least 32 UTF-8 bytes");
        }
    }

    @Data
    public static class CallerKeyRing {

        private List<String> secrets = new ArrayList<>();

        private List<String> permissions = new ArrayList<>();

        private boolean forwardUserContext;
    }
}
