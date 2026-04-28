package org.peoplemesh.service;

import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.AuthIdentityResponse;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthIdentityServiceTest {

    @Mock
    NodeRepository nodeRepository;
    @Mock
    UserIdentityRepository userIdentityRepository;
    @Mock
    EntitlementService entitlementService;

    @InjectMocks
    AuthIdentityService service;

    @Test
    void resolveCurrentIdentity_withUserIdAttribute_returnsPayload() {
        UUID nodeId = UUID.randomUUID();
        SecurityIdentity identity = mock(SecurityIdentity.class);
        MeshNode node = new MeshNode();
        node.id = nodeId;
        node.nodeType = NodeType.USER;
        node.externalId = "mail@test.com";
        node.structuredData = Map.of("avatar_url", "https://cdn.example.com/avatar.png");
        when(identity.getAttribute("pm.userId")).thenReturn(nodeId);
        when(identity.getAttribute("pm.provider")).thenReturn("google");
        when(identity.getAttribute("pm.displayName")).thenReturn("Alice");
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(entitlementService.isAdmin(nodeId)).thenReturn(true);

        Optional<AuthIdentityResponse> payload = service.resolveCurrentIdentity(identity);

        assertTrue(payload.isPresent());
        AuthIdentityResponse schema = payload.get();
        assertEquals(nodeId, schema.userId());
        assertEquals("google", schema.provider());
        assertEquals("Alice", schema.displayName());
        assertEquals("https://cdn.example.com/avatar.png", schema.photoUrl());
        assertTrue(schema.entitlements().isAdmin());
    }

    @Test
    void resolveCurrentIdentity_fallbackByOauthSubject_returnsPayload() {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        Principal principal = mock(Principal.class);
        UUID nodeId = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = nodeId;
        node.nodeType = NodeType.USER;
        UserIdentity linked = new UserIdentity();
        linked.nodeId = nodeId;
        linked.oauthProvider = "microsoft";

        when(identity.getAttribute("pm.userId")).thenReturn(null);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("sub-1");
        when(userIdentityRepository.findByOauthSubject("sub-1")).thenReturn(Optional.of(linked));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(entitlementService.isAdmin(nodeId)).thenReturn(false);

        Optional<AuthIdentityResponse> payload = service.resolveCurrentIdentity(identity);

        assertTrue(payload.isPresent());
        assertEquals("microsoft", payload.get().provider());
        assertFalse(payload.get().entitlements().isAdmin());
    }

    @Test
    void resolveCurrentIdentity_anonymous_returnsEmpty() {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        when(identity.getAttribute("pm.userId")).thenReturn(null);
        when(identity.isAnonymous()).thenReturn(true);

        Optional<AuthIdentityResponse> payload = service.resolveCurrentIdentity(identity);

        assertTrue(payload.isEmpty());
    }
}
