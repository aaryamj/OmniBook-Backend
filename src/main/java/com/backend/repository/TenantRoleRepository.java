package com.backend.repository;

import com.backend.model.TenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRoleRepository extends JpaRepository<TenantRole, Long> {
    List<TenantRole> findByTenantId(Long tenantId);
    Optional<TenantRole> findByTenantIdAndRoleName(Long tenantId, String roleName);
}
