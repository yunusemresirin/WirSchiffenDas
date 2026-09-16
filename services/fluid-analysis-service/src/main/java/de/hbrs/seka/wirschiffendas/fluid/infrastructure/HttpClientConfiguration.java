package de.hbrs.seka.wirschiffendas.fluid.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class HttpClientConfiguration {
    @Bean
    RestClientCustomizer workerHttpTimeouts(
            @Value("${http.client.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${http.client.read-timeout:PT5S}") Duration readTimeout) {
        if (connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("HTTP timeouts must be positive");
        }
        return builder -> {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
            builder.requestFactory(factory);
        };
    }
}
