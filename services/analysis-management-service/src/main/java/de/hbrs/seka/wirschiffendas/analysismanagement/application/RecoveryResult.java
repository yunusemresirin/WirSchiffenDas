package de.hbrs.seka.wirschiffendas.analysismanagement.application;

/**
 * Ergebnis eines automatischen Recovery-Zyklus.
 *
 * @param resumed Anzahl erfolgreich neu gestarteter Läufe
 * @param remaining Anzahl weiterhin technisch fehlgeschlagener Läufe
 */
public record RecoveryResult(int resumed, int remaining) {
}
