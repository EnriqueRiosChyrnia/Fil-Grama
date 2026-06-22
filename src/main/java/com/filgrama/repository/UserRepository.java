package com.filgrama.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.filgrama.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
