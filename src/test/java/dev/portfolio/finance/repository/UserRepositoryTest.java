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
    private UserRepository userRepository;

    @Test
    void shouldFindUserByEmail() {

        User user = new User(
                "Test",
                "User",
                "test@example.com",
                "hashed-password"
        );

        userRepository.save(user);

        var result =
                userRepository.findByEmail("test@example.com");

        assertThat(result)
                .isPresent();

        assertThat(result.get().getEmail())
                .isEqualTo("test@example.com");

        assertThat(result.get().getFirstName())
                .isEqualTo("Test");

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