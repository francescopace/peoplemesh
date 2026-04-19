package org.peoplemesh.service;

import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.exception.BusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private SecurityIdentity identity;
    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private UserIdentityRepository userIdentityRepository;
    @Mock
    private Principal principal;

    private CurrentUserService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CurrentUserService();
        setField("identity", identity);
        setField("nodeRepository", nodeRepository);
        setField("userIdentityRepository", userIdentityRepository);
    }

    @Test
    void findCurrentUserId_sessionUserWithNode_returnsId() {
        UUID userId = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = userId;
        when(identity.getAttribute("pm.userId")).thenReturn(userId);
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));

        Optional<UUID> out = service.findCurrentUserId();

        assertTrue(out.isPresent());
        assertEquals(userId, out.get());
    }

    @Test
    void findCurrentUserId_sessionUserWithoutNode_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(identity.getAttribute("pm.userId")).thenReturn(userId);
        when(nodeRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<UUID> out = service.findCurrentUserId();

        assertTrue(out.isEmpty());
    }

    @Test
    void findCurrentUserId_anonymousWithoutSession_returnsEmpty() {
        when(identity.getAttribute("pm.userId")).thenReturn(null);
        when(identity.isAnonymous()).thenReturn(true);

        Optional<UUID> out = service.findCurrentUserId();

        assertTrue(out.isEmpty());
    }

    @Test
    void findCurrentUserId_oauthLinkedUser_returnsNodeId() {
        UUID nodeId = UUID.randomUUID();
        UserIdentity linked = new UserIdentity();
        linked.nodeId = nodeId;
        when(identity.getAttribute("pm.userId")).thenReturn(null);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("oauth-subject-1");
        when(userIdentityRepository.findByOauthSubject("oauth-subject-1")).thenReturn(Optional.of(linked));

        Optional<UUID> out = service.findCurrentUserId();

        assertTrue(out.isPresent());
        assertEquals(nodeId, out.get());
    }

    @Test
    void resolveUserId_sessionExpired_throwsBusinessException() {
        UUID userId = UUID.randomUUID();
        when(identity.getAttribute("pm.userId")).thenReturn(userId);
        when(nodeRepository.findById(userId)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, service::resolveUserId);

        BusinessException business = assertInstanceOf(BusinessException.class, ex);
        assertEquals(401, business.status());
    }

    @Test
    void resolveUserId_notRegistered_throwsSecurityException() {
        when(identity.getAttribute("pm.userId")).thenReturn(null);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("oauth-subject-2");
        when(userIdentityRepository.findByOauthSubject("oauth-subject-2")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, service::resolveUserId);

        assertInstanceOf(SecurityException.class, ex);
        assertTrue(ex.getMessage().contains("User not registered"));
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CurrentUserService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }
}
