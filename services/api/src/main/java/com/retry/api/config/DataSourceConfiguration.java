package com.retry.api.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DataSourceConfiguration {
    @Bean
    DataSource dataSource(Environment environment) {
        String aspireConnection = System.getenv("ConnectionStrings__retry");
        String configuredUrl = environment.getProperty(
            "spring.datasource.url",
            "jdbc:h2:file:./data/retry-demo;DB_CLOSE_ON_EXIT=FALSE"
        );
        String url = configuredUrl;
        String username = environment.getProperty("spring.datasource.username", "sa");
        String password = environment.getProperty("spring.datasource.password", "");

        if (aspireConnection != null && !aspireConnection.isBlank()) {
            if (aspireConnection.startsWith("jdbc:")) {
                url = aspireConnection;
            } else {
                Map<String, String> properties = parseConnectionString(aspireConnection);
                String host = required(properties, "host", "server", "hostname");
                String database = required(properties, "database", "initial catalog");
                String port = properties.getOrDefault("port", "5432");
                url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
                username = required(properties, "username", "user id", "userid");
                password = required(properties, "password", "pwd");
            }
        }

        return DataSourceBuilder.create()
            .url(url)
            .username(username)
            .password(password)
            .build();
    }

    private static Map<String, String> parseConnectionString(String value) {
        if (value.startsWith("postgres://") || value.startsWith("postgresql://")) {
            URI uri = URI.create(value);
            String[] credentials = uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":", 2);
            String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            if (credentials.length < 2 || database.isBlank()) {
                throw new IllegalArgumentException("Invalid PostgreSQL connection URI.");
            }
            return Map.of(
                "host", uri.getHost(),
                "port", Integer.toString(uri.getPort() < 0 ? 5432 : uri.getPort()),
                "database", decode(database),
                "username", decode(credentials[0]),
                "password", decode(credentials[1])
            );
        }
        return Arrays.stream(value.split(";"))
            .map(part -> part.split("=", 2))
            .filter(part -> part.length == 2)
            .collect(Collectors.toMap(
                part -> part[0].trim().toLowerCase(Locale.ROOT),
                part -> part[1].trim(),
                (first, ignored) -> first
            ));
    }

    private static String required(Map<String, String> properties, String... names) {
        for (String name : names) {
            String value = properties.get(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("Missing required PostgreSQL connection property: " + names[0]);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
