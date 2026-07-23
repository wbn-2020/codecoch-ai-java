package com.codecoachai.common.feign.multipart;

import feign.Client;
import feign.Request;
import feign.Response;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;

/**
 * Streams prepared multipart files through a fixed-length HTTP request.
 */
public final class StreamingMultipartClient implements Client {

    static final int COPY_BUFFER_BYTES = 8192;

    private final Client delegate;
    private final LoadBalancerClient loadBalancerClient;
    private final HttpConnectionFactory connectionFactory;

    public StreamingMultipartClient(Client delegate, LoadBalancerClient loadBalancerClient) {
        this(delegate, loadBalancerClient, uri -> (HttpURLConnection) uri.toURL().openConnection());
    }

    StreamingMultipartClient(
            Client delegate,
            LoadBalancerClient loadBalancerClient,
            HttpConnectionFactory connectionFactory) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.loadBalancerClient = loadBalancerClient;
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        String token = firstHeader(request.headers(), StreamingMultipartEncoder.MARKER_HEADER);
        if (token == null) {
            return delegate.execute(request, options);
        }

        StreamingMultipartEncoder.PreparedMultipart prepared =
                MultipartLeaseRegistry.take(token, request.body());
        if (prepared == null) {
            throw new IOException("Streaming multipart metadata is unavailable or belongs to another request");
        }
        ResolvedMultipart multipart = resolve(prepared);
        URI originalUri = requestUri(request.url());
        if (shouldLoadBalance(originalUri, prepared.serviceId())) {
            if (loadBalancerClient == null) {
                throw new IOException("No load balancer is available for streaming multipart service "
                        + prepared.serviceId());
            }
            return loadBalancerClient.execute(prepared.serviceId(), instance -> {
                URI resolvedUri = loadBalancerClient.reconstructURI(instance, originalUri);
                return executeStreaming(request, options, resolvedUri, multipart);
            });
        }
        return executeStreaming(request, options, originalUri, multipart);
    }

    private Response executeStreaming(
            Request request,
            Request.Options options,
            URI uri,
            ResolvedMultipart multipart) throws IOException {
        HttpURLConnection connection = connectionFactory.open(uri);
        boolean responseOwnsConnection = false;
        try {
            connection.setRequestMethod(request.httpMethod().name());
            connection.setConnectTimeout(options.connectTimeoutMillis());
            connection.setReadTimeout(options.readTimeoutMillis());
            connection.setInstanceFollowRedirects(options.isFollowRedirects());
            connection.setDoInput(true);
            connection.setDoOutput(true);
            copyRequestHeaders(request.headers(), connection);
            connection.setRequestProperty(
                    HttpHeaders.CONTENT_TYPE,
                    "multipart/form-data; boundary=" + multipart.boundary());
            connection.setFixedLengthStreamingMode(multipart.contentLength());

            try (OutputStream output = connection.getOutputStream()) {
                byte[] copyBuffer = new byte[COPY_BUFFER_BYTES];
                for (ResolvedPart part : multipart.parts()) {
                    output.write(part.header());
                    if (part instanceof ResolvedFilePart filePart) {
                        writeFile(output, copyBuffer, filePart);
                    } else if (part instanceof ResolvedTextPart textPart) {
                        output.write(textPart.value());
                    }
                    output.write(CRLF);
                }
                output.write(multipart.closingBoundary());
            }

            int status = connection.getResponseCode();
            InputStream responseStream = responseStream(connection, status);
            Map<String, Collection<String>> responseHeaders = responseHeaders(connection);
            Request sanitizedRequest = sanitizedRequest(request);
            Response.Builder response = Response.builder()
                    .status(status)
                    .reason(connection.getResponseMessage())
                    .headers(responseHeaders)
                    .request(sanitizedRequest)
                    .protocolVersion(protocolVersion(connection));
            if (responseStream == null) {
                connection.disconnect();
                return response.body(new byte[0]).build();
            }
            Response built = response.body(
                            new DisconnectingInputStream(responseStream, connection),
                            responseLength(connection))
                    .build();
            responseOwnsConnection = true;
            return built;
        } finally {
            if (!responseOwnsConnection) {
                connection.disconnect();
            }
        }
    }

    private ResolvedMultipart resolve(StreamingMultipartEncoder.PreparedMultipart prepared) throws IOException {
        List<ResolvedPart> parts = new ArrayList<>(prepared.parts().size());
        long contentLength = 0L;
        for (StreamingMultipartEncoder.Part part : prepared.parts()) {
            byte[] header;
            if (part instanceof StreamingMultipartEncoder.FilePart filePart) {
                MultipartFile file = filePart.file();
                long currentSize = file.getSize();
                if (currentSize != filePart.size()) {
                    throw new IOException("Multipart file size changed before upload");
                }
                header = fileHeader(
                        prepared.boundary(),
                        filePart.name(),
                        filePart.filename(),
                        filePart.contentType());
                parts.add(new ResolvedFilePart(header, file, filePart.size()));
                contentLength = addLength(contentLength, header.length);
                contentLength = addLength(contentLength, filePart.size());
            } else if (part instanceof StreamingMultipartEncoder.TextPart textPart) {
                header = textHeader(prepared.boundary(), textPart.name());
                parts.add(new ResolvedTextPart(header, textPart.value()));
                contentLength = addLength(contentLength, header.length);
                contentLength = addLength(contentLength, textPart.value().length);
            }
            contentLength = addLength(contentLength, CRLF.length);
        }
        byte[] closingBoundary = ("--" + prepared.boundary() + "--\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        contentLength = addLength(contentLength, closingBoundary.length);
        return new ResolvedMultipart(
                prepared.boundary(),
                List.copyOf(parts),
                closingBoundary,
                contentLength);
    }

    private void writeFile(
            OutputStream output,
            byte[] buffer,
            ResolvedFilePart filePart) throws IOException {
        try (InputStream input = filePart.file().getInputStream()) {
            long remaining = filePart.size();
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IOException("Multipart file ended before its declared size");
                }
                if (read == 0) {
                    int single = input.read();
                    if (single < 0) {
                        throw new IOException("Multipart file ended before its declared size");
                    }
                    output.write(single);
                    remaining--;
                    continue;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
            if (input.read() != -1) {
                throw new IOException("Multipart file grew beyond its declared size");
            }
        }
    }

    private void copyRequestHeaders(
            Map<String, Collection<String>> headers,
            HttpURLConnection connection) {
        headers.forEach((name, values) -> {
            if (isTransportHeader(name) || StreamingMultipartEncoder.MARKER_HEADER.equalsIgnoreCase(name)) {
                return;
            }
            for (String value : values) {
                if (value != null) {
                    connection.addRequestProperty(name, value);
                }
            }
        });
    }

    private boolean isTransportHeader(String name) {
        return HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)
                || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)
                || HttpHeaders.HOST.equalsIgnoreCase(name)
                || HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name);
    }

    private Request sanitizedRequest(Request request) {
        Map<String, Collection<String>> headers = new LinkedHashMap<>();
        request.headers().forEach((name, values) -> {
            if (!StreamingMultipartEncoder.MARKER_HEADER.equalsIgnoreCase(name)) {
                headers.put(name, values);
            }
        });
        return Request.create(
                request.httpMethod(),
                request.url(),
                headers,
                Request.Body.empty(),
                request.requestTemplate());
    }

    private InputStream responseStream(HttpURLConnection connection, int status) throws IOException {
        if (status >= 400) {
            return connection.getErrorStream();
        }
        return connection.getInputStream();
    }

    private Map<String, Collection<String>> responseHeaders(HttpURLConnection connection) {
        Map<String, Collection<String>> headers = new LinkedHashMap<>();
        connection.getHeaderFields().forEach((name, values) -> {
            if (name != null && values != null) {
                headers.put(name, List.copyOf(values));
            }
        });
        return headers;
    }

    private Integer responseLength(HttpURLConnection connection) {
        long length = connection.getContentLengthLong();
        return length < 0 || length > Integer.MAX_VALUE ? null : (int) length;
    }

    private Request.ProtocolVersion protocolVersion(HttpURLConnection connection) {
        String statusLine = connection.getHeaderField(0);
        if (statusLine != null && statusLine.toUpperCase(Locale.ROOT).startsWith("HTTP/1.0")) {
            return Request.ProtocolVersion.HTTP_1_0;
        }
        return Request.ProtocolVersion.HTTP_1_1;
    }

    private boolean shouldLoadBalance(URI uri, String serviceId) {
        return serviceId != null
                && uri.getHost() != null
                && uri.getHost().equalsIgnoreCase(serviceId);
    }

    private URI requestUri(String value) throws IOException {
        try {
            return new URI(value);
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw new IOException("Streaming multipart request URL is invalid", ex);
        }
    }

    private String firstHeader(Map<String, Collection<String>> headers, String expectedName) {
        return headers.entrySet().stream()
                .filter(entry -> expectedName.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private byte[] fileHeader(String boundary, String name, String filename, String contentType) {
        return ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + quoted(name)
                + "\"; filename=\"" + quoted(filename) + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] textHeader(String boundary, String name) {
        return ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + quoted(name) + "\"\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private String quoted(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private long addLength(long current, long addition) throws IOException {
        try {
            return Math.addExact(current, addition);
        } catch (ArithmeticException ex) {
            throw new IOException("Multipart request length exceeds supported range", ex);
        }
    }

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private sealed interface ResolvedPart permits ResolvedFilePart, ResolvedTextPart {
        byte[] header();
    }

    private record ResolvedFilePart(
            byte[] header,
            MultipartFile file,
            long size) implements ResolvedPart {
    }

    private record ResolvedTextPart(
            byte[] header,
            byte[] value) implements ResolvedPart {
    }

    private record ResolvedMultipart(
            String boundary,
            List<ResolvedPart> parts,
            byte[] closingBoundary,
            long contentLength) {
    }

    private static final class DisconnectingInputStream extends FilterInputStream {

        private final HttpURLConnection connection;

        private DisconnectingInputStream(InputStream input, HttpURLConnection connection) {
            super(input);
            this.connection = connection;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                connection.disconnect();
            }
        }
    }

    @FunctionalInterface
    interface HttpConnectionFactory {

        HttpURLConnection open(URI uri) throws IOException;
    }
}
