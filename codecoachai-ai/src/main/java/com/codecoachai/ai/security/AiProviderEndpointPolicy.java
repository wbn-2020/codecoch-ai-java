package com.codecoachai.ai.security;

import com.codecoachai.ai.config.AiRouterProperties;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiProviderEndpointPolicy {

    private static final int HTTPS_PORT = 443;
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final AiRouterProperties properties;
    private final HostResolver hostResolver;

    public AiProviderEndpointPolicy(AiRouterProperties properties) {
        this(properties, InetAddress::getAllByName);
    }

    AiProviderEndpointPolicy(AiRouterProperties properties, HostResolver hostResolver) {
        this.properties = properties;
        this.hostResolver = hostResolver;
    }

    public String validateAndNormalizeBaseUrl(String baseUrl) {
        URI endpoint = parseAndValidate(baseUrl);
        resolveAllowedHost(endpoint.getHost());
        return endpoint.toASCIIString();
    }

    public URI chatEndpoint(String baseUrl) {
        return appendEndpoint(parseAndValidate(baseUrl), CHAT_COMPLETIONS_PATH);
    }

    public URI embeddingEndpoint(String baseUrl) {
        URI base = parseAndValidate(baseUrl);
        String path = normalizeBasePath(base.getRawPath());
        if (path.endsWith(CHAT_COMPLETIONS_PATH)) {
            path = path.substring(0, path.length() - CHAT_COMPLETIONS_PATH.length());
            base = rebuild(base, path);
        }
        return appendEndpoint(base, EMBEDDINGS_PATH);
    }

    public URI validateRequestUri(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("AI Provider request URI cannot be empty");
        }
        URI validated = parseAndValidate(uri.toASCIIString());
        resolveAllowedHost(validated.getHost());
        return validated;
    }

    public InetAddress[] resolveAllowedHost(String host) {
        String normalizedHost = normalizeHost(host);
        requireAllowedHost(normalizedHost);
        try {
            InetAddress[] addresses = hostResolver.resolve(normalizedHost);
            if (addresses == null || addresses.length == 0) {
                throw new IllegalArgumentException("AI Provider host cannot be resolved");
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new IllegalArgumentException("AI Provider host resolves to a non-public address");
                }
            }
            return Arrays.copyOf(addresses, addresses.length);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("AI Provider host cannot be resolved", ex);
        }
    }

    private URI parseAndValidate(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalArgumentException("AI Provider base URL cannot be empty");
        }
        URI parsed;
        try {
            parsed = new URI(rawUrl.trim());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("AI Provider base URL is invalid", ex);
        }
        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
            throw new IllegalArgumentException("AI Provider base URL must use HTTPS");
        }
        if (parsed.getRawUserInfo() != null || parsed.getRawQuery() != null || parsed.getRawFragment() != null) {
            throw new IllegalArgumentException("AI Provider base URL cannot contain user info, query, or fragment");
        }
        String host = normalizeHost(parsed.getHost());
        requireAllowedHost(host);
        int effectivePort = parsed.getPort() < 0 ? HTTPS_PORT : parsed.getPort();
        Set<Integer> allowedPorts = properties.getProviderSecurity().getAllowedPorts();
        if (allowedPorts == null || !allowedPorts.contains(effectivePort)) {
            throw new IllegalArgumentException("AI Provider HTTPS port is not allowed");
        }
        return rebuild(parsed, normalizeBasePath(parsed.getRawPath()), host);
    }

    private URI appendEndpoint(URI base, String endpointPath) {
        resolveAllowedHost(base.getHost());
        String path = normalizeBasePath(base.getRawPath());
        if (!path.endsWith(endpointPath)) {
            path += endpointPath;
        }
        return rebuild(base, path);
    }

    private URI rebuild(URI source, String rawPath) {
        return rebuild(source, rawPath, source.getHost());
    }

    private URI rebuild(URI source, String rawPath, String host) {
        try {
            return new URI("https", null, host, source.getPort(), rawPath, null, null);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("AI Provider base URL is invalid", ex);
        }
    }

    private String normalizeBasePath(String rawPath) {
        if (!StringUtils.hasText(rawPath) || "/".equals(rawPath)) {
            return "";
        }
        String path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void requireAllowedHost(String host) {
        Set<String> allowedHosts = properties.getProviderSecurity().getAllowedHosts();
        if (!StringUtils.hasText(host) || allowedHosts == null || allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("AI Provider host allowlist is empty or invalid");
        }
        boolean allowed = allowedHosts.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeAllowlistEntry)
                .anyMatch(entry -> entry.equals(host));
        if (!allowed) {
            throw new IllegalArgumentException("AI Provider host is not allowlisted");
        }
    }

    private String normalizeAllowlistEntry(String entry) {
        String value = entry.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("*.")) {
            throw new IllegalArgumentException("AI Provider host allowlist must use exact host names");
        }
        return normalizeHost(value);
    }

    private String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        String value = host.trim();
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            return IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("AI Provider host is invalid", ex);
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0
                    && first != 10
                    && first != 127
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && second == 0 && third == 0)
                    && !(first == 192 && second == 0 && third == 2)
                    && !(first == 192 && second == 168)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113)
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            if (isIpv4MappedAddress(bytes)) {
                return isPublicIpv4(Arrays.copyOfRange(bytes, 12, 16));
            }
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            boolean uniqueLocal = (first & 0xFE) == 0xFC;
            boolean linkLocal = first == 0xFE && (second & 0xC0) == 0x80;
            boolean documentation = first == 0x20 && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0D && Byte.toUnsignedInt(bytes[3]) == 0xB8;
            boolean nat64WellKnown = first == 0x00 && second == 0x64
                    && Byte.toUnsignedInt(bytes[2]) == 0xFF && Byte.toUnsignedInt(bytes[3]) == 0x9B;
            return !uniqueLocal && !linkLocal && !documentation && !nat64WellKnown;
        }
        return false;
    }

    private boolean isIpv4MappedAddress(byte[] bytes) {
        if (bytes == null || bytes.length != 16
                || Byte.toUnsignedInt(bytes[10]) != 0xFF
                || Byte.toUnsignedInt(bytes[11]) != 0xFF) {
            return false;
        }
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isPublicIpv4(byte[] bytes) {
        if (bytes == null || bytes.length != 4) {
            return false;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        return first != 0
                && first != 10
                && first != 127
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 0 && third == 0)
                && !(first == 192 && second == 0 && third == 2)
                && !(first == 192 && second == 168)
                && !(first == 198 && (second == 18 || second == 19))
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113)
                && first < 224;
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
