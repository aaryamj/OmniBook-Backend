package com.backend.repository;

import com.backend.model.ProviderSchedule;
import com.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderScheduleRepository extends JpaRepository<ProviderSchedule, Long> {
    List<ProviderSchedule> findByProviderOrderByDayOfWeek(User provider);
    Optional<ProviderSchedule> findByProviderAndDayOfWeek(User provider, String dayOfWeek);
}
