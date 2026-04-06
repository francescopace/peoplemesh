package org.peoplemesh.service;

import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.ConnectionDto;
import org.peoplemesh.domain.dto.ConnectionRequestDto;
import org.peoplemesh.domain.enums.ConnectionStatus;
import org.peoplemesh.domain.model.BlocklistEntry;
import org.peoplemesh.domain.model.Connection;
import org.peoplemesh.domain.model.ConnectionRequest;
import org.peoplemesh.domain.model.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class ConnectionService {

    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[<>\"'&]");

    @Inject
    AppConfig config;

    @Inject
    EncryptionService encryption;

    @Inject
    AuditService audit;

    @Transactional
    public ConnectionRequest requestConnection(UUID fromUserId, UUID toProfileId, String message) {
        UserProfile targetProfile = UserProfile.findById(toProfileId);
        if (targetProfile == null || targetProfile.isDeleted()) {
            throw new IllegalArgumentException("Target profile not found");
        }
        UUID toUserId = targetProfile.userId;

        if (fromUserId.equals(toUserId)) {
            throw new IllegalArgumentException("Cannot connect with yourself");
        }

        if (BlocklistEntry.isBlocked(fromUserId, toUserId)) {
            throw new IllegalArgumentException("Connection not possible");
        }

        if (Connection.existsBetween(fromUserId, toUserId)) {
            throw new IllegalArgumentException("Already connected");
        }

        if (ConnectionRequest.existsBetween(fromUserId, toUserId)) {
            throw new IllegalArgumentException("Connection request already exists");
        }

        long todayCount = ConnectionRequest.countTodayByUser(fromUserId);
        if (todayCount >= config.rateLimit().connections().maxPerDay()) {
            throw new IllegalStateException("Daily connection request limit reached (" +
                    config.rateLimit().connections().maxPerDay() + "/day)");
        }

        ConnectionRequest request = new ConnectionRequest();
        request.fromUserId = fromUserId;
        request.toUserId = toUserId;
        if (message != null && !message.isBlank()) {
            String sanitized = sanitizeMessage(message);
            request.messageEncrypted = encryption.encrypt(toUserId, sanitized);
        }
        request.persist();

        audit.log(fromUserId, "CONNECTION_REQUESTED", "peoplemesh_request_connection");
        return request;
    }

    @Transactional
    public void respondToConnection(UUID userId, UUID requestId, boolean accept) {
        ConnectionRequest request = ConnectionRequest.findByIdAndRecipient(requestId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Connection request not found"));

        if (request.status != ConnectionStatus.PENDING) {
            throw new IllegalStateException("Request already responded to");
        }

        request.status = accept ? ConnectionStatus.ACCEPTED : ConnectionStatus.REJECTED;
        request.respondedAt = Instant.now();
        request.persist();

        if (accept) {
            Connection conn = new Connection();
            conn.userAId = request.fromUserId;
            conn.userBId = request.toUserId;
            conn.persist();
        }

        audit.log(userId, accept ? "CONNECTION_ACCEPTED" : "CONNECTION_REJECTED",
                "peoplemesh_respond_to_connection");
    }

    public List<ConnectionDto> getConnections(UUID userId) {
        return Connection.findByUserId(userId).stream()
                .map(c -> {
                    boolean isUserA = c.userAId.equals(userId);
                    UUID partnerId = isUserA ? c.userBId : c.userAId;
                    String myContact = isUserA
                            ? encryption.decrypt(userId, c.sharedContactAEncrypted)
                            : encryption.decrypt(userId, c.sharedContactBEncrypted);
                    UUID partnerProfileId = UserProfile.findActiveByUserId(partnerId)
                            .map(p -> p.id).orElse(null);
                    return new ConnectionDto(c.id, partnerProfileId, myContact, c.connectedAt);
                })
                .toList();
    }

    public List<ConnectionRequestDto> getPendingRequests(UUID userId) {
        return ConnectionRequest.findPendingForUser(userId).stream()
                .map(r -> {
                    String decryptedMessage = encryption.decrypt(userId, r.messageEncrypted);
                    UUID fromProfileId = UserProfile.findActiveByUserId(r.fromUserId)
                            .map(p -> p.id).orElse(null);
                    return new ConnectionRequestDto(r.id, fromProfileId, decryptedMessage,
                            r.status, r.createdAt);
                })
                .toList();
    }

    private String sanitizeMessage(String message) {
        String truncated = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH)
                : message;
        return SANITIZE_PATTERN.matcher(truncated).replaceAll("");
    }
}
