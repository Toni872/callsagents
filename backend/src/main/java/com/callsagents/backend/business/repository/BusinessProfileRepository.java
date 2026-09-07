package com.callsagents.backend.business.repository;

import com.callsagents.backend.business.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {
    Optional<BusinessProfile> findByUserId(UUID userId);
    Optional<BusinessProfile> findByWhatsappNumber(String whatsappNumber);

    /**
     * Look up the owning user id by the real business profile {@code id} column.
     * {@code findById} cannot be used here: the entity uses {@code @MapsId}, so
     * JPA maps the entity id onto {@code user_id} and the table's independent
     * {@code id} column (created by {@code gen_random_uuid()}) is not the PK.
     */
    @Query(value = "SELECT user_id FROM business_profiles WHERE id = :profileId", nativeQuery = true)
    Optional<UUID> findUserIdByProfileId(@Param("profileId") UUID profileId);
}
