package com.backend.repository;

import com.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    
    Optional<User> findByPhone(String phone);

    Optional<User> findByEmailOrPhone(String email, String phone);

    boolean existsByRole(String role);
}
