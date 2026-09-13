package com.backend.repository;

import com.backend.model.DailySettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailySettlementRepository extends JpaRepository<DailySettlement, Long> {

    Optional<DailySettlement> findByTenantIdAndSettlementDate(Long tenantId, LocalDate settlementDate);

    List<DailySettlement> findByTenantIdOrderBySettlementDateDesc(Long tenantId);

    List<DailySettlement> findByTenantIdAndSettlementDateBetweenOrderBySettlementDateDesc(Long tenantId, LocalDate startDate, LocalDate endDate);

    List<DailySettlement> findAllByOrderBySettlementDateDesc();

    List<DailySettlement> findBySettlementDateBetweenOrderBySettlementDateDesc(LocalDate startDate, LocalDate endDate);

    List<DailySettlement> findBySettlementStatusOrderBySettlementDateDesc(String settlementStatus);
}
