package com.codecoachai.interview.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.interview.clone-claim")
public class InterviewCloneClaimProperties {

    private Duration timeout = Duration.ofMinutes(2);
}
