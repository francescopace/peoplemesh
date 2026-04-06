package org.peoplemesh.scheduling;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * The matching engine applies temporal decay at query time based on updatedAt.
 * This job exists as a placeholder for any future batch re-indexing needs.
 * Currently it only logs a status check.
 */
@ApplicationScoped
public class ProfileDecayJob {

    private static final Logger LOG = Logger.getLogger(ProfileDecayJob.class);

    @Scheduled(cron = "0 0 4 * * ?", identity = "profile-decay-check")
    void checkDecay() {
        LOG.info("Profile decay check: decay is applied at query time. No batch action needed.");
    }
}
