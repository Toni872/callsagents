package com.callsagents.backend.common.audit;

import com.callsagents.backend.audit.AuditLogRepository;
import com.callsagents.backend.audit.entity.AuditAction;
import com.callsagents.backend.audit.entity.AuditLog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String entityType, UUID entityId, AuditAction action, Map<String, Object> changes) {
        try {
            AuditLog entry = AuditLog.builder()
                .userId(userId)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .changesJson(changes)
                .build();
            repository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to write audit log for {} {} action {}: {}", entityType, entityId, action, ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String entityType, UUID entityId, AuditAction action, String changesJson) {
        log(userId, entityType, entityId, action, parseJson(changesJson));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String entityType, UUID entityId, AuditAction action) {
        log(userId, entityType, entityId, action, (Map<String, Object>) null);
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse audit changes JSON; storing as null. raw={}", json, ex);
            return null;
        }
    }
}
