package dev.portfolio.finance.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.portfolio.finance.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}