package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

/**
 * Kopie einer Engine-Konfiguration, wie sie an die Analyse-Services übergeben wird.
 */
public record ConfigurationSnapshot(
        String configurationId,
        String oilSystem,
        String fuelSystem,
        String coolingSystem,
        String electricalSystem,
        String engineManagementSystem) {
}
