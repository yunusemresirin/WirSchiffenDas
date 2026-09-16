package de.hbrs.seka.wirschiffendas.analysismanagement.api;

import de.hbrs.seka.wirschiffendas.analysismanagement.application.AnalysisApplicationService;
import de.hbrs.seka.wirschiffendas.analysismanagement.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnalysisControllerTest {
    private AnalysisApplicationService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AnalysisApplicationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AnalysisController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void resultCallbackCarriesAttemptAndEquipmentAndAcknowledgesWithoutResponseBody() throws Exception {
        mvc.perform(put("/internal/analyses/A-1/algorithms/FLUID/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"attemptId":"attempt-1","status":"READY","result":"OK",
                         "equipmentResults":{"oilSystem":"OK","fuelSystem":"OK"}}
                        """))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).updateResult("A-1", AlgorithmName.FLUID, "attempt-1", AnalysisStatus.READY,
                AnalysisResult.OK, null, Map.of("oilSystem", AnalysisResult.OK, "fuelSystem", AnalysisResult.OK));
    }

    @Test
    void missingAttemptIsRejectedBeforeMutation() throws Exception {
        mvc.perform(put("/internal/analyses/A-1/algorithms/FLUID/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RUNNING\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void legacyRetryHasAnActionableErrorBody() throws Exception {
        when(service.retry("A-legacy", AlgorithmName.FLUID)).thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "Legacy analysis: create a new analysis for this configuration"));
        mvc.perform(post("/api/analyses/A-legacy/algorithms/FLUID/retry"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(
                        "Legacy analysis: create a new analysis for this configuration"));
    }
}
