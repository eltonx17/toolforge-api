package com.tooling.toolforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class CORSConfig {

    @Bean
    public WebFluxConfigurer corsConfigurer() {
        return new WebFluxConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Allow CORS for all endpoints
                        // Adjust the allowed origin to your Angular dev server URL
                        .allowedOrigins("http://localhost:4200", "https://tool-forge.vercel.app", "http://192.168.0.109")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allow necessary methods
                        .allowedHeaders("*") // Allow all headers
                        .allowCredentials(false)
                        .exposedHeaders("Session-Id"); // Set to true if you need credentials/cookies
            }
        };
    }
}