package org.peoplemesh.scheduling;

import io.quarkus.scheduler.Scheduled;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Daily job: marks profiles inactive for 12+ months as candidates for deletion.
 * In MVP, profiles inactive beyond the retention period are auto-deleted
 * (in production, a notification step would precede deletion).
 */
@ApplicationScoped
public class RetentionJob {

    private static final Logger LOG = Logger.getLogger(RetentionJob.class);

    @Inject
    AppConfig config;

    @Inject
    GdprService gdprService;

    @Inject
    ProfileService profileService;

    @Scheduled(cron = "0 0 3 * * ?", identity = "retention-check")
    void checkRetention() {
        Instant threshold = Instant.now().minus(config.retention().inactiveMonths() * 30L, ChronoUnit.DAYS);
        List<UUID> inactiveUsers = gdprService.findInactiveUserIds(threshold);

        LOG.infof("Retention check: found %d inactive profiles (threshold: %s)",
                inactiveUsers.size(), threshold);

        for (UUID userId : inactiveUsers) {
            profileService.deleteProfile(userId);
            LOG.infof("Auto-deleted inactive profile for user %s", userId);
        }
    }
}
