package org.peoplemesh.service;

import org.jboss.logging.Logger;
import org.peoplemesh.util.HashUtils;
import org.peoplemesh.domain.model.AuditLogEntry;
import org.peoplemesh.repository.AuditLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes to the audit.audit_log table. Never logs profile content —
 * only action metadata with hashed user IDs and IPs.
 */
@ApplicationScoped
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class);

    @Inject
    NotificationService notificationService;

    @Inject
    AuditLogRepository auditLogRepository;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void log(UUID userId, String action, String toolName, String ipAddress, String metadataJson) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.userIdHash = HashUtils.sha256(userId.toString());
        entry.action = action;
        entry.toolName = toolName;
        entry.timestamp = Instant.now();
        entry.ipHash = ipAddress != null ? HashUtils.sha256(ipAddress) : null;
        entry.metadataJson = metadataJson;
        auditLogRepository.persist(entry);
        try {
            notificationService.notifyAuditEvent(userId, action, toolName, metadataJson);
        } catch (Exception e) {
            LOG.warnf("Notification delivery failed for action=%s: %s", action, e.getMessage());
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void log(UUID userId, String action, String toolName) {
        log(userId, action, toolName, null, null);
    }
}
