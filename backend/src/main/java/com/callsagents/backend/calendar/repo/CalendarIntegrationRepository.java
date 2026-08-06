package com.callsagents.backend.calendar.repo;

import com.callsagents.backend.calendar.domain.CalendarIntegration;
import com.callsagents.backend.calendar.domain.CalendarProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarIntegrationRepository extends JpaRepository<CalendarIntegration, UUID> {
    Optional<CalendarIntegration> findByUserIdAndProvider(UUID userId, CalendarProviderType provider);
    List<CalendarIntegration> findAllByUserId(UUID userId);
    boolean existsByUserIdAndProvider(UUID userId, CalendarProviderType provider);
}
