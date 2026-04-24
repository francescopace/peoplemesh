package org.peoplemesh.service;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.util.HashUtils;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JdbcConsentTokenStoreIT {

    @Inject
    ConsentTokenStore tokenStore;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void clearConsumedTokenTableBeforeEach() throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("DELETE FROM identity.consumed_consent_token")) {
            ps.executeUpdate();
        }
    }

    @Test
    void tryConsume_whenHashIsNew_returnsTrue() {
        String hash = HashUtils.sha256("token-" + UUID.randomUUID());
        Instant expires = Instant.now().plusSeconds(600);

        assertTrue(tokenStore.tryConsume(hash, expires));
    }

    @Test
    void tryConsume_whenHashAlreadyConsumed_returnsFalse() {
        String hash = HashUtils.sha256("token-" + UUID.randomUUID());
        Instant expires = Instant.now().plusSeconds(600);

        assertTrue(tokenStore.tryConsume(hash, expires));
        assertFalse(tokenStore.tryConsume(hash, expires));
    }

    @Test
    void release_afterConsume_allowsFutureConsume() {
        String hash = HashUtils.sha256("token-" + UUID.randomUUID());
        Instant expires = Instant.now().plusSeconds(600);

        assertTrue(tokenStore.tryConsume(hash, expires));
        tokenStore.release(hash);
        assertTrue(tokenStore.tryConsume(hash, expires));
    }

    @Test
    void release_whenHashMissing_doesNotThrow() {
        assertDoesNotThrow(() ->
                tokenStore.release(HashUtils.sha256("never-consumed")));
    }

    @Test
    void cleanupQuery_purgesOnlyExpiredTokens() throws SQLException {
        String expiredHash = HashUtils.sha256("expired-" + UUID.randomUUID());
        String validHash = HashUtils.sha256("valid-" + UUID.randomUUID());

        tokenStore.tryConsume(expiredHash, Instant.now().minusSeconds(1));
        tokenStore.tryConsume(validHash, Instant.now().plusSeconds(600));

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "DELETE FROM identity.consumed_consent_token WHERE expires_at < now()")) {
            int purged = ps.executeUpdate();
            assertEquals(1, purged);
        }

        assertFalse(tokenStore.tryConsume(validHash, Instant.now().plusSeconds(600)));
        assertTrue(tokenStore.tryConsume(expiredHash, Instant.now().plusSeconds(600)));
    }
}
