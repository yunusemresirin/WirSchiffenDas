package de.hbrs.seka.wirschiffendas.configuration.application;

import de.hbrs.seka.wirschiffendas.configuration.api.CreateConfigurationRequest;
import de.hbrs.seka.wirschiffendas.configuration.domain.EngineConfiguration;
import de.hbrs.seka.wirschiffendas.configuration.infrastructure.EngineConfigurationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Anwendungsdienst zum Anlegen und Laden von Engine-Konfigurationen.
 */
@Service
public class ConfigurationApplicationService {

    private final EngineConfigurationRepository repository;

    public ConfigurationApplicationService(EngineConfigurationRepository repository) {
        this.repository = repository;
    }

    /**
     * Legt eine neue Konfiguration mit generierter ID an.
     */
    public EngineConfiguration create(CreateConfigurationRequest request) {
        EngineConfiguration configuration = new EngineConfiguration(
                "C-" + UUID.randomUUID(),
                request.oilSystem(),
                request.fuelSystem(),
                request.coolingSystem(),
                request.electricalSystem(),
                request.engineManagementSystem());

        return repository.save(configuration);
    }

    /**
     * Lädt eine Konfiguration oder wirft 404, wenn sie nicht existiert.
     */
    public EngineConfiguration get(String configurationId) {
        return repository.findById(configurationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Configuration not found"));
    }
}
