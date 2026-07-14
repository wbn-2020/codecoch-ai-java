package com.codecoachai.resume.feign;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.resume.feign.vo.InnerFileUploadVO;
import feign.Client;
import feign.Feign;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

class FileFeignClientMultipartContractTest {

    @Test
    void generatedResourceIsEncodedAsNamedFileMultipartPart() throws Exception {
        CapturingClient capturingClient = new CapturingClient();
        FileFeignClient client = Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(HttpMessageConverters::new))
                .decoder((response, type) -> Result.success(new InnerFileUploadVO()))
                .client(capturingClient)
                .target(FileFeignClient.class, "http://file-service");
        Path generatedFile = Files.createTempFile("resume-artifact-contract-", ".pdf");
        Files.writeString(generatedFile, "%PDF-contract-body", StandardCharsets.UTF_8);

        try {
            client.upload(new PathMultipartFile(generatedFile, "resume.pdf", "application/pdf"), "RESUME", 10L);
        } finally {
            Files.deleteIfExists(generatedFile);
        }

        Request request = capturingClient.request;
        assertNotNull(request);
        String contentType = request.headers().get("Content-Type").iterator().next();
        String body = new String(request.body(), StandardCharsets.UTF_8);
        assertTrue(contentType.startsWith("multipart/form-data;"), contentType);
        assertTrue(contentType.contains("boundary="), contentType);
        assertTrue(body.contains("Content-Disposition: form-data; name=\"file\""), body);
        assertTrue(body.contains("filename=\"resume.pdf\""), body);
        assertTrue(body.contains("%PDF-contract-body"), body);
    }

    private static final class CapturingClient implements Client {

        private Request request;

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            this.request = request;
            return Response.builder()
                    .status(200)
                    .reason("OK")
                    .request(request)
                    .body("{}", StandardCharsets.UTF_8)
                    .build();
        }
    }
}
