package de.hbrs.seka.wirschiffendas.fluid.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
public class AnalysisManagementClient {

    private final RestClient client;

    public AnalysisManagementClient(RestClient.Builder builder, @Value("${services.analysis-management.url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public void reportStatus(String analysisId, String algorithm, String status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        if (message != null) body.put("message", message);
        client.put().uri("/internal/analyses/{analysisId}/algorithms/{algorithm}/status", analysisId, algorithm)
                .body(body).retrieve().toBodilessEntity();
    }

    public void reportResult(String analysisId, String algorithm, String status, String result, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("result", result);
        if (message != null) body.put("message", message);
        client.put().uri("/internal/analyses/{analysisId}/algorithms/{algorithm}/result", analysisId, algorithm)
                .body(body).retrieve().toBodilessEntity();
    }
}
