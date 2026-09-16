package de.hbrs.seka.wirschiffendas.thermal.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class AnalysisManagementClient {
    private final RestClient client;
    private final int maxAttempts;
    private final Duration backoff;

    public AnalysisManagementClient(RestClient.Builder builder,
                                    @Value("${services.analysis-management.url}") String baseUrl,
                                    @Value("${worker.callbacks.max-attempts:3}") int maxAttempts,
                                    @Value("${worker.callbacks.backoff:PT0.2S}") Duration backoff) {
        if (maxAttempts < 1 || maxAttempts > 10 || backoff.isNegative() || backoff.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("Callback attempts must be 1..10 and backoff 0..10 seconds");
        }
        this.client = builder.clone().baseUrl(baseUrl).build();
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    public void reportStatus(String analysisId, String attemptId, String algorithm, String status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("attemptId", attemptId);
        body.put("status", status);
        if (message != null) body.put("message", message);
        deliver(() -> client.put().uri("/internal/analyses/{analysisId}/algorithms/{algorithm}/status", analysisId, algorithm)
                .body(body).retrieve().toBodilessEntity());
    }

    public void reportResult(String analysisId, String attemptId, String algorithm, String status,
                             String result, String message, Map<String, String> equipmentResults) {
        Map<String, Object> body = new HashMap<>();
        body.put("attemptId", attemptId);
        body.put("status", status);
        body.put("result", result);
        body.put("equipmentResults", equipmentResults);
        if (message != null) body.put("message", message);
        deliver(() -> client.put().uri("/internal/analyses/{analysisId}/algorithms/{algorithm}/result", analysisId, algorithm)
                .body(body).retrieve().toBodilessEntity());
    }

    private void deliver(Runnable callback) {
        for (int attempt = 1; ; attempt++) {
            try {
                callback.run();
                return;
            } catch (RestClientResponseException exception) {
                int code = exception.getStatusCode().value();
                boolean transientFailure = code == 500 || code == 502 || code == 503 || code == 504;
                if (!transientFailure || attempt >= maxAttempts) throw exception;
            } catch (ResourceAccessException exception) {
                if (attempt >= maxAttempts) throw exception;
            }
            try {
                Thread.sleep(backoff.multipliedBy(attempt).toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RestClientException("Callback delivery interrupted", exception);
            }
        }
    }
}
