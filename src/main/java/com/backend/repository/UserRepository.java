package com.backend.repository;

import com.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM users WHERE email = :email LIMIT 1", nativeQuery = true)
    Optional<User> findByEmailIgnoringTenant(@org.springframework.data.repository.query.Param("email") String email);
    
    Optional<User> findByPhone(String phone);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM users WHERE phone = :phone LIMIT 1", nativeQuery = true)
    Optional<User> findByPhoneIgnoringTenant(@org.springframework.data.repository.query.Param("phone") String phone);

    Optional<User> findByEmailOrPhone(String email, String phone);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM users WHERE reset_password_token = :token LIMIT 1", nativeQuery = true)
    Optional<User> findByResetPasswordTokenIgnoringTenant(@org.springframework.data.repository.query.Param("token") String token);

    boolean existsByRole(String role);
    
    java.util.List<User> findByTenantIdAndRole(Long tenantId, String role);
    java.util.List<User> findByTenantId(Long tenantId);

    @org.springframework.data.jpa.repository.Query(value = "SELECT email FROM users WHERE email IS NOT NULL AND email != ''", nativeQuery = true)
    java.util.List<String> findAllUserEmailsIgnoringTenant();
}
