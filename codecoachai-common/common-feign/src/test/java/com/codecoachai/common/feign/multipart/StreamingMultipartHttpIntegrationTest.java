package com.codecoachai.common.feign.multipart;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import feign.Client;
import feign.Feign;
import feign.codec.StringDecoder;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

class StreamingMultipartHttpIntegrationTest {

    private static final int TEN_MIB = 10 * 1024 * 1024;

    @TempDir
    Path tempDir;

    @AfterEach
    void releaseLeasesAfterAssertion() {
        int pending = MultipartLeaseRegistry.pendingLeaseCount();
        MultipartLeaseRegistry.releaseAll();
        assertEquals(0, pending, "streaming multipart lease leaked from the test");
    }

    @Test
    void streamsFileAtTenMibLimitWithoutCallingGetBytes() throws Exception {
        try (UploadServer server = new UploadServer(tempDir.resolve("near-limit-server"))) {
            UploadApi api = uploadApi(server);
            Path source = writeRepeated(tempDir.resolve("near-limit.bin"), TEN_MIB, (byte) 0x5a);
            TrackingMultipartFile file =
                    new TrackingMultipartFile(source, "near-limit.pdf", "application/pdf");

            String result = api.upload(file, "near-limit");

            assertEquals("ok", result);
            assertEquals(0, file.getBytesCalls.get());
            assertEquals(1, file.inputOpenCalls.get());
            assertEquals(1, file.inputCloseCalls.get());
            assertTrue(file.maxRequestedRead.get() <= StreamingMultipartClient.COPY_BUFFER_BYTES);
            assertMultipart(
                    server.request("near-limit"),
                    source,
                    TEN_MIB,
                    "near-limit.pdf",
                    "application/pdf");
            assertEquals(0, MultipartLeaseRegistry.pendingLeaseCount());
        }
    }

    @Test
    void concurrentUploadsKeepBodiesIsolatedAndReleaseEveryLease() throws Exception {
        int uploadCount = 8;
        try (UploadServer server = new UploadServer(tempDir.resolve("concurrent-server"))) {
            UploadApi api = uploadApi(server);
            List<TrackingMultipartFile> files = new ArrayList<>();
            List<Path> sources = new ArrayList<>();
            for (int index = 0; index < uploadCount; index++) {
                Path source = writeRepeated(
                        tempDir.resolve("source-" + index + ".bin"),
                        256 * 1024 + index,
                        (byte) (index + 1));
                sources.add(source);
                files.add(new TrackingMultipartFile(
                        source,
                        "resume-" + index + ".pdf",
                        "application/pdf"));
            }

            ExecutorService workers = Executors.newFixedThreadPool(uploadCount);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            try {
                for (int index = 0; index < uploadCount; index++) {
                    int current = index;
                    futures.add(workers.submit(() -> {
                        start.await();
                        return api.upload(files.get(current), "concurrent-" + current);
                    }));
                }
                start.countDown();
                for (Future<String> future : futures) {
                    assertEquals("ok", future.get(20, TimeUnit.SECONDS));
                }
            } finally {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }

            for (int index = 0; index < uploadCount; index++) {
                TrackingMultipartFile file = files.get(index);
                assertEquals(0, file.getBytesCalls.get());
                assertEquals(1, file.inputOpenCalls.get());
                assertEquals(1, file.inputCloseCalls.get());
                assertTrue(file.maxRequestedRead.get() <= StreamingMultipartClient.COPY_BUFFER_BYTES);
                assertMultipart(
                        server.request("concurrent-" + index),
                        sources.get(index),
                        256 * 1024L + index,
                        "resume-" + index + ".pdf",
                        "application/pdf");
            }
            assertEquals(0, MultipartLeaseRegistry.pendingLeaseCount());
        }
    }

    private UploadApi uploadApi(UploadServer server) {
        StreamingMultipartEncoder encoder = new StreamingMultipartEncoder(
                (object, bodyType, template) -> {
                    throw new AssertionError("multipart request must not use the buffering encoder");
                },
                StreamingMultipartEncoder.DEFAULT_MAX_FILE_BYTES);
        Client delegate = (request, options) -> {
            throw new AssertionError("streaming multipart request must not use the delegate client");
        };
        return Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(encoder)
                .decoder(new StringDecoder())
                .client(new StreamingMultipartClient(delegate, null))
                .target(UploadApi.class, server.baseUrl());
    }

