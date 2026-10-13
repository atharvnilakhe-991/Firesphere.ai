package com.firesphere.backend.common.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fireSphereOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("FireSphere.ai API")
                        .description("API Documentation for FireSphere.ai Backend Core")
                        .version("v1.0.0"));
    }
}
