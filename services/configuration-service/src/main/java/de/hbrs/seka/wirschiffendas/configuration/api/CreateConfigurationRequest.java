package de.hbrs.seka.wirschiffendas.configuration.api;

import jakarta.validation.constraints.NotBlank;

public record CreateConfigurationRequest(
        @NotBlank String oilSystem,
        @NotBlank String fuelSystem,
        @NotBlank String coolingSystem,
        @NotBlank String electricalSystem,
        @NotBlank String engineManagementSystem) {
}
