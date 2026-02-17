package com.example.stockbrokerage.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI stockBrokerageOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Stock Brokerage API")
                .description("Low-latency stock trading platform with rule engine and fraud detection")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Stock Brokerage Team")
                    .email("support@stockbrokerage.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
