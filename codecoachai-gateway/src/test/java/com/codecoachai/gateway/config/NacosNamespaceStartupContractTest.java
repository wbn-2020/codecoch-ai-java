package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ClientBasicParamUtil;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class NacosNamespaceStartupContractTest {

    private static final String NAMESPACE_PLACEHOLDER = "${NACOS_NAMESPACE}";

    @Test
    void discoveryAndConfigRequireTheSameExplicitNamespace() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertEquals(
                NAMESPACE_PLACEHOLDER,
                properties.getProperty("spring.cloud.nacos.discovery.namespace"));
        assertEquals(
                NAMESPACE_PLACEHOLDER,
                properties.getProperty("spring.cloud.nacos.config.namespace"));
    }

    @Test
    void resolvedNacosClientDefaultsBlankNamespaceToLiteralPublic() {
        NacosConfigProperties properties = new NacosConfigProperties();
        NacosClientProperties clientProperties =
                NacosClientProperties.PROTOTYPE.derive(properties.assembleConfigServiceProperties());

        assertEquals("public", ClientBasicParamUtil.parseNamespace(clientProperties));
    }

    @Test
    void resolvedNacosClientKeepsDedicatedNamespaceId() {
        NacosConfigProperties properties = new NacosConfigProperties();
        properties.setNamespace("codecoachai-test");
        NacosClientProperties clientProperties =
                NacosClientProperties.PROTOTYPE.derive(properties.assembleConfigServiceProperties());

        assertEquals("codecoachai-test", ClientBasicParamUtil.parseNamespace(clientProperties));
    }
}
