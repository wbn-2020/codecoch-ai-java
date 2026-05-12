package com.codecoachai.common.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(OpenAPI.class)
public class SwaggerConfig {

    @Bean
    public OpenAPI codeCoachAiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeCoachAI API")
                        .version("V1")
                        .description("CodeCoachAI V1 backend API"));
    }
}
