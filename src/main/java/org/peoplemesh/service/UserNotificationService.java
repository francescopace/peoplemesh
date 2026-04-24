package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.StringUtils;
import org.peoplemesh.util.HashUtils;
import org.peoplemesh.domain.dto.UserNotificationDto;
import org.peoplemesh.domain.model.AuditLogEntry;
import org.peoplemesh.repository.AuditLogRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class UserNotificationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_METADATA_LENGTH = 240;
    private static final Set<String> SUPPRESSED_ACTIONS = Set.of(
            "MATCHES_SEARCHED"
    );

    @Inject
    AppConfig appConfig;

    @Inject
    AuditLogRepository auditLogRepository;

    public List<UserNotificationDto> getRecentNotifications(UUID userId, Integer limit) {
        String userHash = hashUserId(userId);
        int pageSize = normalizeLimit(limit);

        return fetchRecentEntries(userHash, pageSize).stream()
                .map(this::toDto)
                .toList();
    }

    List<AuditLogEntry> fetchRecentEntries(String userHash, int pageSize) {
        return auditLogRepository.findRecentNotifications(userHash, SUPPRESSED_ACTIONS, pageSize);
    }

    String hashUserId(UUID userId) {
        return HashUtils.sha256(userId.toString());
    }

    private UserNotificationDto toDto(AuditLogEntry entry) {
        return new UserNotificationDto(
                entry.id,
                buildSubject(entry.action),
                entry.action,
                entry.toolName,
                StringUtils.abbreviate(entry.metadataJson, MAX_METADATA_LENGTH),
                entry.timestamp
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String buildSubject(String action) {
        return StringUtils.buildNotificationSubject(appConfig.notification().subjectPrefix(), action);
    }

}
