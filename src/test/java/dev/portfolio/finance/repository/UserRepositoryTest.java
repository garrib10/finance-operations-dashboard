package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.User;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class UserRepositoryTest {

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsAndClearsPhotoKeyWithoutChangingAccountFields() {
        User user = new User("Photo", "User", "photo@example.com", "hashed-password");
        assertThat(user.getProfilePhotoKey()).isNull();
        user.updatePreferences(dev.portfolio.finance.entity.DateFormatPreference.ISO, 25);
        user.changeProfilePhotoKey(dev.portfolio.finance.support.ProfilePhotoTestSupport.KEY);
        userRepository.saveAndFlush(user);
        entityManager.clear();
        User loaded = userRepository.findByEmail(user.getEmail()).orElseThrow();
        assertThat(loaded.getProfilePhotoKey()).isEqualTo(dev.portfolio.finance.support.ProfilePhotoTestSupport.KEY);
        assertThat(new tools.jackson.databind.json.JsonMapper().writeValueAsString(loaded))
                .doesNotContain("profilePhotoKey", dev.portfolio.finance.support.ProfilePhotoTestSupport.KEY);
        var createdAt = loaded.getCreatedAt();
        loaded.changeProfilePhotoKey(null);
        userRepository.saveAndFlush(loaded);
        entityManager.clear();
        User cleared = userRepository.findByEmail(user.getEmail()).orElseThrow();
        assertThat(cleared.getProfilePhotoKey()).isNull();
        assertThat(cleared.getFirstName()).isEqualTo("Photo");
        assertThat(cleared.getLastName()).isEqualTo("User");
        assertThat(cleared.getDisplayName()).isEqualTo("Photo User");
        assertThat(cleared.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(cleared.getDateFormat()).isEqualTo(dev.portfolio.finance.entity.DateFormatPreference.ISO);
        assertThat(cleared.getTransactionPageSize()).isEqualTo(25);
        assertThat(cleared.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void shouldFindUserByEmail() {

        User user = new User(
                "Test",
                "User",
                "test@example.com",
                "hashed-password"
        );

        userRepository.saveAndFlush(user);
        entityManager.clear();

        var result =
                userRepository.findByEmail("test@example.com");

        assertThat(result)
                .isPresent();

        assertThat(result.get().getEmail())
                .isEqualTo("test@example.com");

        assertThat(result.get().getFirstName())
                .isEqualTo("Test");

        assertThat(result.get().getDisplayName()).isEqualTo("Test User");
        assertThat(result.get().getDateFormat()).isEqualTo(dev.portfolio.finance.entity.DateFormatPreference.MEDIUM);
        assertThat(result.get().getTransactionPageSize()).isEqualTo(10);

        assertThat(result.get().getLastName())
                .isEqualTo("User");
    }

    @Test
    void shouldReturnEmptyWhenEmailDoesNotExist() {

        var result =
                userRepository.findByEmail("missing@example.com");

        assertThat(result)
                .isEmpty();
    }

    @Test
    void shouldReturnTrueWhenEmailExists() {

        User user = new User(
                "Test",
                "User",
                "test@example.com",
                "hashed-password"
        );

        userRepository.save(user);

        boolean exists =
                userRepository.existsByEmail(
                        "test@example.com"
                );

        assertThat(exists)
                .isTrue();
    }

    @Test
    void shouldReturnFalseWhenEmailDoesNotExist() {

        boolean exists =
                userRepository.existsByEmail(
                        "missing@example.com"
                );

        assertThat(exists)
                .isFalse();
    }
}