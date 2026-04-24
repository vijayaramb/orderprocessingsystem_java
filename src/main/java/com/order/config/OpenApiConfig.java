package com.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderProcessingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Processing System API")
                        .description("Production-ready order management REST API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Engineering Team")));
    }
}
