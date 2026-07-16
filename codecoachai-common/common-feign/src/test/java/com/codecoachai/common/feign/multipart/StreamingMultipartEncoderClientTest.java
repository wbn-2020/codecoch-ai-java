package com.codecoachai.common.feign.multipart;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import feign.Client;
import feign.Capability;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.Target;
import feign.codec.Encoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

class StreamingMultipartEncoderClientTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void releaseLeasesAfterAssertion() {
        int pending = MultipartLeaseRegistry.pendingLeaseCount();
        MultipartLeaseRegistry.releaseAll();
        assertEquals(0, pending, "streaming multipart lease leaked from the test");
    }

    @Test
    void clientSpecificConfigurationBuildsStreamingEncoderAndCapability() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    HttpMessageConverters.class,
                    () -> new HttpMessageConverters(List.of()));
            context.register(StreamingMultipartFeignConfiguration.class);
            context.refresh();

            assertInstanceOf(StreamingMultipartEncoder.class, context.getBean(Encoder.class));
            assertInstanceOf(StreamingMultipartCapability.class, context.getBean(Capability.class));
        }
    }

    @Test
    void nonMultipartEncodingAndRequestsUseTheirDelegates() throws Exception {
        AtomicInteger encoderCalls = new AtomicInteger();
        Encoder delegateEncoder = (object, bodyType, template) -> {
            encoderCalls.incrementAndGet();
            template.body("delegate-body");
        };
        StreamingMultipartEncoder encoder =
                new StreamingMultipartEncoder(delegateEncoder, StreamingMultipartEncoder.DEFAULT_MAX_FILE_BYTES);
        RequestTemplate template = baseTemplate(MediaType.APPLICATION_JSON_VALUE);

        encoder.encode(Map.of("value", "test"), Map.class, template);

        assertEquals(1, encoderCalls.get());
        assertEquals("delegate-body", new String(template.body(), StandardCharsets.UTF_8));
        assertEquals(0, MultipartLeaseRegistry.pendingLeaseCount());

        AtomicInteger clientCalls = new AtomicInteger();
        Client delegateClient = (request, options) -> {
            clientCalls.incrementAndGet();
            return Response.builder()
                    .status(204)
                    .reason("No Content")
                    .request(request)
                    .build();
        };
        StreamingMultipartClient client = new StreamingMultipartClient(delegateClient, null);
        Request request = request(template);

        Response response = client.execute(request, new Request.Options());

        assertEquals(204, response.status());
        assertEquals(1, clientCalls.get());
        assertEquals(0, MultipartLeaseRegistry.pendingLeaseCount());
    }

    @Test
    void errorResponseBodyCloseDisconnectsAndLeaseIsConsumed() throws Exception {
        Path source = Files.writeString(tempDir.resolve("resume.pdf"), "%PDF-error", StandardCharsets.UTF_8);
        NoBytesMultipartFile file = new NoBytesMultipartFile(source, "resume.pdf", "application/pdf");
        StreamingMultipartEncoder encoder = encoder();
        RequestTemplate template = baseTemplate(MediaType.MULTIPART_FORM_DATA_VALUE);
        encoder.encode(Map.of("file", file), Map.class, template);
        assertEquals(1, MultipartLeaseRegistry.pendingLeaseCount());
        RecordingHttpURLConnection connection = new RecordingHttpURLConnection(
                new URL("http://example.test/upload"),
                422,
                "Unprocessable Entity",
                "rejected");
        StreamingMultipartClient client = new StreamingMultipartClient(
                failingDelegate(),
                null,
                ignored -> connection);

        Response response = client.execute(request(template), new Request.Options());

        assertEquals(422, response.status());
        assertEquals(0, MultipartLeaseRegistry.pendingLeaseCount());
        assertFalse(connection.disconnected.get());
        assertFalse(connection.requestHeaders.containsKey(StreamingMultipartEncoder.MARKER_HEADER));
        assertTrue(connection.output.toString(StandardCharsets.UTF_8).contains("%PDF-error"));
        assertArrayEquals(
                "rejected".getBytes(StandardCharsets.UTF_8),
                response.body().asInputStream().readAllBytes());

        response.close();

        assertTrue(connection.responseStreamClosed.get());
        assertTrue(connection.disconnected.get());
        assertEquals(0, file.getBytesCalls.get());
        assertEquals(1, file.inputCloseCalls.get());
    }

    @Test
    void connectionFailureDoesNotLeaveMultipartLeaseOrOpenSource() throws Exception {
        Path source = Files.writeString(tempDir.resolve("resume.docx"), "docx", StandardCharsets.UTF_8);
        NoBytesMultipartFile file = new NoBytesMultipartFile(
                source,
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        RequestTemplate template = baseTemplate(MediaType.MULTIPART_FORM_DATA_VALUE);
        encoder().encode(Map.of("file", file), Map.class, template);
        StreamingMultipartClient client = new StreamingMultipartClient(
                failingDelegate(),
                null,
                ignored -> {
                    throw new IOException("connection unavailable");
                });

        IOException error = assertThrows(
                IOException.class,
                () -> client.execute(request(template), new Request.Options()));

        assertTrue(error.getMessage().contains("connection unavailable"));
        assertEquals(0, MultipartLeaseRegistry.pendingLeaseCount());
        assertEquals(0, file.inputOpenCalls.get());
        assertEquals(0, file.getBytesCalls.get());
    }

    @Test
    void tokenBodyKeepsStrongLeaseUntilExecuteAndIsNotFileContent() throws Exception {
        Path source = Files.writeString(tempDir.resolve("strong.txt"), "file-content", StandardCharsets.UTF_8);
        NoBytesMultipartFile file = new NoBytesMultipartFile(source, "strong.txt", "text/plain");
        RequestTemplate template = baseTemplate(MediaType.MULTIPART_FORM_DATA_VALUE);
        encoder().encode(Map.of("file", file), Map.class, template);

        byte[] tokenBody = template.body();
        String token = firstHeader(template.headers(), StreamingMultipartEncoder.MARKER_HEADER);

        assertNotNull(token);
        assertEquals(token, new String(tokenBody, StandardCharsets.US_ASCII));
        assertFalse(new String(tokenBody, StandardCharsets.US_ASCII).contains("file-content"));
        assertEquals(1, MultipartLeaseRegistry.pendingLeaseCount());

        StreamingMultipartEncoder.PreparedMultipart prepared =
                MultipartLeaseRegistry.take(token, tokenBody);

        assertNotNull(prepared);
        assertEquals(file, ((StreamingMultipartEncoder.FilePart) prepared.parts().get(0)).file());
        assertEquals(0, MultipartLeaseRegistry.pendingLeaseCount());
    }

    @Test
    void serviceNamedTargetIsResolvedThroughLoadBalancerBeforeStreaming() throws Exception {
        Path source = Files.writeString(tempDir.resolve("balanced.pdf"), "%PDF-balanced", StandardCharsets.UTF_8);
        NoBytesMultipartFile file = new NoBytesMultipartFile(source, "balanced.pdf", "application/pdf");
        RequestTemplate template = baseTemplate(MediaType.MULTIPART_FORM_DATA_VALUE);
        template.target("http://codecoachai-file");
        template.feignTarget(new Target.HardCodedTarget<>(
                FileServiceMarker.class,
                "codecoachai-file",
                "http://codecoachai-file"));
        encoder().encode(Map.of("file", file), Map.class, template);
        RecordingLoadBalancerClient loadBalancer = new RecordingLoadBalancerClient();
        AtomicReference<URI> openedUri = new AtomicReference<>();
        RecordingHttpURLConnection connection = new RecordingHttpURLConnection(
                new URL("http://127.0.0.1:18081/upload"),
                200,
                "OK",
                "ok");
        StreamingMultipartClient client = new StreamingMultipartClient(
                failingDelegate(),
                loadBalancer,
                uri -> {
                    openedUri.set(uri);
                    return connection;
                });

        Response response = client.execute(request(template), new Request.Options());
        response.close();

        assertEquals("codecoachai-file", loadBalancer.executedServiceId.get());
        assertEquals("127.0.0.1", openedUri.get().getHost());
        assertEquals(18081, openedUri.get().getPort());
        assertEquals("/upload", openedUri.get().getPath());
        assertEquals(0, MultipartLeaseRegistry.pendingLeaseCount());
        assertEquals(0, file.getBytesCalls.get());
    }

    private StreamingMultipartEncoder encoder() {
        return new StreamingMultipartEncoder(
                (object, bodyType, template) -> {
                    throw new AssertionError("multipart request must not use the buffering encoder");
                },
                StreamingMultipartEncoder.DEFAULT_MAX_FILE_BYTES);
    }

    private RequestTemplate baseTemplate(String contentType) {
        return new RequestTemplate()
                .method(Request.HttpMethod.POST)
                .target("http://example.test")
                .uri("/upload")
                .header(HttpHeaders.CONTENT_TYPE, contentType);
    }

    private Client failingDelegate() {
        return (request, options) -> {
            throw new AssertionError("streaming multipart request must not use the delegate client");
        };
    }

    private Request request(RequestTemplate template) {
        return template.resolve(Map.of()).request();
    }

    private String firstHeader(Map<String, Collection<String>> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }

    private interface FileServiceMarker {
    }

    private static class NoBytesMultipartFile implements MultipartFile {

        private final Path path;
        private final String filename;
        private final String contentType;
        private final AtomicInteger getBytesCalls = new AtomicInteger();
        private final AtomicInteger inputOpenCalls = new AtomicInteger();
        private final AtomicInteger inputCloseCalls = new AtomicInteger();

        private NoBytesMultipartFile(Path path, String filename, String contentType) {
            this.path = path;
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return getSize() == 0;
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public byte[] getBytes() {
            getBytesCalls.incrementAndGet();
            throw new AssertionError("getBytes() must not be called");
        }

        @Override
        public InputStream getInputStream() throws IOException {
            inputOpenCalls.incrementAndGet();
            InputStream input = Files.newInputStream(path);
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    return input.read(bytes, offset, length);
                }

                @Override
                public void close() throws IOException {
                    try {
                        input.close();
                    } finally {
                        inputCloseCalls.incrementAndGet();
                    }
                }
            };
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.copy(path, dest.toPath());
        }
    }

    private static final class RecordingHttpURLConnection extends HttpURLConnection {

        private final int responseCode;
        private final String responseMessage;
        private final byte[] responseBody;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final Map<String, List<String>> requestHeaders = new java.util.LinkedHashMap<>();
        private final AtomicBoolean disconnected = new AtomicBoolean();
        private final AtomicBoolean responseStreamClosed = new AtomicBoolean();

        private RecordingHttpURLConnection(
                URL url,
                int responseCode,
                String responseMessage,
                String responseBody) {
            super(url);
            this.responseCode = responseCode;
            this.responseMessage = responseMessage;
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void disconnect() {
            disconnected.set(true);
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public OutputStream getOutputStream() {
            connected = true;
            return output;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public String getResponseMessage() {
            return responseMessage;
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(responseBody) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        responseStreamClosed.set(true);
                    }
                }
            };
        }

        @Override
        public InputStream getInputStream() {
            return getErrorStream();
        }

        @Override
        public long getContentLengthLong() {
            return responseBody.length;
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            return Map.of(
                    "Content-Type", List.of("text/plain"),
                    "Content-Length", List.of(String.valueOf(responseBody.length)));
        }

        @Override
        public String getHeaderField(int index) {
            return index == 0 ? "HTTP/1.1 " + responseCode + " " + responseMessage : null;
        }

        @Override
        public void addRequestProperty(String key, String value) {
            requestHeaders.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(value);
        }

        @Override
        public void setRequestProperty(String key, String value) {
            requestHeaders.put(key, new java.util.ArrayList<>(List.of(value)));
        }
    }

    private static final class RecordingLoadBalancerClient implements LoadBalancerClient {

        private final ServiceInstance instance = new DefaultServiceInstance(
                "file-1",
                "codecoachai-file",
                "127.0.0.1",
                18081,
                false);
        private final AtomicReference<String> executedServiceId = new AtomicReference<>();

        @Override
        public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
            executedServiceId.set(serviceId);
            return execute(serviceId, instance, request);
        }

        @Override
        public <T> T execute(
                String serviceId,
                ServiceInstance serviceInstance,
                LoadBalancerRequest<T> request) throws IOException {
            try {
                return request.apply(serviceInstance);
            } catch (IOException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }

        @Override
        public URI reconstructURI(ServiceInstance serviceInstance, URI original) {
            String query = original.getRawQuery() == null ? "" : "?" + original.getRawQuery();
            return URI.create("http://" + serviceInstance.getHost() + ":" + serviceInstance.getPort()
                    + original.getRawPath() + query);
        }

        @Override
        public ServiceInstance choose(String serviceId) {
            return instance;
        }

        @Override
        public <T> ServiceInstance choose(
                String serviceId,
                org.springframework.cloud.client.loadbalancer.Request<T> request) {
            return instance;
        }
    }
}
