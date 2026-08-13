package com.callsagents.backend.voice.repo;

import com.callsagents.backend.voice.domain.VoiceProviderType;
import com.callsagents.backend.voice.domain.VoiceCall;
import com.callsagents.backend.voice.domain.VoiceCallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoiceCallRepository extends JpaRepository<VoiceCall, UUID> {
    List<VoiceCall> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<VoiceCall> findAllByLeadIdOrderByCreatedAtDesc(UUID leadId);
    Optional<VoiceCall> findByProviderAndProviderCallId(VoiceProviderType provider, String providerCallId);
    long countByStatus(VoiceCallStatus status);
}
