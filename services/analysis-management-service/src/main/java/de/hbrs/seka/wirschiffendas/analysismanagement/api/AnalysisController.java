package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import de.hbrs.seka.wirschiffendas.analysismanagement.application.AnalysisApplicationService;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST-Endpunkte zum Starten, Abfragen und Wiederholen von Analysen.
 */
@RestController
public class AnalysisController {

    private final AnalysisApplicationService service;

    public AnalysisController(AnalysisApplicationService service) {
        this.service = service;
    }

    /**
     * Startet eine neue Analyse. Antwortet mit 202 Accepted, da die Verarbeitung asynchron im Hintergrund läuft.
     */
    @PostMapping("/api/analyses")
    public ResponseEntity<AnalysisResponse> start(
            @Valid @RequestBody StartAnalysisRequest request) {
        return ResponseEntity
            .accepted() // 202 Accepted: Anfrage angenommen, Verarbeitung läuft asynchron weiter
            .body(AnalysisResponse.from(service.start(request.configurationId())));
    }

    /**
     * Liefert den aktuellen Zustand einer Analyse.
     */
    @GetMapping("/api/analyses/{analysisId}")
    public AnalysisResponse get(@PathVariable String analysisId) {
        return AnalysisResponse.from(service.get(analysisId));
    }

    /**
     * Wiederholt einen fehlgeschlagenen Algorithmus. Antwortet mit 202 Accepted, da die Verarbeitung asynchron läuft.
     */
    @PostMapping("/api/analyses/{analysisId}/algorithms/{algorithm}/retry")
    public ResponseEntity<AnalysisResponse> retry(
            @PathVariable String analysisId,
            @PathVariable AlgorithmName algorithm) {
        return ResponseEntity
            .accepted() // 202 Accepted: Anfrage angenommen, Verarbeitung läuft asynchron weiter
            .body(AnalysisResponse.from(service.retry(analysisId, algorithm)));
    }

    /**
     * Interner Recovery-Hook: setzt nach erfolgreichem Circuit-Breaker-Probe
     * alle durch Nichterreichbarkeit fehlgeschlagenen Läufe an diesem Algorithmus fort.
     */
    @PostMapping("/internal/analyses/recover/{algorithm}")
    public ResponseEntity<Void> recover(@PathVariable AlgorithmName algorithm) {
        service.resumeRecoverableFailures(algorithm);
        return ResponseEntity.accepted().build();
    }

    /**
     * Interner Callback: aktualisiert den Status eines Algorithmus.
     */
    @PutMapping("/internal/analyses/{analysisId}/algorithms/{algorithm}/status")
    public AnalysisResponse updateStatus(
            @PathVariable String analysisId,
            @PathVariable AlgorithmName algorithm,
            @Valid @RequestBody StatusUpdateRequest request) {
        return AnalysisResponse.from(
                service.updateStatus(analysisId, algorithm, request.status(), request.message()));
    }

    /**
     * Interner Callback: aktualisiert das Ergebnis eines Algorithmus.
     */
    @PutMapping("/internal/analyses/{analysisId}/algorithms/{algorithm}/result")
    public AnalysisResponse updateResult(
            @PathVariable String analysisId,
            @PathVariable AlgorithmName algorithm,
            @Valid @RequestBody ResultUpdateRequest request) {
        return AnalysisResponse.from(
                service.updateResult(
                        analysisId,
                        algorithm,
                        request.status(),
                        request.result(),
                        request.message()));
    }
}
