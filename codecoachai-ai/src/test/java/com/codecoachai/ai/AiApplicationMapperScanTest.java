package com.codecoachai.ai;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AiApplicationMapperScanTest {

    @Test
    void scansCampaignReviewMappers() {
        MapperScan mapperScan = AiApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(Arrays.asList(mapperScan.value()))
            .contains("com.codecoachai.ai.agent.campaignreview.mapper");
    }
}
