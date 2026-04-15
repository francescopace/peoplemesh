package org.peoplemesh.service;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

@ApplicationScoped
public class JdbcConsentTokenStore implements ConsentTokenStore {

    private static final Logger LOG = Logger.getLogger(JdbcConsentTokenStore.class);

    private static final String SQL_CONSUME =
            "INSERT INTO identity.consumed_consent_token (token_hash, expires_at) " +
            "VALUES (?, ?) ON CONFLICT (token_hash) DO NOTHING";

    private static final String SQL_RELEASE =
            "DELETE FROM identity.consumed_consent_token WHERE token_hash = ?";

    private static final String SQL_PURGE_EXPIRED =
            "DELETE FROM identity.consumed_consent_token WHERE expires_at < now()";

    @Inject
    AgroalDataSource dataSource;

    @Override
    public boolean tryConsume(String tokenHash, Instant expiresAt) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(SQL_CONSUME)) {
            ps.setString(1, tokenHash);
            ps.setTimestamp(2, Timestamp.from(expiresAt));
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to consume consent token", e);
        }
    }

    @Override
    public void release(String tokenHash) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(SQL_RELEASE)) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to release consent token", e);
        }
    }

    /**
     * Removes expired consumed-consent-token rows.
     * @return number of purged rows
     */
    public int purgeExpired() {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(SQL_PURGE_EXPIRED)) {
            int purged = ps.executeUpdate();
            if (purged > 0) {
                LOG.infof("Consent token cleanup: removed %d expired entries", purged);
            }
            return purged;
        } catch (SQLException e) {
            throw new RuntimeException("Consent token cleanup failed", e);
        }
    }
}
