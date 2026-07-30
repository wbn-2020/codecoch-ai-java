package com.codecoachai.common.oss.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliyun.oss.OSS;
import com.codecoachai.common.oss.service.OssFileService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OssAutoConfigurationTest {

    @Test
    void defaultsBareAliyunEndpointToHttpsForSignedUrls() {
        OssProperties properties = properties("oss-cn-hangzhou.aliyuncs.com");
        OssAutoConfiguration configuration = new OssAutoConfiguration();
        OSS client = configuration.ossClient(properties);
        try {
            OssFileService service = configuration.ossFileService(client, properties);
            String signedUrl = service.signUrl("acceptance/avatar.png", Duration.ofMinutes(5));

            assertTrue(signedUrl.startsWith("https://acceptance-bucket.oss-cn-hangzhou.aliyuncs.com/"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    void preservesExplicitEndpointSchemeAndRemovesTrailingSlash() {
        assertEquals(
                "http://127.0.0.1:9000",
                OssAutoConfiguration.normalizeClientEndpoint(" http://127.0.0.1:9000/ "));
        assertEquals(
                "https://oss-cn-shanghai.aliyuncs.com",
                OssAutoConfiguration.normalizeClientEndpoint("https://oss-cn-shanghai.aliyuncs.com/"));
    }

    private OssProperties properties(String endpoint) {
        OssProperties properties = new OssProperties();
        properties.setEndpoint(endpoint);
        properties.setBucket("acceptance-bucket");
        properties.setAccessKeyId("test-access-key");
        properties.setAccessKeySecret("test-access-secret");
        return properties;
    }
}
