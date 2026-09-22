package de.hbrs.seka.wirschiffendas.configuration.api;

import de.hbrs.seka.wirschiffendas.configuration.application.ConfigurationApplicationService;
import de.hbrs.seka.wirschiffendas.configuration.domain.EngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigurationController.class)
class ConfigurationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConfigurationApplicationService service;

    private String body(String variant) {
        return "{\"oilSystem\":\"STANDARD\",\"fuelSystem\":\"PREMIUM\",\"coolingSystem\":\""
                + variant + "\",\"electricalSystem\":\"PREMIUM\",\"engineManagementSystem\":\"ADVANCED\"}";
    }

    @ParameterizedTest
    @ValueSource(strings = {"STANDARD", "PREMIUM", "ADVANCED", "INVALID"})
    void acceptsCatalogIncludingPersistableInvalid(String variant) throws Exception {
        when(service.create(any())).thenAnswer(invocation -> {
            CreateConfigurationRequest request = invocation.getArgument(0);
            return new EngineConfiguration("C-test", request.oilSystem().name(), request.fuelSystem().name(),
                    request.coolingSystem().name(), request.electricalSystem().name(), request.engineManagementSystem().name());
        });
        mvc.perform(post("/api/configurations").contentType(MediaType.APPLICATION_JSON).content(body(variant)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.coolingSystem").value(variant));
    }

    @Test
    void rejectsUnknownVariantBeforePersistence() throws Exception {
        mvc.perform(post("/api/configurations").contentType(MediaType.APPLICATION_JSON).content(body("UNKNOWN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
