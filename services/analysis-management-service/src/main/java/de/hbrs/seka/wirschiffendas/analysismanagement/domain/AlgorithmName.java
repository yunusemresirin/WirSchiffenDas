package de.hbrs.seka.wirschiffendas.analysismanagement.domain;

import java.util.Set;

public enum AlgorithmName {
    FLUID("oilSystem", "fuelSystem"),
    THERMAL("coolingSystem"),
    ELECTRICAL("electricalSystem"),
    ENGINE_MANAGEMENT("engineManagementSystem");

    private final Set<String> equipmentNames;
    AlgorithmName(String... equipmentNames) { this.equipmentNames = Set.of(equipmentNames); }
    public Set<String> equipmentNames() { return equipmentNames; }
}