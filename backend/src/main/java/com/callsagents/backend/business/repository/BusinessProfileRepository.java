package com.callsagents.backend.business.repository;

import com.callsagents.backend.business.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {
    Optional<BusinessProfile> findByUserId(UUID userId);
    Optional<BusinessProfile> findByWhatsappNumber(String whatsappNumber);
}
