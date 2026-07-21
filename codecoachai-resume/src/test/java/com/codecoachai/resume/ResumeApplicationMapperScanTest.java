package com.codecoachai.resume;

import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeApplicationMapperScanTest {

    @Test
    void scansAllV7MapperPackagesWithoutTreatingServiceInterfacesAsMappers() {
        MapperScans mapperScans = ResumeApplication.class.getAnnotation(MapperScans.class);

        assertThat(mapperScans).isNotNull();
        List<String> scannedPackages = new ArrayList<>();
        for (MapperScan mapperScan : mapperScans.value()) {
            scannedPackages.addAll(Arrays.asList(mapperScan.value()));
            scannedPackages.addAll(Arrays.asList(mapperScan.basePackages()));
        }
        assertThat(scannedPackages)
            .contains(
                "com.codecoachai.resume.careerinterview.mapper",
                "com.codecoachai.resume.careeroffer.mapper",
                "com.codecoachai.resume.careercontact.mapper",
                "com.codecoachai.resume.careerresearch.mapper",
                "com.codecoachai.resume.careercampaign"
            );
        assertThat(Arrays.asList(mapperScans.value()))
            .filteredOn(scan -> Arrays.asList(scan.value()).contains("com.codecoachai.resume.careercampaign")
                || Arrays.asList(scan.basePackages()).contains("com.codecoachai.resume.careercampaign"))
            .singleElement()
            .satisfies(scan -> assertThat(scan.annotationClass()).isEqualTo(Mapper.class));
    }
}
