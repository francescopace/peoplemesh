package org.peoplemesh.scheduling;

import io.quarkus.scheduler.Scheduled;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.service.GdprService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Daily job: permanently removes soft-deleted data after the purge period (30 days).
 */
@ApplicationScoped
public class BackupPurgeJob {

    private static final Logger LOG = Logger.getLogger(BackupPurgeJob.class);

    @Inject
    AppConfig config;

    @Inject
    GdprService gdprService;

    @Scheduled(cron = "0 30 3 * * ?", identity = "backup-purge")
    void purge() {
        Instant threshold = Instant.now().minus(config.retention().purgeDays(), ChronoUnit.DAYS);
        int purged = gdprService.purgeDeletedData(threshold);
        LOG.infof("Backup purge: permanently removed %d records (threshold: %s)", purged, threshold);
    }
}
