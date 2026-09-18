package de.hbrs.seka.wirschiffendas.configuration.api;

import de.hbrs.seka.wirschiffendas.configuration.application.ConfigurationApplicationService;
import de.hbrs.seka.wirschiffendas.configuration.domain.EngineConfiguration;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * REST-Endpunkte zum Anlegen und Laden von Engine-Konfigurationen.
 */
@RestController
@RequestMapping("/api/configurations")
public class ConfigurationController {

    private final ConfigurationApplicationService service;

    public ConfigurationController(ConfigurationApplicationService service) {
        this.service = service;
    }

    /**
     * Legt eine neue Konfiguration an und liefert 201 Created mit Location-Header.
     *
     * <pre>
     * HTTP/1.1 201 Created
     * Location: /api/configurations/abc-123
     * Content-Type: application/json
     *
     * {
     *   "configurationId": "abc-123",
     *   "oilSystem": "...",
     *   "fuelSystem": "...",
     *   ...
     * }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<EngineConfigurationResponse> create(
            @Valid @RequestBody CreateConfigurationRequest request) {
        EngineConfiguration created = service.create(request);
        return ResponseEntity
                // Location-Header zeigt, unter welcher URI die neue Konfiguration abrufbar ist
                .created(URI.create("/api/configurations/" + created.getConfigurationId()))
                .body(EngineConfigurationResponse.from(created));
    }

    /**
     * Liefert eine Konfiguration anhand ihrer ID.
     */
    @GetMapping("/{configurationId}")
    public EngineConfigurationResponse get(@PathVariable String configurationId) {
        return EngineConfigurationResponse.from(service.get(configurationId));
    }
}
