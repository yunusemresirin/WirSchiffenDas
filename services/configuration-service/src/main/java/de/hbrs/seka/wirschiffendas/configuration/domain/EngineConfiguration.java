package de.hbrs.seka.wirschiffendas.configuration.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Engine-Konfiguration mit den Eingabewerten für die Analyse-Kette.
 */
@Entity
@Table(name = "engine_configurations")
public class EngineConfiguration {

    @Id
    private String configurationId;

    private String oilSystem;
    private String fuelSystem;
    private String coolingSystem;
    private String electricalSystem;
    private String engineManagementSystem;

    protected EngineConfiguration() {
    }

    public EngineConfiguration(
            String configurationId,
            String oilSystem,
            String fuelSystem,
            String coolingSystem,
            String electricalSystem,
            String engineManagementSystem) {
        this.configurationId = configurationId;
        this.oilSystem = oilSystem;
        this.fuelSystem = fuelSystem;
        this.coolingSystem = coolingSystem;
        this.electricalSystem = electricalSystem;
        this.engineManagementSystem = engineManagementSystem;
    }

    public String getConfigurationId() { return configurationId; }
    public String getOilSystem() { return oilSystem; }
    public String getFuelSystem() { return fuelSystem; }
    public String getCoolingSystem() { return coolingSystem; }
    public String getElectricalSystem() { return electricalSystem; }
    public String getEngineManagementSystem() { return engineManagementSystem; }
}
