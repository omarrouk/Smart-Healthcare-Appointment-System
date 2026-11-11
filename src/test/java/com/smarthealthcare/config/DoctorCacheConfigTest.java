package com.smarthealthcare.config;

import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.cache.CacheManager;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorCacheConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DoctorCacheConfig.class);

    @Test
    void configuresABoundedSingletonAndClosesItWithTheContext() {
        var reference = new AtomicReference<CacheManager>();
        runner.withPropertyValues("app.cache.doctors.max-entries=7", "app.cache.doctors.ttl-seconds=60").run(context -> {
            CacheManager manager = context.getBean(CacheManager.class);
            reference.set(manager);
            assertThat(context.getBean(CacheManager.class)).isSameAs(manager);
            assertThat(manager.isClosed()).isFalse();
            CaffeineConfiguration<?, ?> configuration = manager.getCache("doctors")
                    .getConfiguration(CaffeineConfiguration.class);
            assertThat(configuration.getMaximumSize().orElseThrow()).isEqualTo(7);
            assertThat(configuration.getExpireAfterWrite().orElseThrow()).isEqualTo(TimeUnit.SECONDS.toNanos(60));
            assertThat(configuration.isStoreByValue()).isFalse();
            var properties = new HashMap<String, Object>();
            context.getBean(HibernatePropertiesCustomizer.class).customize(properties);
            assertThat(properties.get("hibernate.javax.cache.cache_manager")).isSameAs(manager);
            assertThat(properties.get("hibernate.javax.cache.missing_cache_strategy")).isEqualTo("fail");
        });
        assertThat(reference.get().isClosed()).isTrue();
    }

    @Test
    void independentContextsDoNotShareOrCloseEachOthersManagers() {
        runner.run(first -> {
            CacheManager manager = first.getBean(CacheManager.class);
            runner.run(second -> assertThat(second.getBean(CacheManager.class)).isNotSameAs(manager));
            assertThat(manager.isClosed()).isFalse();
            assertThat(manager.getCache("doctors")).isNotNull();
        });
    }

    @Test
    void disabledCachingDoesNotCreateACacheManager() {
        runner.withPropertyValues("spring.jpa.properties.hibernate.cache.use_second_level_cache=false")
                .run(context -> assertThat(context).doesNotHaveBean(CacheManager.class));
    }

    @Test
    void rejectsInvalidCapacityAndExpiry() {
        for (String property : new String[]{"app.cache.doctors.max-entries=0", "app.cache.doctors.ttl-seconds=-1"}) {
            runner.withPropertyValues(property).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            });
        }
    }
}
