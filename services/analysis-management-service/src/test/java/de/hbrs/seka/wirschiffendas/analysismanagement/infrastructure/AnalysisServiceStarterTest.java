package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisServiceStarterTest {

    @ParameterizedTest
    @CsvSource({
            "FLUID, analysisServiceStarterFluid",
            "THERMAL, analysisServiceStarterThermal",
            "ELECTRICAL, analysisServiceStarterElectrical",
            "ENGINE_MANAGEMENT, analysisServiceStarterEngineManagement"
    })
    void usesDedicatedBreakerPerTarget(AlgorithmName algorithm, String expectedName) {
        assertThat(AnalysisServiceStarter.breakerName(algorithm)).isEqualTo(expectedName);
    }
}
