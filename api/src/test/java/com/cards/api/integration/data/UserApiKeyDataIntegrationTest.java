package com.cards.api.integration.data;

import com.cards.api.config.AuditConfig;
import com.cards.api.entity.User;
import com.cards.api.entity.UserApiKey;
import com.cards.api.infraestructure.config.JpaTestConfig;
import com.cards.api.infraestructure.mother.UserMother;
import com.cards.api.integration.TestcontainersConfig;
import com.cards.api.repo.UserApiKeyRepository;
import com.cards.api.repo.UserRepository;
import com.cards.api.util.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfig.class, AuditConfig.class, JpaTestConfig.class})
class UserApiKeyDataIntegrationTest {

    @Autowired
    private UserApiKeyRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final String PLAINTEXT = "sk-proj-abc12345";
    private static final String ANOTHER_PLAINTEXT = "sk-ant-xyz789";

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();

        user1 = userRepository.save(
            UserMother.createMinimal(
                UserMother.uniqueUsername("apikey1", System.currentTimeMillis()),
                UserMother.uniqueEmail("apikey1", System.currentTimeMillis())
            )
        );

        user2 = userRepository.save(
            UserMother.createMinimal(
                UserMother.uniqueUsername("apikey2", System.currentTimeMillis()),
                UserMother.uniqueEmail("apikey2", System.currentTimeMillis())
            )
        );
    }

    @Nested
    @DisplayName("Encryption roundtrip")
    class EncryptionRoundtrip {

        @Test
        @DisplayName("SHOULD encrypt on write and decrypt on read")
        void shouldEncryptAndDecrypt() {
            UserApiKey key = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.OPENAI)
                .keyAlias("...2345")
                .encryptedKey(PLAINTEXT)
                .build();
            key = repository.saveAndFlush(key);
            entityManager.clear();

            UserApiKey found = repository.findById(key.getId()).orElseThrow();

            assertThat(found.getEncryptedKey()).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("SHOULD encrypt different values correctly")
        void shouldEncryptDifferentValues() {
            UserApiKey key1 = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.OPENAI)
                .keyAlias("...2345")
                .encryptedKey(PLAINTEXT)
                .build();
            UserApiKey key2 = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.ANTHROPIC)
                .keyAlias("...6789")
                .encryptedKey(ANOTHER_PLAINTEXT)
                .build();
            key1 = repository.saveAndFlush(key1);
            key2 = repository.saveAndFlush(key2);
            entityManager.clear();

            assertThat(repository.findById(key1.getId()).orElseThrow().getEncryptedKey())
                .isEqualTo(PLAINTEXT);
            assertThat(repository.findById(key2.getId()).orElseThrow().getEncryptedKey())
                .isEqualTo(ANOTHER_PLAINTEXT);
        }
    }

    @Nested
    @DisplayName("Unique constraint")
    class UniqueConstraint {

        @Test
        @DisplayName("SHOULD fail when same user+provider combination")
        void shouldFailOnDuplicateUserAndProvider() {
            UserApiKey key1 = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.OPENAI)
                .keyAlias("...2345")
                .encryptedKey(PLAINTEXT)
                .build();
            repository.saveAndFlush(key1);

            UserApiKey key2 = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.OPENAI)
                .keyAlias("...xxxx")
                .encryptedKey(ANOTHER_PLAINTEXT)
                .build();

            assertThatThrownBy(() -> repository.saveAndFlush(key2))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("SHOULD allow same provider for different users")
        void shouldAllowSameProviderForDifferentUsers() {
            UserApiKey key1 = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.OPENAI)
                .keyAlias("...2345")
                .encryptedKey(PLAINTEXT)
                .build();
            repository.saveAndFlush(key1);

            UserApiKey key2 = UserApiKey.builder()
                .user(user2)
                .provider(AiProvider.OPENAI)
                .keyAlias("...6789")
                .encryptedKey(ANOTHER_PLAINTEXT)
                .build();
            UserApiKey saved = repository.saveAndFlush(key2);

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("SHOULD allow different providers for same user")
        void shouldAllowDifferentProvidersForSameUser() {
            UserApiKey key1 = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.OPENAI)
                .keyAlias("...2345")
                .encryptedKey(PLAINTEXT)
                .build();
            repository.saveAndFlush(key1);

            UserApiKey key2 = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.ANTHROPIC)
                .keyAlias("...6789")
                .encryptedKey(ANOTHER_PLAINTEXT)
                .build();
            UserApiKey saved = repository.saveAndFlush(key2);

            assertThat(saved.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Finder methods")
    class FinderMethods {

        private UserApiKey key1;
        private UserApiKey key2;

        @BeforeEach
        void createKeys() {
            key1 = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.OPENAI)
                .keyAlias("...2345")
                .encryptedKey(PLAINTEXT)
                .build();
            key1 = repository.saveAndFlush(key1);

            key2 = UserApiKey.builder()
                .user(user1)
                .provider(AiProvider.MISTRAL)
                .keyAlias("...6789")
                .encryptedKey(ANOTHER_PLAINTEXT)
                .build();
            key2 = repository.saveAndFlush(key2);

            entityManager.clear();
        }

        @Test
        @DisplayName("findByUserId SHOULD return only keys of that user")
        void shouldFindByUserId() {
            List<UserApiKey> keys = repository.findByUserId(user1.getId());

            assertThat(keys).hasSize(2);
            assertThat(keys).extracting(UserApiKey::getProvider)
                .containsExactlyInAnyOrder(AiProvider.OPENAI, AiProvider.MISTRAL);
        }

        @Test
        @DisplayName("findByUserId SHOULD return empty for user with no keys")
        void shouldReturnEmptyWhenUserHasNoKeys() {
            List<UserApiKey> keys = repository.findByUserId(user2.getId());

            assertThat(keys).isEmpty();
        }

        @Test
        @DisplayName("findByIdAndUserId SHOULD return key when it belongs to user")
        void shouldFindByIdAndUserId() {
            Optional<UserApiKey> found = repository.findByIdAndUserId(key1.getId(), user1.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getProvider()).isEqualTo(AiProvider.OPENAI);
        }

        @Test
        @DisplayName("findByIdAndUserId SHOULD return empty when key belongs to another user")
        void shouldNotFindWhenKeyBelongsToAnotherUser() {
            Optional<UserApiKey> found = repository.findByIdAndUserId(key1.getId(), user2.getId());

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("findByUserIdAndProvider SHOULD return key for matching user and provider")
        void shouldFindByUserIdAndProvider() {
            Optional<UserApiKey> found = repository.findByUserIdAndProvider(user1.getId(), AiProvider.OPENAI);

            assertThat(found).isPresent();
            assertThat(found.get().getEncryptedKey()).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("findByUserIdAndProvider SHOULD return empty when provider does not exist")
        void shouldNotFindByNonExistentProvider() {
            Optional<UserApiKey> found = repository.findByUserIdAndProvider(user1.getId(), AiProvider.GOOGLE);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("existsByUserIdAndProvider SHOULD return true when key exists")
        void shouldExistByUserIdAndProvider() {
            boolean exists = repository.existsByUserIdAndProvider(user1.getId(), AiProvider.OPENAI);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("existsByUserIdAndProvider SHOULD return false when key does not exist")
        void shouldNotExistByUserIdAndProvider() {
            boolean exists = repository.existsByUserIdAndProvider(user1.getId(), AiProvider.GOOGLE);

            assertThat(exists).isFalse();
        }
    }
}
