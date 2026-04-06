package org.peoplemesh.service;

import io.quarkus.vault.VaultTransitSecretEngine;
import io.quarkus.vault.transit.KeyConfigRequestDetail;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Field-level encryption via Vault Transit engine.
 * Each user gets a dedicated encryption key (aes256-gcm96) named "user-{uuid}".
 * If Vault is unavailable, operations fail loudly — no silent plaintext fallback.
 */
@ApplicationScoped
public class EncryptionService {

    private static final Logger LOG = Logger.getLogger(EncryptionService.class);
    private static final String KEY_PREFIX = "user-";

    @Inject
    VaultTransitSecretEngine transit;

    public String encrypt(UUID userId, String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        String keyName = keyName(userId);
        return transit.encrypt(keyName, plaintext);
    }

    public String decrypt(UUID userId, String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        String keyName = keyName(userId);
        return transit.decrypt(keyName, ciphertext).asString();
    }

    public void createKeyIfAbsent(UUID userId) {
        String keyName = keyName(userId);
        try {
            transit.createKey(keyName, null);
        } catch (Exception e) {
            LOG.debugf("Key %s may already exist: %s", keyName, e.getMessage());
        }
    }

    public void deleteKey(UUID userId) {
        String keyName = keyName(userId);
        try {
            KeyConfigRequestDetail config = new KeyConfigRequestDetail();
            config.setDeletionAllowed(true);
            transit.updateKeyConfiguration(keyName, config);
            transit.deleteKey(keyName);
        } catch (Exception e) {
            LOG.warnf("Could not delete transit key %s: %s", keyName, e.getMessage());
        }
    }

    private String keyName(UUID userId) {
        return KEY_PREFIX + userId.toString();
    }
}
