package org.peoplemesh.service;

import java.time.Instant;

/**
 * Pluggable store for tracking single-use consent tokens.
 * Implementations must guarantee atomic "consume-once" semantics.
 */
public interface ConsentTokenStore {

    /**
     * Atomically marks a token as consumed.
     *
     * @param tokenHash SHA-256 hash of the full token string
     * @param expiresAt when the consumed record can be safely purged
     * @return {@code true} if this was the first consumption (token is valid),
     *         {@code false} if the token was already consumed
     */
    boolean tryConsume(String tokenHash, Instant expiresAt);

    /**
     * Removes a previously consumed token so it can be retried
     * (rollback scenario when profile submission fails after consumption).
     */
    void release(String tokenHash);
}
