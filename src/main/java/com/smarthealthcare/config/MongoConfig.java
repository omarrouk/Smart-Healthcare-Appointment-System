package com.smarthealthcare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "mongoDateTimeProvider")
public class MongoConfig {

    @Bean
    DateTimeProvider mongoDateTimeProvider() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
