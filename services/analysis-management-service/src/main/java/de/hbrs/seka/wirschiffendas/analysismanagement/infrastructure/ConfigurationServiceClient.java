package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP-Client, der Engine-Konfigurationen vom Configuration-Service lädt.
 */
@Component
public class ConfigurationServiceClient {

    private final RestClient client;

    public ConfigurationServiceClient(
            RestClient.Builder builder,
            @Value("${services.configuration.url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    /**
     * Lädt eine Konfiguration anhand ihrer ID.
     */
    public ConfigurationSnapshot get(String configurationId) {
        return client
                .get() // GET-Request
                .uri("/api/configurations/{configurationId}", configurationId) // Pfadvariable einsetzen
                .retrieve() // Request ausführen
                .body(ConfigurationSnapshot.class); // Antwort als Snapshot deserialisieren
    }
}
