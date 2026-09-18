package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import de.hbrs.seka.wirschiffendas.analysismanagement.application.AnalysisApplicationService;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnalysisController {

    private final AnalysisApplicationService service;

    public AnalysisController(AnalysisApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/analyses")
    public ResponseEntity<AnalysisResponse> start(
            @Valid @RequestBody StartAnalysisRequest request) {
        return ResponseEntity.accepted()
                .body(AnalysisResponse.from(service.start(request.configurationId())));
    }

    @GetMapping("/api/analyses/{analysisId}")
    public AnalysisResponse get(@PathVariable String analysisId) {
        return AnalysisResponse.from(service.get(analysisId));
    }

    @PostMapping("/api/analyses/{analysisId}/algorithms/{algorithm}/retry")
    public ResponseEntity<AnalysisResponse> retry(
            @PathVariable String analysisId,
            @PathVariable AlgorithmName algorithm) {
        return ResponseEntity.accepted()
                .body(AnalysisResponse.from(service.retry(analysisId, algorithm)));
    }

    @PutMapping("/internal/analyses/{analysisId}/algorithms/{algorithm}/status")
    public AnalysisResponse updateStatus(
            @PathVariable String analysisId,
            @PathVariable AlgorithmName algorithm,
            @Valid @RequestBody StatusUpdateRequest request) {
        return AnalysisResponse.from(
                service.updateStatus(analysisId, algorithm, request.status(), request.message()));
    }

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
