package com.example.stockbrokerage.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockBrokerageOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("Stock Brokerage API")
                .description("""
                    REST API for a full-stack stock brokerage platform.\n
                    **Features:**\n
                    - Client & account management\n
                    - Order execution with fraud detection and business-rule validation (Drools)\n
                    - Portfolio tracking and P/L reporting\n
                    - Multi-technique trend analysis (MA Crossover, RSI, MACD, Momentum, Volume) with adaptive per-stock weights\n
                    - 8-hour stock price prediction using 5-min Yahoo Finance bars (Linear Regression, EMA, Momentum, Mean Reversion, Holt-Winters)\n
                    - CSV import/export for holdings and activity\n
                    \n
                    **Authentication:** Use `POST /api/auth/login` to obtain a session token, then click **Authorize** and enter it.
                    """)
                .version("2.0.0")
                .contact(new Contact()
                    .name("Stock Brokerage Team")
                    .email("support@stockbrokerage.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .externalDocs(new ExternalDocumentation()
                .description("GitHub Repository")
                .url("https://github.com/vkhera/tradewebapp"))
            .addServersItem(new Server().url("http://localhost:8080").description("Local development"))
            // Ordered tag list controls section order in Swagger UI
            .tags(List.of(
                new Tag().name("Authentication").description("Login and session management"),
                new Tag().name("Accounts").description("Client cash account operations (fund / withdraw)"),
                new Tag().name("Portfolio").description("Portfolio holdings, P/L and summary"),
                new Tag().name("Stocks").description("Real-time stock price and quote lookup"),
                new Tag().name("Trades").description("Order submission, status and history"),
                new Tag().name("Trend Analysis").description("Multi-technique stock trend analysis with adaptive per-stock weights"),
                new Tag().name("Price Predictions").description("8-hour hourly price predictions using 5-min Yahoo Finance bars"),
                new Tag().name("Import").description("Bulk CSV import of holdings and activity"),
                new Tag().name("Clients").description("Client registration and profile management"),
                new Tag().name("Admin – Clients").description("Admin: client management and audit logs"),
                new Tag().name("Admin – Trades").description("Admin: full trade history and audit logs"),
                new Tag().name("Admin – Rules").description("Admin: business rule CRUD (fraud / cash validation)")
            ))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the token returned by POST /api/auth/login")));
    }
}
