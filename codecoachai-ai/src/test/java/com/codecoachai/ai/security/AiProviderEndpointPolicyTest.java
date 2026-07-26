package com.codecoachai.ai.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codecoachai.ai.config.AiRouterProperties;
import java.net.InetAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiProviderEndpointPolicyTest {

    @Test
    void acceptsAllowlistedHttpsHostAndBuildsProviderEndpoints() throws Exception {
        AiProviderEndpointPolicy policy = policy(
                Set.of("api.example.com"),
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")});

        assertEquals(
                "https://api.example.com/v1",
                policy.validateAndNormalizeBaseUrl("https://API.EXAMPLE.COM/v1/"));
        assertEquals(
                URI.create("https://api.example.com/v1/chat/completions"),
                policy.chatEndpoint("https://api.example.com/v1"));
        assertEquals(
                URI.create("https://api.example.com/v1/embeddings"),
                policy.embeddingEndpoint("https://api.example.com/v1/chat/completions"));
    }

    @Test
    void rejectsHttpUserInfoAndNonAllowlistedHosts() {
        AiProviderEndpointPolicy policy = policy(Set.of("api.example.com"), publicResolver());

        assertThrows(IllegalArgumentException.class,
                () -> policy.validateAndNormalizeBaseUrl("http://api.example.com/v1"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateAndNormalizeBaseUrl("https://user@api.example.com/v1"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateAndNormalizeBaseUrl("https://api.example.com.evil.test/v1"));
    }

    @Test
    void rejectsWildcardAllowlistEntries() {
        AiProviderEndpointPolicy policy = policy(Set.of("*.example.com"), publicResolver());

        assertThrows(IllegalArgumentException.class,
                () -> policy.validateAndNormalizeBaseUrl("https://api.example.com/v1"));
    }

    @Test
    void rejectsPrivateMetadataAndMixedDnsAnswers() throws Exception {
        AiProviderEndpointPolicy privatePolicy = policy(
                Set.of("api.example.com"),
                host -> new InetAddress[]{InetAddress.getByName("169.254.169.254")});
        AiProviderEndpointPolicy mixedPolicy = policy(
                Set.of("api.example.com"),
                host -> new InetAddress[]{
                        InetAddress.getByName("93.184.216.34"),
                        InetAddress.getByName("10.0.0.7")
                });

        assertThrows(IllegalArgumentException.class,
                () -> privatePolicy.validateAndNormalizeBaseUrl("https://api.example.com/v1"));
        assertThrows(IllegalArgumentException.class,
                () -> mixedPolicy.validateAndNormalizeBaseUrl("https://api.example.com/v1"));
    }

    @Test
    void connectionTimeResolutionRejectsDnsRebinding() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AiProviderEndpointPolicy policy = policy(
                Set.of("api.example.com"),
                host -> calls.incrementAndGet() == 1
                        ? new InetAddress[]{InetAddress.getByName("93.184.216.34")}
                        : new InetAddress[]{InetAddress.getByName("127.0.0.1")});

        policy.validateAndNormalizeBaseUrl("https://api.example.com/v1");

        assertThrows(IllegalArgumentException.class, () -> policy.resolveAllowedHost("api.example.com"));
    }

    @Test
    void requestUriIsRevalidatedImmediatelyBeforeSending() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AiProviderEndpointPolicy policy = policy(
                Set.of("api.example.com"),
                host -> calls.incrementAndGet() == 1
                        ? new InetAddress[]{InetAddress.getByName("93.184.216.34")}
                        : new InetAddress[]{InetAddress.getByName("127.0.0.1")});

        URI endpoint = policy.chatEndpoint("https://api.example.com/v1");

        assertThrows(IllegalArgumentException.class, () -> policy.validateRequestUri(endpoint));
    }

    @Test
    void rejectsIpv4MappedLoopbackAndNat64Addresses() throws Exception {
        AiProviderEndpointPolicy mappedLoopback = policy(
                Set.of("api.example.com"),
                host -> new InetAddress[]{InetAddress.getByName("::ffff:127.0.0.1")});
        AiProviderEndpointPolicy nat64 = policy(
                Set.of("api.example.com"),
                host -> new InetAddress[]{InetAddress.getByName("64:ff9b::7f00:1")});

        assertThrows(IllegalArgumentException.class,
                () -> mappedLoopback.validateAndNormalizeBaseUrl("https://api.example.com/v1"));
        assertThrows(IllegalArgumentException.class,
                () -> nat64.validateAndNormalizeBaseUrl("https://api.example.com/v1"));
    }

    private static AiProviderEndpointPolicy policy(
            Set<String> allowedHosts, AiProviderEndpointPolicy.HostResolver resolver) {
        AiRouterProperties properties = new AiRouterProperties();
        properties.getProviderSecurity().setAllowedHosts(allowedHosts);
        properties.getProviderSecurity().setAllowedPorts(Set.of(443));
        return new AiProviderEndpointPolicy(properties, resolver);
    }

    private static AiProviderEndpointPolicy.HostResolver publicResolver() {
        return host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")};
    }
}
