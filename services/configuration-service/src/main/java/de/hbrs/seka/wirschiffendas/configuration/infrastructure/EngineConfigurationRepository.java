package de.hbrs.seka.wirschiffendas.configuration.infrastructure;

import de.hbrs.seka.wirschiffendas.configuration.domain.EngineConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineConfigurationRepository extends JpaRepository<EngineConfiguration, String> {
}
