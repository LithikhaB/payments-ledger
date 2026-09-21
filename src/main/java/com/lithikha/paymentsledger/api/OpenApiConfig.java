package com.lithikha.paymentsledger.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Payments Ledger API")
                .version("v1")
                .description("Double-entry ledger with idempotent transfers and optimistic locking"));
    }
}