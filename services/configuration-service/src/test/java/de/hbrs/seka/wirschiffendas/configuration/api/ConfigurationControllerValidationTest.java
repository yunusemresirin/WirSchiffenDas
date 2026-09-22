package de.hbrs.seka.wirschiffendas.configuration.api;

import de.hbrs.seka.wirschiffendas.configuration.application.ConfigurationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigurationController.class)
class ConfigurationControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigurationApplicationService service;

    @Test
    void rejectsUnknownVariantWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/configurations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oilSystem": "STANDARD",
                                  "fuelSystem": "PREMIUM",
                                  "coolingSystem": "UNKNOWN",
                                  "electricalSystem": "PREMIUM",
                                  "engineManagementSystem": "ADVANCED"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
