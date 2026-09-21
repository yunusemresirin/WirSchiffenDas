package de.hbrs.seka.wirschiffendas.configuration.domain;

/**
 * Kontrollierte Varianten für die simulierte Optional-Equipment-Konfiguration.
 *
 * STANDARD, PREMIUM und ADVANCED sind gültige Demo-Varianten.
 * INVALID ist absichtlich speicherbar, damit die Analyse-Kette einen
 * fachlich ungültigen Wert erkennen und mit FAILED abbrechen kann.
 */
public enum ConfigurationVariant {
    STANDARD,
    PREMIUM,
    ADVANCED,
    INVALID
}
