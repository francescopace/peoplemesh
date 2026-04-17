package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;
import org.peoplemesh.service.OAuthTokenExchangeService.GitHubEnrichedResult;
import org.peoplemesh.service.OAuthTokenExchangeService.OidcSubject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class OAuthCallbackService {

    public static final Set<String> DEFAULT_CONSENT_SCOPES = Set.of(
            "professional_matching", "embedding_processing");

    @Inject
    OAuthTokenExchangeService tokenExchangeService;

    @Inject
    ProfileService profileService;

    @Inject
    ConsentService consentService;

    @Inject
    AppConfig appConfig;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    UserIdentityRepository userIdentityRepository;

    public record LoginResult(UUID userId, String displayName, boolean isNewUser) {}

    @Transactional
    public LoginResult handleLogin(String provider, OidcSubject subject) {
        String email = normalizeEmail(subject.email());

        UserIdentity existingIdentity = findIdentityByOauth(provider, subject.subject()).orElse(null);
        MeshNode userNode;
        boolean isNewUser;

        if (existingIdentity != null) {
            userNode = findNodeById(existingIdentity.nodeId).orElse(null);
            if (userNode == null) {
                userNode = createUserNode(email);
                existingIdentity.nodeId = userNode.id;
            }
            isNewUser = false;
        } else {
            userNode = (email != null) ? findUserNodeByExternalId(email).orElse(null) : null;
            isNewUser = userNode == null;
            if (isNewUser) {
                userNode = createUserNode(email);
            }

            if (userNode == null) {
                userNode = createUserNode(email);
            }

            existingIdentity = newIdentity();
            existingIdentity.nodeId = userNode.id;
            existingIdentity.oauthProvider = provider;
            existingIdentity.oauthSubject = subject.subject();
            persistIdentity(existingIdentity);
        }

        syncEntitlements(existingIdentity, subject.subject());
        existingIdentity.lastActiveAt = now();
        persistIdentity(existingIdentity);

        if (email != null && (userNode.externalId == null || userNode.externalId.isBlank())) {
            userNode.externalId = email;
            persistNode(userNode);
        }

        if (isNewUser) {
            for (String scope : DEFAULT_CONSENT_SCOPES) {
                recordConsent(userNode.id, scope);
            }
        }

        String displayName = OAuthProfileParser.fullNameOrNull(subject);
        profileService.upsertProfileFromProvider(
                userNode.id, provider, displayName,
                subject.givenName(), subject.familyName(), subject.email(),
                subject.picture(), null, subject.hostedDomain());

        return new LoginResult(userNode.id, displayName, isNewUser);
    }

    public ProfileSchema handleImport(String provider, String code, String redirectUri) {
        try {
            if ("github".equals(provider)) {
                GitHubEnrichedResult enriched = tokenExchangeService.exchangeGitHubEnriched(code, redirectUri);
                if (enriched == null) {
                    throw new IllegalStateException("Token exchange failed for provider: " + provider);
                }
                return OAuthProfileParser.buildEnrichedGitHubSchema(enriched);
            } else {
                OidcSubject subject = tokenExchangeService.exchangeAndResolveSubject(provider, code, redirectUri);
                if (subject == null) {
                    throw new IllegalStateException("Token exchange failed for provider: " + provider);
                }
                return OAuthProfileParser.buildImportSchema(provider, subject);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Token exchange failed for provider: " + provider, e);
        }
    }

    private void syncEntitlements(UserIdentity identity, String oauthSubject) {
        boolean shouldBeAdmin = appConfig.entitlements().isAdmin()
                .map(list -> list.contains(oauthSubject)).orElse(false);
        if (identity.isAdmin != shouldBeAdmin) {
            identity.isAdmin = shouldBeAdmin;
        }
    }

    private MeshNode createUserNode(String email) {
        MeshNode node = newUserNode();
        node.nodeType = NodeType.USER;
        node.title = "Anonymous";
        node.description = "";
        node.tags = new ArrayList<>();
        node.structuredData = new LinkedHashMap<>();
        node.searchable = true;
        node.externalId = email;
        persistNode(node);
        return node;
    }

    Optional<UserIdentity> findIdentityByOauth(String provider, String subject) {
        return userIdentityRepository.findByProviderAndSubject(provider, subject);
    }

    Optional<MeshNode> findNodeById(UUID nodeId) {
        return nodeRepository.findById(nodeId);
    }

    Optional<MeshNode> findUserNodeByExternalId(String email) {
        return nodeRepository.findUserByExternalId(email);
    }

    UserIdentity newIdentity() {
        return new UserIdentity();
    }

    MeshNode newUserNode() {
        return new MeshNode();
    }

    void persistIdentity(UserIdentity identity) {
        userIdentityRepository.persist(identity);
    }

    void persistNode(MeshNode node) {
        nodeRepository.persist(node);
    }

    void recordConsent(UUID nodeId, String scope) {
        consentService.recordConsent(nodeId, scope, null);
    }

    Instant now() {
        return Instant.now();
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        String normalized = email.trim();
        if (normalized.isBlank() || !normalized.contains("@")) return null;
        return normalized;
    }
}
