package com.retry.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DataSourceConfigurationTest {
    @Test
    void requiresTlsForAzurePostgres() {
        String url = "jdbc:postgresql://retry-test.postgres.database.azure.com:5432/retry";

        assertEquals(url + "?sslmode=require", DataSourceConfiguration.requireAzurePostgresTls(url));
    }

    @Test
    void addsTlsWhenAzurePostgresUrlAlreadyHasOptions() {
        String url = "jdbc:postgresql://retry-test.postgres.database.azure.com:5432/retry?connectTimeout=10";

        assertEquals(
            url + "&sslmode=require",
            DataSourceConfiguration.requireAzurePostgresTls(url)
        );
    }

    @Test
    void preservesStrongerAzurePostgresTlsModes() {
        String url = "jdbc:postgresql://retry-test.postgres.database.azure.com:5432/retry?sslmode=verify-full";

        assertEquals(url, DataSourceConfiguration.requireAzurePostgresTls(url));
    }

    @Test
    void rejectsAzurePostgresUrlsConfiguredWithoutTls() {
        String url = "jdbc:postgresql://retry-test.postgres.database.azure.com:5432/retry?sslmode=disable";

        assertThrows(
            IllegalArgumentException.class,
            () -> DataSourceConfiguration.requireAzurePostgresTls(url)
        );
    }

    @Test
    void rejectsAnyWeakModeWhenAnAzureUrlContainsMultipleSslModes() {
        String url = "jdbc:postgresql://retry-test.postgres.database.azure.com:5432/retry"
            + "?sslmode=require&sslmode=disable";

        assertThrows(
            IllegalArgumentException.class,
            () -> DataSourceConfiguration.requireAzurePostgresTls(url)
        );
    }

    @Test
    void preservesSslModeFromPostgresConnectionUri() {
        Map<String, String> properties = DataSourceConfiguration.parseConnectionString(
            "postgresql://demo:fake-password@retry-test.postgres.database.azure.com/retry?sslmode=verify-full"
        );

        assertEquals("verify-full", properties.get("sslmode"));
    }

    @Test
    void leavesLocalPostgresAndH2UrlsUnchanged() {
        String localPostgresUrl = "jdbc:postgresql://localhost:5432/retry";
        String h2Url = "jdbc:h2:mem:retry";

        assertEquals(localPostgresUrl, DataSourceConfiguration.requireAzurePostgresTls(localPostgresUrl));
        assertEquals(h2Url, DataSourceConfiguration.requireAzurePostgresTls(h2Url));
    }
}
