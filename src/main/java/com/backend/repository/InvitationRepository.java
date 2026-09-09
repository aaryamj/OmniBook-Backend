package com.backend.repository;

import com.backend.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import com.backend.model.Tenant;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByToken(String token);
    
    java.util.List<Invitation> findByTenantId(Long tenantId);

    long countByTenantAndRoleAndUsedFalse(Tenant tenant, String role);
}
