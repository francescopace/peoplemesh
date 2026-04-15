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
class JdbcConsentTokenStoreTest {

    @Inject
    ConsentTokenStore tokenStore;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void cleanTable() throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("DELETE FROM identity.consumed_consent_token")) {
            ps.executeUpdate();
        }
    }

    @Test
    void tryConsume_firstTime_returnsTrue() {
        String hash = HashUtils.sha256("token-" + UUID.randomUUID());
        Instant expires = Instant.now().plusSeconds(600);

        assertTrue(tokenStore.tryConsume(hash, expires));
    }

    @Test
    void tryConsume_secondTime_returnsFalse() {
        String hash = HashUtils.sha256("token-" + UUID.randomUUID());
        Instant expires = Instant.now().plusSeconds(600);

        assertTrue(tokenStore.tryConsume(hash, expires));
        assertFalse(tokenStore.tryConsume(hash, expires));
    }

    @Test
    void release_afterConsume_allowsReConsumption() {
        String hash = HashUtils.sha256("token-" + UUID.randomUUID());
        Instant expires = Instant.now().plusSeconds(600);

        assertTrue(tokenStore.tryConsume(hash, expires));
        tokenStore.release(hash);
        assertTrue(tokenStore.tryConsume(hash, expires));
    }

    @Test
    void release_nonExistentHash_doesNotThrow() {
        assertDoesNotThrow(() ->
                tokenStore.release(HashUtils.sha256("never-consumed")));
    }

    @Test
    void expiredTokens_arePurgedByCleanupQuery() throws SQLException {
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
