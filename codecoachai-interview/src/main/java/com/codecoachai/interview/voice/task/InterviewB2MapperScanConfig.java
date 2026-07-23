package com.codecoachai.interview.voice.task;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(annotationClass = Mapper.class, basePackages = {
        "com.codecoachai.interview.scenario",
        "com.codecoachai.interview.voicedelivery",
        "com.codecoachai.interview.audioretention"
})
public class InterviewB2MapperScanConfig {
}
