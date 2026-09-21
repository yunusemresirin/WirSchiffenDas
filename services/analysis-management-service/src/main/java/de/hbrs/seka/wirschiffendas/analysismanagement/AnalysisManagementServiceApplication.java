package de.hbrs.seka.wirschiffendas.analysismanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AnalysisManagementServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalysisManagementServiceApplication.class, args);
    }
}
