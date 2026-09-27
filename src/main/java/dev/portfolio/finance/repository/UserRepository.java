package dev.portfolio.finance.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.portfolio.finance.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForPhotoUpdate(@org.springframework.data.repository.query.Param("email") String email);

    boolean existsByEmail(String email);
}