package de.hbrs.seka.wirschiffendas.configuration.application;

import de.hbrs.seka.wirschiffendas.configuration.api.CreateConfigurationRequest;
import de.hbrs.seka.wirschiffendas.configuration.domain.ConfigurationVariant;
import de.hbrs.seka.wirschiffendas.configuration.domain.EngineConfiguration;
import de.hbrs.seka.wirschiffendas.configuration.infrastructure.EngineConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationApplicationServiceTest {

    @Mock
    private EngineConfigurationRepository repository;

    @InjectMocks
    private ConfigurationApplicationService service;

    @Test
    void createGeneratesIdAndPersistsConfiguration() {
        when(repository.save(any(EngineConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateConfigurationRequest request = new CreateConfigurationRequest(
                ConfigurationVariant.STANDARD,
                ConfigurationVariant.PREMIUM,
                ConfigurationVariant.STANDARD,
                ConfigurationVariant.PREMIUM,
                ConfigurationVariant.ADVANCED);

        EngineConfiguration created = service.create(request);

        assertThat(created.getConfigurationId()).startsWith("C-");
        assertThat(created.getOilSystem()).isEqualTo("STANDARD");
        assertThat(created.getFuelSystem()).isEqualTo("PREMIUM");
        assertThat(created.getCoolingSystem()).isEqualTo("STANDARD");
        assertThat(created.getElectricalSystem()).isEqualTo("PREMIUM");
        assertThat(created.getEngineManagementSystem()).isEqualTo("ADVANCED");
        verify(repository).save(any(EngineConfiguration.class));
    }
    @Test
    void invalidVariantIsPersistedForAnalysisDemo() {
        when(repository.save(any(EngineConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateConfigurationRequest request = new CreateConfigurationRequest(
                ConfigurationVariant.INVALID,
                ConfigurationVariant.PREMIUM,
                ConfigurationVariant.STANDARD,
                ConfigurationVariant.PREMIUM,
                ConfigurationVariant.ADVANCED);

        EngineConfiguration created = service.create(request);

        assertThat(created.getOilSystem()).isEqualTo("INVALID");
        verify(repository).save(any(EngineConfiguration.class));
    }

}
