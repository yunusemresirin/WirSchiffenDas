package de.hbrs.seka.wirschiffendas.analysismanagement.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
public class RuntimeConfiguration {
    @Bean
    Clock analysisClock() { return Clock.systemUTC(); }

    @Bean
    RestClientCustomizer outgoingTimeouts(
            @Value("${http.client.connect-timeout:2s}") Duration connectTimeout,
            @Value("${http.client.read-timeout:5s}") Duration readTimeout) {
        if (connectTimeout.isZero() || connectTimeout.isNegative() || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("HTTP timeouts must be positive");
        }
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
            builder.requestFactory(factory);
        };
    }
}