package org.peoplemesh.service;

import org.peoplemesh.config.HashUtils;
import org.peoplemesh.domain.model.AuditLogEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes to the audit.audit_log table. Never logs profile content —
 * only action metadata with hashed user IDs and IPs.
 */
@ApplicationScoped
public class AuditService {

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void log(UUID userId, String action, String toolName, String ipAddress, String metadataJson) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.userIdHash = HashUtils.sha256(userId.toString());
        entry.action = action;
        entry.toolName = toolName;
        entry.timestamp = Instant.now();
        entry.ipHash = ipAddress != null ? HashUtils.sha256(ipAddress) : null;
        entry.metadataJson = metadataJson;
        entry.persist();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void log(UUID userId, String action, String toolName) {
        log(userId, action, toolName, null, null);
    }
}
