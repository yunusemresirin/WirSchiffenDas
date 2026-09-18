package de.hbrs.seka.wirschiffendas.enginemanagement.infrastructure;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP-Client, der Status- und Ergebnis-Updates an den Analysis-Management-Service meldet.
 */
@Component
public class AnalysisManagementClient {

    private final RestClient client;

    public AnalysisManagementClient(
        // RestClient.Builder wird von Spring Boot als Bean bereitgestellt
        RestClient.Builder builder,
        // Basis-URL des Analysis-Management-Service aus der Konfiguration
        @Value("${services.analysis-management.url}") String baseUrl
    ) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    /**
     * Meldet den Status eines Algorithmus (z. B. RUNNING oder FAILED).
     */
    public void reportStatus(String analysisId, String algorithm, String status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);

        // Optionale Meldung nur mitsenden, wenn vorhanden
        if (message != null) body.put("message", message);

        client
            .put() // PUT-Request
            .uri("/internal/analyses/{analysisId}/algorithms/{algorithm}/status", analysisId, algorithm) // Pfadvariablen einsetzen
            .body(body) // Request-Body als JSON serialisieren
            .retrieve() // Request ausführen
            .toBodilessEntity(); // Antwort ohne Body verarbeiten
    }

    /**
     * Meldet das Endergebnis eines Algorithmus (z. B. READY mit Ergebnis OK).
     */
    public void reportResult(String analysisId, String algorithm, String status, String result, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("result", result);

        // Optionale Meldung nur mitsenden, wenn vorhanden
        if (message != null) body.put("message", message);

        client
            .put() // PUT-Request
            .uri("/internal/analyses/{analysisId}/algorithms/{algorithm}/result", analysisId, algorithm) // Pfadvariablen einsetzen
            .body(body) // Request-Body als JSON serialisieren
            .retrieve() // Request ausführen
            .toBodilessEntity(); // Antwort ohne Body verarbeiten
    }

}
