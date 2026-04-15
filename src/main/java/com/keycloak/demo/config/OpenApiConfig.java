package com.multitenant.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Swagger/OpenAPI documentation available at /api/swagger-ui.html
 *
 * WHY: Provides interactive API documentation where developers can:
 *   1. See all endpoints grouped by tag (Company Auth, Client Auth, Tenant Management, etc.)
 *   2. Try out API calls directly from the browser
 *   3. Input their JWT token via the "Authorize" button to test protected endpoints
 *
 * The "Bearer Authentication" security scheme adds a padlock icon on every endpoint
 * and an "Authorize" button at the top of the Swagger UI. When you paste a JWT there,
 * it's automatically included as "Authorization: Bearer <token>" in all requests.
 *
 * HOW TO USE:
 *   1. Start the app
 *   2. Open http://localhost:8080/api/swagger-ui.html
 *   3. Call POST /v1/auth/company/login to get a JWT
 *   4. Click "Authorize", paste the access_token, click "Authorize"
 *   5. Now all "Try it out" calls will include the JWT automatically
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Multitenant Architecture API")
                        .version("1.0.0")
                        .description("Industry-level multitenant API with Keycloak authentication")
                        .contact(new Contact().name("Admin").email("admin@multitenant.com"))
                )
                // Adds the "Authorize" button to Swagger UI and the lock icon on endpoints
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .bearerFormat("JWT")
                                        .scheme("bearer")
                                        .description("Enter your JWT token obtained from Keycloak")
                        )
                );
    }
}
