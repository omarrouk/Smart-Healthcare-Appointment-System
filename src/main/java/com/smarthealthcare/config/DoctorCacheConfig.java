package com.smarthealthcare.config;

import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import java.time.Duration;
import java.util.OptionalLong;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.jpa.properties.hibernate.cache.use_second_level_cache",
        havingValue = "true", matchIfMissing = true)
public class DoctorCacheConfig {

    private static final Logger logger = LoggerFactory.getLogger("healthcare.cache");

    @Bean(destroyMethod = "close")
    CacheManager doctorCacheManager(@Value("${app.cache.doctors.max-entries:1000}") long maxEntries,
                                    @Value("${app.cache.doctors.ttl-seconds:300}") long ttlSeconds) {
        if (maxEntries <= 0 || ttlSeconds <= 0) {
            throw new IllegalArgumentException("Doctor cache capacity and TTL must be positive");
        }
        var configuration = new CaffeineConfiguration<Object, Object>();
        configuration.setStoreByValue(false);
        configuration.setMaximumSize(OptionalLong.of(maxEntries));
        configuration.setExpireAfterWrite(OptionalLong.of(Duration.ofSeconds(ttlSeconds).toNanos()));

        // A provider per context keeps separate SessionFactories from closing each other's cache.
        CacheManager manager = new CaffeineCachingProvider().getCacheManager();
        try {
            manager.createCache("doctors", configuration);
            logger.info("doctor_cache_initialized maxEntries={} ttlSeconds={}", maxEntries, ttlSeconds);
            return manager;
        } catch (RuntimeException exception) {
            manager.close();
            throw exception;
        }
    }

    @Bean
    HibernatePropertiesCustomizer doctorCacheProperties(CacheManager doctorCacheManager) {
        return properties -> {
            properties.put("hibernate.javax.cache.cache_manager", doctorCacheManager);
            properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.jcache.internal.JCacheRegionFactory");
            properties.put("hibernate.javax.cache.missing_cache_strategy", "fail");
        };
    }
}
