package com.outreach.datasyncdriver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI driverOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DataSync Driver API")
                        .description("Synchronization driver — manages initial sync and streams changes to external systems")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Outreach Team")
                                .email("support@outreach.com")));
    }
}

