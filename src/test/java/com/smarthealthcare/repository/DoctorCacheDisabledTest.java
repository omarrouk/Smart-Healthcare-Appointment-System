package com.smarthealthcare.repository;

import com.smarthealthcare.config.DoctorCacheConfig;
import com.smarthealthcare.entity.Doctor;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DoctorCacheConfig.class)
class DoctorCacheDisabledTest {

    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory factory;

    @Test
    void disablingSecondLevelCachePreservesDoctorReads() {
        var user = new User(UUID.randomUUID() + "@example.com", "test-only-hash", Role.DOCTOR);
        entityManager.persist(user);
        var doctor = new Doctor(user, "Uncached Doctor", "Test");
        entityManager.persist(doctor);
        entityManager.flush();
        entityManager.clear();
        SessionFactory sessions = factory.unwrap(SessionFactory.class);
        sessions.getStatistics().clear();

        assertThat(entityManager.find(Doctor.class, doctor.getId()).getName()).isEqualTo("Uncached Doctor");
        entityManager.clear();
        assertThat(entityManager.find(Doctor.class, doctor.getId()).getName()).isEqualTo("Uncached Doctor");

        assertThat(sessions.getSessionFactoryOptions().isSecondLevelCacheEnabled()).isFalse();
        assertThat(sessions.getStatistics().getSecondLevelCacheHitCount()).isZero();
        assertThat(sessions.getStatistics().getPrepareStatementCount()).isEqualTo(2);
    }
}
