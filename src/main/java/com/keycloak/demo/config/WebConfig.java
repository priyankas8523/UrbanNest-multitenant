package com.keycloak.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration placeholder.
 *
 * Currently empty - extend this to add:
 *   - Custom message converters (e.g., for CSV export)
 *   - Custom argument resolvers (e.g., to inject current tenant as a method parameter)
 *   - Static resource handlers
 *   - Interceptors for logging, rate limiting, etc.
 *
 * CORS is configured in SecurityConfig (not here) because it needs to work
 * with Spring Security's filter chain to properly handle preflight OPTIONS requests.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
