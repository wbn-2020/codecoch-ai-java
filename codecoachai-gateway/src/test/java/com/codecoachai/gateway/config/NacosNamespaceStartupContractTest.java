package com.codecoachai.gateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ClientBasicParamUtil;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;

class NacosNamespaceStartupContractTest {

    private static final String NAMESPACE_PLACEHOLDER = "${NACOS_NAMESPACE}";

    @Test
    void discoveryAndConfigUseTheSameNamespacePlaceholder() {
        Properties properties = applicationProperties();

        assertEquals(
                NAMESPACE_PLACEHOLDER,
                properties.getProperty("spring.cloud.nacos.discovery.namespace"));
        assertEquals(
                NAMESPACE_PLACEHOLDER,
                properties.getProperty("spring.cloud.nacos.config.namespace"));
    }

    @Test
    void missingNamespaceCannotResolveTheRequiredPlaceholder() {
        Properties properties = applicationProperties();
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addLast(new PropertiesPropertySource("application", properties));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveRequiredPlaceholders(
                        properties.getProperty("spring.cloud.nacos.config.namespace")));
    }

    @Test
    void dedicatedNamespaceResolvesForDiscoveryAndConfig() {
        Properties properties = applicationProperties();
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(new MapPropertySource(
                "deployment",
                Map.of("NACOS_NAMESPACE", "codecoachai-test")));
        propertySources.addLast(new PropertiesPropertySource("application", properties));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        assertEquals(
                "codecoachai-test",
                resolver.resolveRequiredPlaceholders(
                        properties.getProperty("spring.cloud.nacos.discovery.namespace")));
        assertEquals(
                "codecoachai-test",
                resolver.resolveRequiredPlaceholders(
                        properties.getProperty("spring.cloud.nacos.config.namespace")));
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

    private Properties applicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        return factory.getObject();
    }
}
