package de.hbrs.seka.wirschiffendas.fluid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class FluidAnalysisServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FluidAnalysisServiceApplication.class, args);
    }
}
