package com.backend.repository;

import com.backend.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    List<UserSession> findByUserIdOrderByLoginAtDesc(Long userId);
    Optional<UserSession> findByToken(String token);
}
