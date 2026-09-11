package com.backend.repository;

import com.backend.model.ProviderProfile;
import com.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, Long> {
    Optional<ProviderProfile> findByUser(User user);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProviderProfile p WHERE p.user.id = :userId")
    Optional<ProviderProfile> findByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProviderProfile p WHERE p.user.tenant.id = :tenantId")
    java.util.List<ProviderProfile> findByTenantId(@org.springframework.data.repository.query.Param("tenantId") Long tenantId);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM ProviderProfile p JOIN ProviderService ps ON ps.providerProfile = p WHERE p.user.tenant.id = :tenantId AND ps.serviceName = :serviceName AND ps.isActive = true")
    java.util.List<ProviderProfile> findByTenantIdAndServiceName(@org.springframework.data.repository.query.Param("tenantId") Long tenantId, @org.springframework.data.repository.query.Param("serviceName") String serviceName);
}
