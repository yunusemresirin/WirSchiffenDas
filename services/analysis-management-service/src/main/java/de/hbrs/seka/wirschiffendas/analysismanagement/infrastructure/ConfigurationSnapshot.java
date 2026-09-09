package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

public record ConfigurationSnapshot(
        String configurationId,
        String oilSystem,
        String fuelSystem,
        String coolingSystem,
        String electricalSystem,
        String engineManagementSystem) {
}
