package com.retry.api.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AgentClient {
    private final RestClient restClient;

    public AgentClient(
        RestClient.Builder builder,
        @Value("${app.agent.url:http://localhost:8000}") String agentUrl
    ) {
        this.restClient = builder.baseUrl(agentUrl).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(String rawRequest) {
        try {
            Map<String, Object> response = restClient.post()
                .uri("/api/jobs/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("rawRequest", rawRequest))
                .retrieve()
                .body(Map.class);
            if (response == null) {
                throw new AgentUnavailableException("작업 분석 결과가 비어 있습니다. 잠시 후 다시 시도해 주세요.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new AgentUnavailableException("AI 분석 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    public static class AgentUnavailableException extends RuntimeException {
        public AgentUnavailableException(String message) {
            super(message);
        }
    }
}
