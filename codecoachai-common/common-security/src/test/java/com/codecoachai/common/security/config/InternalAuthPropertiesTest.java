package com.codecoachai.common.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.security.config.InternalAuthProperties.CallerKeyRing;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class InternalAuthPropertiesTest {

    private static final String OUTBOUND_SECRET =
            "receiver-outbound-secret-0123456789abcdef";
    private static final String LEGACY_SECRET =
            "legacy-shared-secret-0123456789abcdefghi";
    private static final String CORE_CURRENT =
            "core-current-secret-0123456789abcdefghij";
    private static final String CORE_PREVIOUS =
            "core-previous-secret-0123456789abcdefgh";
    private static final String AI_CURRENT =
            "ai-current-secret-0123456789abcdefghijkl";

    @Test
    void pinnedCallerUsesOnlyItsConfiguredKeyRing() {
        InternalAuthProperties properties = properties();
        properties.setCallerKeyRings(Map.of(
                "codecoachai-core", keyRing(CORE_CURRENT, CORE_PREVIOUS),
                "codecoachai-ai", keyRing(AI_CURRENT)));

        assertEquals(
                List.of(CORE_CURRENT, CORE_PREVIOUS),
                properties.verificationSecretsFor("codecoachai-core"));
        assertEquals(
                List.of(AI_CURRENT),
                properties.verificationSecretsFor("codecoachai-ai"));
    }

    @Test
    void mappedAndUnmappedCallersCanUseExplicitLegacyCompatibilityWindow() {
        InternalAuthProperties properties = properties();
        properties.setLegacySharedSecret(LEGACY_SECRET);
        properties.setLegacySharedSecretEnabled(true);
        properties.setLegacySharedSecretCallers(
                Set.of("codecoachai-core", "codecoachai-search", "codecoachai-ai"));
        properties.setCallerKeyRings(Map.of("codecoachai-core", keyRing(CORE_CURRENT)));

        assertEquals(
                List.of(LEGACY_SECRET),
                properties.verificationSecretsFor("codecoachai-search"));
        assertEquals(
                List.of(LEGACY_SECRET),
                properties.verificationSecretsFor("codecoachai-ai"));
        assertEquals(
                List.of(CORE_CURRENT, LEGACY_SECRET),
                properties.verificationSecretsFor("codecoachai-core"));
    }

    @Test
    void emptyLegacyCallerSetRejectsEveryUnpinnedCaller() {
        InternalAuthProperties properties = properties();
        properties.setLegacySharedSecret(LEGACY_SECRET);
        properties.setLegacySharedSecretEnabled(true);
        properties.setLegacySharedSecretCallers(Set.of());

        assertEquals(List.of(), properties.verificationSecretsFor("codecoachai-gateway"));
        assertEquals(List.of(), properties.verificationSecretsFor("codecoachai-core"));
    }

    @Test
    void disablingLegacyFallbackRejectsUnmappedCaller() {
        InternalAuthProperties properties = properties();
        properties.setLegacySharedSecretEnabled(false);

        assertEquals(List.of(), properties.verificationSecretsFor("codecoachai-core"));
    }

    @Test
    void unknownCallerInKeyRingFailsConfigurationValidation() {
        InternalAuthProperties properties = properties();
        properties.setCallerKeyRings(Map.of("forged-service", keyRing(CORE_CURRENT)));

        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void requestPermissionsEnforceMethodAndNormalizedInnerPath() {
        InternalAuthProperties properties = properties();
        CallerKeyRing core = keyRing(CORE_CURRENT);
        core.setPermissions(List.of(
                "GET /inner/resume/search-documents",
                "POST /inner/agent/**"));
        properties.setCallerKeyRings(Map.of("codecoachai-core", core));

        assertTrue(properties.isRequestAllowed(
                "codecoachai-core", "GET", "/inner/resume/search-documents"));
        assertTrue(properties.isRequestAllowed(
                "codecoachai-core", "POST", "/inner/agent/job-coach"));
        assertFalse(properties.isRequestAllowed(
                "codecoachai-core", "POST", "/inner/resume/search-documents"));
        assertFalse(properties.isRequestAllowed(
                "codecoachai-core", "GET", "/inner/admin/users"));
    }

    @Test
    void weakDuplicateAndLegacyReusedCallerKeysFailValidation() {
        InternalAuthProperties weak = properties();
        weak.setCallerKeyRings(Map.of("codecoachai-core", keyRing("short-secret")));
        assertThrows(IllegalStateException.class, weak::validate);

        InternalAuthProperties duplicate = properties();
        duplicate.setCallerKeyRings(Map.of(
                "codecoachai-core", keyRing(CORE_CURRENT),
                "codecoachai-ai", keyRing(CORE_CURRENT)));
        assertThrows(IllegalStateException.class, duplicate::validate);

        InternalAuthProperties legacyReuse = properties();
        legacyReuse.setLegacySharedSecret(LEGACY_SECRET);
        legacyReuse.setLegacySharedSecretEnabled(true);
        legacyReuse.setLegacySharedSecretCallers(Set.of("codecoachai-core"));
        legacyReuse.setCallerKeyRings(Map.of("codecoachai-core", keyRing(LEGACY_SECRET)));
        assertThrows(IllegalStateException.class, legacyReuse::validate);
    }

    @Test
    void missingPermissionAndUnresolvedPlaceholderFailValidation() {
        InternalAuthProperties missingPermission = properties();
        CallerKeyRing ring = keyRing(CORE_CURRENT);
        ring.setPermissions(List.of());
        missingPermission.setCallerKeyRings(Map.of("codecoachai-core", ring));
        assertThrows(IllegalStateException.class, missingPermission::validate);

        InternalAuthProperties unresolved = properties();
        unresolved.setSecret("${CODECOACHAI_INTERNAL_OUTBOUND_SECRET}");
        assertThrows(IllegalStateException.class, unresolved::validate);
    }

    @Test
    void springBinderPreservesHyphenatedCallerNameAsKeyRingMapKey() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("codecoachai.internal.auth.secret", OUTBOUND_SECRET);
        values.put("codecoachai.internal.auth.legacy-shared-secret-enabled", "false");
        values.put(
                "codecoachai.internal.auth.caller-key-rings.codecoachai-core.secrets[0]",
                CORE_CURRENT);
        values.put(
                "codecoachai.internal.auth.caller-key-rings.codecoachai-core.secrets[1]",
                CORE_PREVIOUS);
        values.put(
                "codecoachai.internal.auth.caller-key-rings.codecoachai-core.permissions[0]",
                "POST /inner/job/**");
        values.put(
                "codecoachai.internal.auth.caller-key-rings.codecoachai-core.forward-user-context",
                "true");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("internal-auth-test", values));

        InternalAuthProperties properties = Binder.get(environment)
                .bind("codecoachai.internal.auth", Bindable.of(InternalAuthProperties.class))
                .orElseThrow(() -> new IllegalStateException("Internal auth properties did not bind"));
        properties.validate();

        assertEquals(
                List.of(CORE_CURRENT, CORE_PREVIOUS),
                properties.verificationSecretsFor("codecoachai-core"));
        assertTrue(properties.mayForwardUserContext("codecoachai-core"));
    }

    private InternalAuthProperties properties() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setEnabled(true);
        properties.setSecret(OUTBOUND_SECRET);
        properties.setLegacySharedSecretEnabled(false);
        return properties;
    }

    private static CallerKeyRing keyRing(String... secrets) {
        CallerKeyRing keyRing = new CallerKeyRing();
        keyRing.setSecrets(List.of(secrets));
        keyRing.setPermissions(List.of("GET /inner/test"));
        return keyRing;
    }
}
