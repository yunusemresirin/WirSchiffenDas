package de.hbrs.seka.wirschiffendas.configuration.api;

import de.hbrs.seka.wirschiffendas.configuration.domain.ConfigurationVariant;
import jakarta.validation.constraints.NotNull;

/**
 * Anfrage zum Anlegen einer neuen Engine-Konfiguration.
 *
 * INVALID ist ein bewusst zugelassener Demonstrationswert. Er wird persistiert,
 * aber erst vom zuständigen Analyse-Algorithmus als fachlich ungültig bewertet.
 */
public record CreateConfigurationRequest(
        @NotNull ConfigurationVariant oilSystem,
        @NotNull ConfigurationVariant fuelSystem,
        @NotNull ConfigurationVariant coolingSystem,
        @NotNull ConfigurationVariant electricalSystem,
        @NotNull ConfigurationVariant engineManagementSystem) {
}
