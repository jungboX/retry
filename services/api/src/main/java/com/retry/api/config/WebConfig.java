package com.retry.api.config;

import java.util.Arrays;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins:http://localhost:5173}") String origins) {
        this.allowedOrigins = Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toArray(String[]::new);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(httpClient));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type", "X-Demo-Session-Id")
            .maxAge(3600);
    }
}
