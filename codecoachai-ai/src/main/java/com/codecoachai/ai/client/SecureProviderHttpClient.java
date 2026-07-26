package com.codecoachai.ai.client;

import com.codecoachai.ai.security.AiProviderEndpointPolicy;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

@Component
public class SecureProviderHttpClient {

    private final AiProviderEndpointPolicy endpointPolicy;
    private final CloseableHttpClient httpClient;

    public SecureProviderHttpClient(AiProviderEndpointPolicy endpointPolicy) {
        this.endpointPolicy = endpointPolicy;
        DnsResolver dnsResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                try {
                    return endpointPolicy.resolveAllowedHost(host);
                } catch (IllegalArgumentException ex) {
                    UnknownHostException wrapped = new UnknownHostException(ex.getMessage());
                    wrapped.initCause(ex);
                    throw wrapped;
                }
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                try {
                    endpointPolicy.resolveAllowedHost(host);
                    return host;
                } catch (IllegalArgumentException ex) {
                    UnknownHostException wrapped = new UnknownHostException(ex.getMessage());
                    wrapped.initCause(ex);
                    throw wrapped;
                }
            }
        };
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .setMaxConnTotal(50)
                        .setMaxConnPerRoute(20)
                        .build();
        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .build();
    }

    public String postJson(URI uri, String apiKey, String jsonBody, Duration timeout) throws IOException {
        HttpPost request = request(uri, apiKey, jsonBody, timeout);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            requireSuccess(response);
            if (response.getEntity() == null) {
                return "";
            }
            try {
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            } catch (ParseException ex) {
                throw new IOException("AI Provider returned an invalid response entity", ex);
            }
        }
    }

    public void postJsonLines(URI uri, String apiKey, String jsonBody, Duration timeout,
            Consumer<String> lineConsumer) throws IOException {
        HttpPost request = request(uri, apiKey, jsonBody, timeout);
        request.setHeader("Accept", "text/event-stream");
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            requireSuccess(response);
            if (response.getEntity() == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineConsumer.accept(line);
                }
            }
        }
    }

    @PreDestroy
    public void close() throws IOException {
        httpClient.close();
    }

    private HttpPost request(URI uri, String apiKey, String jsonBody, Duration timeout) {
        URI safeUri = endpointPolicy.validateRequestUri(uri);
        long timeoutMillis = Math.max(1L, timeout == null ? 60_000L : timeout.toMillis());
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setConnectTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMillis))
                .build();
        HttpPost request = new HttpPost(safeUri);
        request.setConfig(requestConfig);
        request.setHeader("Authorization", "Bearer " + apiKey);
        request.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
        request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        return request;
    }

    private void requireSuccess(CloseableHttpResponse response) throws IOException {
        int statusCode = response.getCode();
        if (statusCode < 200 || statusCode >= 300) {
            EntityUtils.consumeQuietly(response.getEntity());
            throw new ProviderHttpStatusException(statusCode);
        }
    }

    public static final class ProviderHttpStatusException extends IOException {

        private final int statusCode;

        public ProviderHttpStatusException(int statusCode) {
            super("AI Provider returned HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
