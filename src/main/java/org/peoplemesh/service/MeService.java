package org.peoplemesh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.peoplemesh.domain.dto.MeIdentityResponse;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;
import org.peoplemesh.util.JsonMergePatchUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class MeService {

    @Inject
    NodeRepository nodeRepository;

    @Inject
    UserIdentityRepository userIdentityRepository;

    @Inject
    EntitlementService entitlementService;

    @Inject
    ProfileService profileService;

    @Inject
    ConsentService consentService;

    @Inject
    AuditService auditService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Validator validator;

    public Optional<MeIdentityResponse> resolveIdentityPayload(SecurityIdentity identity) {
        UUID userId = identity.<UUID>getAttribute("pm.userId");
        String provider = identity.<String>getAttribute("pm.provider");
        String displayName = identity.<String>getAttribute("pm.displayName");
        if (userId != null) {
            return nodeRepository.findById(userId)
                    .filter(node -> node.nodeType == NodeType.USER)
                    .map(node -> buildPayload(userId, provider, displayName, node));
        }
        if (identity.isAnonymous()) {
            return Optional.empty();
        }
        String subject = identity.getPrincipal().getName();
        Optional<UserIdentity> linkedIdentity = userIdentityRepository.findByOauthSubject(subject);
        return linkedIdentity
                .flatMap(user -> nodeRepository.findById(user.nodeId)
                        .map(node -> buildPayload(user.nodeId, user.oauthProvider, null, node)));
    }

    public Optional<UUID> findCurrentUserNodeId(UUID userId) {
        return nodeRepository.findPublishedUserNode(userId).map(node -> node.id);
    }

    public ProfileSchema resolveProfile(UUID userId, ProfileSchema updates) {
        profileService.upsertProfile(userId, updates);
        return profileService.getProfile(userId).orElse(updates);
    }

    public ProfileSchema patchProfile(UUID userId, JsonNode mergePatch) {
        if (mergePatch == null) {
            throw new ValidationBusinessException("Missing merge patch payload");
        }

        JsonNode currentProfileNode;
        Optional<ProfileSchema> currentProfile = profileService.getProfile(userId);
        if (currentProfile.isPresent()) {
            currentProfileNode = objectMapper.convertValue(currentProfile.get(), JsonNode.class);
        } else {
            currentProfileNode = objectMapper.createObjectNode();
        }
        JsonNode mergedNode = JsonMergePatchUtils.apply(currentProfileNode, mergePatch);

        if (mergedNode == null || !mergedNode.isObject()) {
            throw new ValidationBusinessException("Merge patch must resolve to a JSON object");
        }

        ProfileSchema mergedSchema = toProfileSchema(mergedNode);
        validatePatchedSchema(mergedSchema);

        profileService.upsertProfileReplace(userId, mergedSchema);
        return profileService.getProfile(userId).orElse(mergedSchema);
    }

    public void applySelectiveImport(UUID userId, ProfileSchema selectedFields, String source) {
        if (source == null || source.isBlank()) {
            throw new ValidationBusinessException("Missing source parameter");
        }
        profileService.applySelectiveImport(userId, selectedFields, source.trim());
    }

    public void grantConsent(UUID userId, String scope, Collection<String> allowedScopes, String clientIpHash) {
        validateConsentScope(scope, allowedScopes);
        consentService.recordConsent(userId, scope, clientIpHash);
        auditService.log(userId, "CONSENT_GRANTED", "privacy_consent");
    }

    public void revokeConsent(UUID userId, String scope, Collection<String> allowedScopes) {
        validateConsentScope(scope, allowedScopes);
        consentService.revokeConsent(userId, scope);
        auditService.log(userId, "CONSENT_REVOKED", "privacy_consent");
    }

    public List<String> getActiveConsentScopes(UUID userId) {
        return consentService.getActiveScopes(userId);
    }

    public Map<String, Object> getConsentView(UUID userId) {
        return Map.of(
                "scopes", ConsentService.DEFAULT_CONSENT_SCOPES,
                "active", getActiveConsentScopes(userId));
    }

    public void validateConsentScope(String scope, Collection<String> allowedScopes) {
        if (scope == null || scope.isBlank() || !allowedScopes.contains(scope)) {
            throw new ValidationBusinessException("Invalid consent scope: " + scope);
        }
    }

    private ProfileSchema toProfileSchema(JsonNode mergedNode) {
        try {
            return objectMapper.treeToValue(mergedNode, ProfileSchema.class);
        } catch (JsonProcessingException e) {
            throw new ValidationBusinessException("Invalid merge patch payload");
        }
    }

    private void validatePatchedSchema(ProfileSchema schema) {
        Set<ConstraintViolation<ProfileSchema>> violations = validator.validate(schema);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private MeIdentityResponse buildPayload(UUID nodeId, String provider, String displayName, MeshNode node) {
        String photoUrl = readPhotoUrl(node);
        String safeDisplayName = normalizeString(displayName);
        MeIdentityResponse.IdentityInfo identityInfo = new MeIdentityResponse.IdentityInfo(
                safeDisplayName,
                photoUrl
        );
        MeIdentityResponse.SessionInfo sessionInfo = new MeIdentityResponse.SessionInfo(
                nodeId,
                normalizeString(provider),
                node.externalId != null && !node.externalId.isBlank(),
                node.id,
                new MeIdentityResponse.EntitlementsInfo(entitlementService.isAdmin(nodeId))
        );
        return new MeIdentityResponse(identityInfo, sessionInfo);
    }

    private static String readPhotoUrl(MeshNode node) {
        if (node == null || node.structuredData == null) {
            return null;
        }
        Object raw = node.structuredData.get("avatar_url");
        if (!(raw instanceof String value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
