package de.hbrs.seka.wirschiffendas.thermal.api;

import de.hbrs.seka.wirschiffendas.thermal.application.AnalysisWorker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Interner Endpunkt, über den der Analysis-Management-Service die thermische Analyse startet.
 */
@RestController
@RequestMapping("/internal/analyses")
public class AnalysisController {

    private final AnalysisWorker worker;

    public AnalysisController(AnalysisWorker worker) { this.worker = worker; }

    /**
     * Nimmt einen Analyseauftrag an und startet die asynchrone Verarbeitung.
     */
    @PostMapping
    public ResponseEntity<Void> start(@RequestBody AnalysisCommand command) {
        worker.execute(command);
        
        return ResponseEntity.accepted().build(); // 202 Accepted: Verarbeitung läuft asynchron
    }
}
