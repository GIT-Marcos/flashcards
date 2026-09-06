package com.cards.api.integration.data;

import com.cards.api.config.AuditConfig;
import com.cards.api.entity.StudySession;
import com.cards.api.entity.User;
import com.cards.api.infraestructure.config.JpaTestConfig;
import com.cards.api.infraestructure.mother.StudySessionMother;
import com.cards.api.infraestructure.mother.UserMother;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.repo.StudySessionRepository;
import com.cards.api.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
@DisplayName("StudySession @Version optimistic locking")
class StudySessionVersionDataIntegrationTest {

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private StudySession savedSession;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
            UserMother.createMinimal(
                UserMother.uniqueUsername("versionsession", System.currentTimeMillis()),
                UserMother.uniqueEmail("versionsession", System.currentTimeMillis())
            )
        );
        savedSession = studySessionRepository.saveAndFlush(
            StudySessionMother.createForUser(user)
        );
        entityManager.clear();
    }

    @Test
    @DisplayName("should have version = 0 after initial persist")
    void versionShouldBeZeroAfterPersist() {
        StudySession reloaded = studySessionRepository.findById(savedSession.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isZero();
    }

    @Test
    @DisplayName("should increment version after updateMetrics")
    void versionShouldIncrementAfterUpdate() {
        StudySession session = studySessionRepository.findById(savedSession.getId()).orElseThrow();
        session.updateMetrics(3, Instant.now());
        studySessionRepository.saveAndFlush(session);

        StudySession reloaded = studySessionRepository.findById(savedSession.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isOne();
    }
}
