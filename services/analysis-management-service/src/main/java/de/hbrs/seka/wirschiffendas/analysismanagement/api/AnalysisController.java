package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import de.hbrs.seka.wirschiffendas.analysismanagement.application.AnalysisApplicationService;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.AlgorithmName;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnalysisController {
    private final AnalysisApplicationService service;
    public AnalysisController(AnalysisApplicationService service) { this.service = service; }

    @PostMapping("/api/analyses")
    public ResponseEntity<AnalysisResponse> start(@Valid @RequestBody StartAnalysisRequest request) {
        return ResponseEntity.accepted().body(AnalysisResponse.from(service.start(request.configurationId())));
    }

    @GetMapping("/api/analyses/{analysisId}")
    public AnalysisResponse get(@PathVariable String analysisId) {
        return AnalysisResponse.from(service.get(analysisId));
    }

    @PostMapping("/api/analyses/{analysisId}/algorithms/{algorithm}/retry")
    public ResponseEntity<AnalysisResponse> retry(@PathVariable String analysisId,
                                                 @PathVariable AlgorithmName algorithm) {
        return ResponseEntity.accepted().body(AnalysisResponse.from(service.retry(analysisId, algorithm)));
    }

    @PutMapping("/internal/analyses/{analysisId}/algorithms/{algorithm}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String analysisId, @PathVariable AlgorithmName algorithm,
                                             @Valid @RequestBody StatusUpdateRequest request) {
        service.updateStatus(analysisId, algorithm, request.attemptId(), request.status(), request.message());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/internal/analyses/{analysisId}/algorithms/{algorithm}/result")
    public ResponseEntity<Void> updateResult(@PathVariable String analysisId, @PathVariable AlgorithmName algorithm,
                                             @Valid @RequestBody ResultUpdateRequest request) {
        service.updateResult(analysisId, algorithm, request.attemptId(), request.status(), request.result(),
                request.message(), request.equipmentResults());
        return ResponseEntity.noContent().build();
    }
}