package de.hbrs.seka.wirschiffendas.electrical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class ElectricalAnalysisServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ElectricalAnalysisServiceApplication.class, args);
    }
}
