package com.retry.api.config;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
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
                String sslMode = properties.getOrDefault("sslmode", properties.get("ssl mode"));
                if (sslMode != null && !sslMode.isBlank()) {
                    url += "?sslmode=" + URLEncoder.encode(sslMode, StandardCharsets.UTF_8);
                }
                username = required(properties, "username", "user id", "userid");
                password = required(properties, "password", "pwd");
            }
        }

        url = requireAzurePostgresTls(url);

        return DataSourceBuilder.create()
            .url(url)
            .username(username)
            .password(password)
            .build();
    }

    static String requireAzurePostgresTls(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            return jdbcUrl;
        }

        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String host = uri.getHost();
        if (host == null || !host.toLowerCase(Locale.ROOT).endsWith(".postgres.database.azure.com")) {
            return jdbcUrl;
        }

        var sslModes = Arrays.stream(uri.getRawQuery() == null ? new String[0] : uri.getRawQuery().split("&"))
            .map(parameter -> parameter.split("=", 2))
            .filter(parameter -> parameter.length == 2 && parameter[0].equalsIgnoreCase("sslmode"))
            .map(parameter -> URLDecoder.decode(parameter[1], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT))
            .toList();

        if (sslModes.stream().anyMatch(mode ->
            !mode.equals("require") && !mode.equals("verify-ca") && !mode.equals("verify-full")
        )) {
            throw new IllegalArgumentException(
                "Azure PostgreSQL connections must use TLS (sslmode=require or stronger)."
            );
        }
        if (!sslModes.isEmpty()) {
            return jdbcUrl;
        }

        String query = uri.getRawQuery();
        return jdbcUrl + (query == null || query.isEmpty() ? "?" : "&") + "sslmode=require";
    }

    static Map<String, String> parseConnectionString(String value) {
        if (value.startsWith("postgres://") || value.startsWith("postgresql://")) {
            URI uri = URI.create(value);
            String[] credentials = uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":", 2);
            String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            if (credentials.length < 2 || database.isBlank()) {
                throw new IllegalArgumentException("Invalid PostgreSQL connection URI.");
            }
            Map<String, String> properties = new HashMap<>(Map.of(
                "host", uri.getHost(),
                "port", Integer.toString(uri.getPort() < 0 ? 5432 : uri.getPort()),
                "database", decode(database),
                "username", decode(credentials[0]),
                "password", decode(credentials[1])
            ));
            if (uri.getRawQuery() != null) {
                Arrays.stream(uri.getRawQuery().split("&"))
                    .map(parameter -> parameter.split("=", 2))
                    .filter(parameter -> parameter.length == 2 && parameter[0].equalsIgnoreCase("sslmode"))
                    .findFirst()
                    .ifPresent(parameter -> properties.put(
                        "sslmode",
                        URLDecoder.decode(parameter[1], StandardCharsets.UTF_8)
                    ));
            }
            return properties;
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
