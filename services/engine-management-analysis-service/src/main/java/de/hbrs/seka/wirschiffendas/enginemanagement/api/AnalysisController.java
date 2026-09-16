package de.hbrs.seka.wirschiffendas.enginemanagement.api;

import de.hbrs.seka.wirschiffendas.enginemanagement.application.AnalysisWorker;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/analyses")
public class AnalysisController {
    private final AnalysisWorker worker;

    public AnalysisController(AnalysisWorker worker) {
        this.worker = worker;
    }

    @PostMapping
    public ResponseEntity<Void> start(@Valid @RequestBody AnalysisCommand command) {
        worker.execute(command);
        return ResponseEntity.accepted().build();
    }
}
