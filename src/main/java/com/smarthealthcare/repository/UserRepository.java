package com.smarthealthcare.repository;

import com.smarthealthcare.entity.Role;
import com.smarthealthcare.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    boolean existsByEmailIgnoreCase(String email);
}
