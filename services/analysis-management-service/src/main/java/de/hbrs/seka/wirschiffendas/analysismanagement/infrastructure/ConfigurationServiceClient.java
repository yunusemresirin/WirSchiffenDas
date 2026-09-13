package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ConfigurationServiceClient {

    private final RestClient client;

    public ConfigurationServiceClient(
            RestClient.Builder builder,
            @Value("${services.configuration.url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public ConfigurationSnapshot get(String configurationId) {
        return client.get()
                .uri("/api/configurations/{configurationId}", configurationId)
                .retrieve()
                .body(ConfigurationSnapshot.class);
    }
}
