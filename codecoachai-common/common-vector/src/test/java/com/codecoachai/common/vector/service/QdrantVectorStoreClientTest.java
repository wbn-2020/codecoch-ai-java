package com.codecoachai.common.vector.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codecoachai.common.vector.config.VectorStoreProperties;
import com.codecoachai.common.vector.domain.VectorPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class QdrantVectorStoreClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void upsertSplitsPointsIntoConfiguredBatches() throws Exception {
        VectorStoreProperties properties = new VectorStoreProperties();
        properties.setBaseUrl("http://qdrant.test");
        properties.setUpsertBatchSize(2);
        RecordingHttpClient httpClient = new RecordingHttpClient();
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties, objectMapper, httpClient);

        client.upsert("question_embedding", List.of(
                point("1"),
                point("2"),
                point("3"),
                point("4"),
                point("5")
        ));

        assertThat(httpClient.requests()).hasSize(3);
        assertThat(httpClient.requests()).allSatisfy(request ->
                assertThat(request.uri()).isEqualTo(URI.create("http://qdrant.test/collections/question_embedding/points?wait=true")));
        assertThat(httpClient.payloadPointCounts()).containsExactly(2, 2, 1);
        assertThat(httpClient.payloadPointCounts()).allSatisfy(count ->
                assertThat(count).isLessThanOrEqualTo(properties.getUpsertBatchSize()));
    }

    @Test
    void upsertBatchSizeIsClampedToSafeBounds() {
        VectorStoreProperties properties = new VectorStoreProperties();

        properties.setUpsertBatchSize(0);
        assertThat(properties.getUpsertBatchSize()).isEqualTo(1);

        properties.setUpsertBatchSize(5000);
        assertThat(properties.getUpsertBatchSize()).isEqualTo(1000);
    }

    private VectorPoint point(String id) {
        return VectorPoint.builder()
                .id(id)
                .vector(List.of(0.1f, 0.2f))
                .payload(Map.of("source", "test"))
                .build();
    }

    private final class RecordingHttpClient extends HttpClient {

        private final List<HttpRequest> requests = new ArrayList<>();
        private final List<String> bodies = new ArrayList<>();

        List<HttpRequest> requests() {
            return requests;
        }

        List<Integer> payloadPointCounts() {
            return bodies.stream()
                    .map(body -> {
                        try {
                            JsonNode points = objectMapper.readTree(body).path("points");
                            return points.size();
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .toList();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<java.net.Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<java.util.concurrent.Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            requests.add(request);
            bodies.add(readBody(request));
            return buildResponse(request, responseBodyHandler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> responseBodyHandler) {
            try {
                return CompletableFuture.completedFuture(send(request, responseBodyHandler));
            } catch (IOException | InterruptedException ex) {
                CompletableFuture<HttpResponse<T>> failed = new CompletableFuture<>();
                failed.completeExceptionally(ex);
                return failed;
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        private String readBody(HttpRequest request) {
            BodySubscriber subscriber = new BodySubscriber();
            request.bodyPublisher().orElseThrow().subscribe(subscriber);
            return subscriber.body().join();
        }

        private <T> HttpResponse<T> buildResponse(HttpRequest request, BodyHandler<T> responseBodyHandler)
                throws IOException {
            ResponseInfo responseInfo = new ResponseInfo() {
                @Override
                public int statusCode() {
                    return 200;
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(Map.of(), (name, value) -> true);
                }

                @Override
                public Version version() {
                    return Version.HTTP_1_1;
                }
            };
            HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(responseInfo);
            subscriber.onSubscribe(new CompletedSubscription());
            subscriber.onNext(List.of(ByteBuffer.wrap("{\"result\":{}}".getBytes())));
            subscriber.onComplete();
            return new RecordedResponse<>(request, subscriber.getBody().toCompletableFuture().join());
        }
    }

    private static final class BodySubscriber implements Flow.Subscriber<ByteBuffer> {

        private final CompletableFuture<String> body = new CompletableFuture<>();
        private final StringBuilder builder = new StringBuilder();

        CompletableFuture<String> body() {
            return body;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            builder.append(new String(item.array(), item.position(), item.remaining()));
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(builder.toString());
        }
    }

    private static final class CompletedSubscription implements Subscription {

        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
        }
    }

    private record RecordedResponse<T>(HttpRequest request, T body) implements HttpResponse<T> {

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}
