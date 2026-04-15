package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.StringUtils;
import org.peoplemesh.domain.model.MeshNode;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);

    @Inject
    AppConfig appConfig;

    public void notifyAuditEvent(UUID userId, String action, String toolName, String metadataJson) {
        if (userId == null || action == null || action.isBlank()) {
            return;
        }
        if (!appConfig.notification().enabled()) {
            return;
        }

        MeshNode node = MeshNode.<MeshNode>findByIdOptional(userId).orElse(null);
        if (node == null || node.externalId == null || node.externalId.isBlank()) {
            LOG.debugf("Notification skipped (no email): userId=%s action=%s", userId, action);
            return;
        }

        String email = node.externalId;

        String subject = buildSubject(action);
        String body = buildBody(userId, action, toolName, metadataJson);

        if (appConfig.notification().dryRun()) {
            LOG.infof("Notification dry-run: to=%s subject=%s body=%s",
                    maskEmail(email), subject, StringUtils.abbreviate(body, 500));
            return;
        }

        LOG.infof("Notification event queued: to=%s subject=%s", maskEmail(email), subject);
    }

    private String buildSubject(String action) {
        return StringUtils.buildNotificationSubject(appConfig.notification().subjectPrefix(), action);
    }

    private static String buildBody(UUID userId, String action, String toolName, String metadataJson) {
        return """
                Event: %s
                Tool: %s
                Time: %s
                User: %s
                Metadata: %s
                """.formatted(
                action,
                toolName != null ? toolName : "-",
                Instant.now(),
                userId,
                metadataJson != null ? metadataJson : "-"
        );
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        return local.charAt(0) + "***@" + domain;
    }

}
