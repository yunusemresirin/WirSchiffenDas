package de.hbrs.seka.wirschiffendas.electrical.application;

import de.hbrs.seka.wirschiffendas.electrical.api.AnalysisCommand;
import de.hbrs.seka.wirschiffendas.electrical.infrastructure.AnalysisManagementClient;
import de.hbrs.seka.wirschiffendas.electrical.infrastructure.NextServiceClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Führt die elektrische Analyse asynchron aus und stößt danach den nächsten Service an.
 */
@Service
public class AnalysisWorker {
    private static final String ALGORITHM = "ELECTRICAL";
    private final AnalysisManagementClient managementClient;
    private final NextServiceClient nextServiceClient;
    public AnalysisWorker(AnalysisManagementClient managementClient, NextServiceClient nextServiceClient) { this.managementClient = managementClient; this.nextServiceClient = nextServiceClient; }

    /**
     * Meldet RUNNING, prüft die Konfiguration und meldet anschließend das Ergebnis.
     */
    @Async
    public void execute(AnalysisCommand command) {
        managementClient.reportStatus(command.analysisId(), ALGORITHM, "RUNNING", null);
        if (!pause(command.analysisId())) return;
        String electricalSystem = command.configuration().get("electricalSystem");
        boolean ok = valid(electricalSystem);
        String result = ok ? "OK" : "FAILED";
        String message = ok
                ? null
                : "Invalid electrical configuration: electricalSystem must be STANDARD, PREMIUM or ADVANCED";
        managementClient.reportResult(
                command.analysisId(),
                ALGORITHM,
                ok ? "READY" : "FAILED",
                result,
                message);
        // Ungültige Konfiguration wird nicht weiterverarbeitet.
        if (ok) nextServiceClient.startNext(command, result);
    }

    /**
     * Simuliert die Rechenzeit; meldet bei Unterbrechung einen Fehler.
     */
    private boolean pause(String analysisId) {
        try { Thread.sleep(2000); return true; }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            managementClient.reportResult(analysisId, ALGORITHM, "FAILED", "FAILED", "Analysis interrupted");
            return false;
        }
    }

    /**
     * Nur die kontrollierten fachlich gültigen Demo-Varianten werden verarbeitet.
     * INVALID bleibt als absichtlicher Fehlerfall für den Demonstrator verfügbar.
     */
    private boolean valid(String value) {
        return "STANDARD".equalsIgnoreCase(value)
                || "PREMIUM".equalsIgnoreCase(value)
                || "ADVANCED".equalsIgnoreCase(value);
    }
}
