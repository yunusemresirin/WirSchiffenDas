package de.hbrs.seka.wirschiffendas.configuration.api;

import de.hbrs.seka.wirschiffendas.configuration.domain.EngineConfiguration;

/**
 * API-Antwort mit den Daten einer Engine-Konfiguration.
 */
public record EngineConfigurationResponse(
        String configurationId,
        String oilSystem,
        String fuelSystem,
        String coolingSystem,
        String electricalSystem,
        String engineManagementSystem) {

    public static EngineConfigurationResponse from(EngineConfiguration configuration) {
        return new EngineConfigurationResponse(
                configuration.getConfigurationId(),
                configuration.getOilSystem(),
                configuration.getFuelSystem(),
                configuration.getCoolingSystem(),
                configuration.getElectricalSystem(),
                configuration.getEngineManagementSystem());
    }
}