    private void assertMultipart(
            ReceivedRequest received,
            Path source,
            long sourceSize,
            String filename,
            String contentType) throws Exception {
        assertNotNull(received);
        String boundary = boundary(received.contentType());
        byte[] probe;
        try (InputStream input = Files.newInputStream(received.body())) {
            probe = input.readNBytes(4096);
        }
        int headerEnd = indexOf(probe, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        assertTrue(headerEnd > 0, "multipart file header terminator is missing");
        long fileOffset = headerEnd + 4L;
        String header = new String(probe, 0, headerEnd, StandardCharsets.UTF_8);
        assertTrue(header.startsWith("--" + boundary + "\r\n"));
        assertTrue(header.contains("Content-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\""));
        assertTrue(header.contains("Content-Type: " + contentType));

        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        assertEquals(fileOffset + sourceSize + suffix.length, Files.size(received.body()));
        assertEquals(Files.size(received.body()), received.contentLength());
        assertArrayEquals(sha256(source), sha256(received.body(), fileOffset, sourceSize));
        assertArrayEquals(suffix, readRange(received.body(), fileOffset + sourceSize, suffix.length));
    }

    private String boundary(String contentType) {
        assertNotNull(contentType);
        String marker = "boundary=";
        int index = contentType.indexOf(marker);
        assertTrue(index >= 0, "multipart boundary is missing");
        return contentType.substring(index + marker.length()).trim();
    }

    private int indexOf(byte[] source, byte[] target) {
        outer:
        for (int index = 0; index <= source.length - target.length; index++) {
            for (int targetIndex = 0; targetIndex < target.length; targetIndex++) {
                if (source[index + targetIndex] != target[targetIndex]) {
                    continue outer;
                }
            }
            return index;
        }
        return -1;
    }

    private byte[] sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = sha256Digest();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        }
    }

    private byte[] sha256(Path path, long offset, long length) throws IOException {
        MessageDigest digest = sha256Digest();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long remaining = length;
            while (remaining > 0) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), remaining));
                int read = channel.read(buffer);
                if (read < 0) {
                    throw new IOException("Multipart body ended before expected file content");
                }
                buffer.flip();
                digest.update(buffer);
                remaining -= read;
            }
        }
        return digest.digest();
    }

    private byte[] readRange(Path path, long offset, int length) throws IOException {
        byte[] bytes = new byte[length];
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) {
                    throw new IOException("Multipart suffix is truncated");
                }
            }
        }
        return bytes;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Path writeRepeated(Path path, long size, byte value) throws IOException {
        byte[] block = new byte[8192];
        java.util.Arrays.fill(block, value);
        try (OutputStream output = Files.newOutputStream(path)) {
            long remaining = size;
            while (remaining > 0) {
                int length = (int) Math.min(block.length, remaining);
                output.write(block, 0, length);
                remaining -= length;
            }
        }
        return path;
    }

    private interface UploadApi {

        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        String upload(
                @RequestPart("file") MultipartFile file,
                @RequestParam("requestId") String requestId);
    }

    private static final class TrackingMultipartFile implements MultipartFile {

        private final Path path;
        private final String filename;
        private final String contentType;
        private final AtomicInteger getBytesCalls = new AtomicInteger();
        private final AtomicInteger inputOpenCalls = new AtomicInteger();
        private final AtomicInteger inputCloseCalls = new AtomicInteger();
        private final AtomicInteger maxRequestedRead = new AtomicInteger();

        private TrackingMultipartFile(Path path, String filename, String contentType) {
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
            return new FilterInputStream(input) {
                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    maxRequestedRead.accumulateAndGet(length, Math::max);
                    return super.read(bytes, offset, length);
                }

                @Override
                public void close() throws IOException {
                    try {
                        super.close();
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

    private static final class UploadServer implements AutoCloseable {

        private final Path directory;
        private final HttpServer server;
        private final ExecutorService executor = Executors.newFixedThreadPool(10);
        private final Map<String, ReceivedRequest> requests = new ConcurrentHashMap<>();

        private UploadServer(Path directory) throws IOException {
            this.directory = Files.createDirectories(directory);
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                    0);
            server.createContext("/upload", this::handle);
            server.setExecutor(executor);
            server.start();
        }

        private String baseUrl() {
            return "http://" + server.getAddress().getAddress().getHostAddress()
                    + ":" + server.getAddress().getPort();
        }

        private ReceivedRequest request(String requestId) {
            return requests.get(requestId);
        }

        private void handle(HttpExchange exchange) throws IOException {
            String requestId = query(exchange.getRequestURI().getRawQuery(), "requestId");
            Path body = Files.createTempFile(directory, "request-", ".multipart");
            try (InputStream input = exchange.getRequestBody();
                 OutputStream output = Files.newOutputStream(body)) {
                input.transferTo(output);
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            long contentLength = Long.parseLong(
                    exchange.getRequestHeaders().getFirst("Content-Length"));
            requests.put(requestId, new ReceivedRequest(body, contentType, contentLength));
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            } finally {
                exchange.close();
            }
        }

        private String query(String rawQuery, String name) {
            if (rawQuery == null) {
                return null;
            }
            for (String parameter : rawQuery.split("&")) {
                String[] pair = parameter.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                if (name.equals(key)) {
                    return pair.length == 1
                            ? ""
                            : URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
            return null;
        }

        @Override
        public void close() throws Exception {
            server.stop(0);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private record ReceivedRequest(
            Path body,
            String contentType,
            long contentLength) {
    }
}
