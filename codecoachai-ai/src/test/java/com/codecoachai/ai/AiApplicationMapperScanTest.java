package com.codecoachai.ai;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AiApplicationMapperScanTest {

    @Test
    void scansCampaignReviewMappers() {
        MapperScan mapperScan = AiApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(Arrays.asList(mapperScan.value()))
            .contains(
                "com.codecoachai.ai.agent.campaignreview.mapper",
                "com.codecoachai.ai.agent.campaigncockpit.mapper",
                "com.codecoachai.ai.agent.campaignpulse.mapper");
    }

    @Test
    void scansCampaignCockpitFeignClient() {
        EnableFeignClients feignClients =
                AiApplication.class.getAnnotation(EnableFeignClients.class);

        assertThat(feignClients).isNotNull();
        assertThat(Arrays.asList(feignClients.basePackages()))
                .contains(
                        "com.codecoachai.ai.agent.feign",
                        "com.codecoachai.ai.agent.campaigncockpit");
    }
}
