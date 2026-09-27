package dev.portfolio.finance.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.portfolio.finance.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForPhotoUpdate(@org.springframework.data.repository.query.Param("email") String email);

    /** First lock in every refresh-session transaction: user row, then token/session rows. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByEmail(String email);
}