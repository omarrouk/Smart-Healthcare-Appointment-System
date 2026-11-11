package com.smarthealthcare.repository;

import com.smarthealthcare.config.DoctorCacheConfig;
import com.smarthealthcare.entity.Doctor;
import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.cache.CacheManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DoctorCacheConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DoctorCacheIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory factory;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DoctorRepository doctors;
    @Autowired private CacheManager cacheManager;

    private TransactionTemplate transactions;
    private Statistics statistics;
    private Long doctorId;
    private Long userId;
    private String specialty;

    @BeforeEach
    void commitUniqueFixture() {
        transactions = new TransactionTemplate(transactionManager);
        statistics = factory.unwrap(SessionFactory.class).getStatistics();
        transactions.executeWithoutResult(transaction -> {
            var user = new User(UUID.randomUUID() + "@example.com", "test-only-hash", Role.DOCTOR);
            entityManager.persist(user);
            var doctor = new Doctor(user, "Cache Test Doctor", "Cache-" + UUID.randomUUID());
            entityManager.persist(doctor);
            doctorId = doctor.getId();
            userId = user.getId();
            specialty = doctor.getSpecialty();
        });
        factory.getCache().evict(Doctor.class, doctorId);
        statistics.clear();
    }

    @AfterEach
    void removeOnlyCommittedFixture() {
        transactions.executeWithoutResult(transaction -> {
            Doctor doctor = entityManager.find(Doctor.class, doctorId);
            if (doctor != null) entityManager.remove(doctor);
            entityManager.flush();
            User user = entityManager.find(User.class, userId);
            if (user != null) entityManager.remove(user);
        });
    }

    @Test
    void firstLevelCacheReusesTheSameManagedEntityWithinATransaction() {
        transactions.executeWithoutResult(transaction -> {
            Doctor first = entityManager.find(Doctor.class, doctorId);
            Doctor second = entityManager.find(Doctor.class, doctorId);
            assertThat(second).isSameAs(first);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        });
    }

    @Test
    void secondLevelCacheReusesStateAcrossSeparateTransactions() {
        Doctor first = loadDoctor();
        Doctor second = loadDoctor();

        assertThat(second).isNotSameAs(first);
        assertThat(second.getName()).isEqualTo(first.getName());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getSecondLevelCacheHitCount()).isEqualTo(1);
        assertThat(cacheManager.getCacheNames()).containsExactly("doctors");
    }

    @Test
    void committedUpdateRefreshesCachedDoctorState() {
        loadDoctor();
        transactions.executeWithoutResult(transaction ->
                entityManager.find(Doctor.class, doctorId).updateDetails("Updated Cache Doctor", "Updated Specialty"));

        Doctor updated = loadDoctor();
        assertThat(updated.getName()).isEqualTo("Updated Cache Doctor");
        assertThat(updated.getSpecialty()).isEqualTo("Updated Specialty");
    }

    @Test
    void rollbackDoesNotPublishUncommittedDoctorDetails() {
        loadDoctor();
        transactions.executeWithoutResult(transaction -> {
            entityManager.find(Doctor.class, doctorId).updateDetails("Uncommitted Name", "Uncommitted Specialty");
            entityManager.flush();
            transaction.setRollbackOnly();
        });

        Doctor restored = loadDoctor();
        assertThat(restored.getName()).isEqualTo("Cache Test Doctor");
        assertThat(restored.getSpecialty()).isEqualTo(specialty);
    }

    @Test
    void committedDeletionInvalidatesCachedDoctorState() {
        loadDoctor();
        assertThat(factory.getCache().contains(Doctor.class, doctorId)).isTrue();
        transactions.executeWithoutResult(transaction -> entityManager.remove(entityManager.find(Doctor.class, doctorId)));

        // READ_WRITE may retain an unreadable lock marker after deletion.
        assertThat(loadDoctor()).isNull();
        assertThat(loadDoctor()).isNull();
    }

    @Test
    void accountStateIsNotCachedWithTheDoctor() {
        loadDoctor();
        transactions.executeWithoutResult(transaction -> entityManager.find(User.class, userId).setEnabled(false));

        transactions.executeWithoutResult(transaction -> {
            Doctor cachedDoctor = entityManager.find(Doctor.class, doctorId);
            assertThat(cachedDoctor.getUser().isEnabled()).isFalse();
        });
        assertThat(factory.getCache().contains(User.class, userId)).isFalse();
    }

    @Test
    void doctorSearchQueriesStillReadTheDatabase() {
        transactions.executeWithoutResult(transaction -> assertThat(doctors.findBySpecialtyIgnoreCase(specialty)).hasSize(1));
        transactions.executeWithoutResult(transaction -> assertThat(doctors.findBySpecialtyIgnoreCase(specialty)).hasSize(1));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getQueryCacheHitCount()).isZero();
    }

    private Doctor loadDoctor() {
        return transactions.execute(transaction -> entityManager.find(Doctor.class, doctorId));
    }
}
