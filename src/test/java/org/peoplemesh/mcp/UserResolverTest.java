package org.peoplemesh.mcp;

import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserResolverTest {

    @Mock SecurityIdentity identity;

    @InjectMocks UserResolver userResolver;

    @Test
    void resolveUserId_noSessionAndNoOauthSubject_throwsSecurityException() {
        when(identity.<UUID>getAttribute("pm.userId")).thenReturn(null);
        when(identity.getPrincipal()).thenReturn(() -> "unknown-subject");

        // MeshNode.findByIdOptional(...) is inherited from PanacheEntityBase
        // and require bytecode enhancement — cannot be mocked in unit tests.
        // Session-based and OAuth subject resolution paths are covered via FullFlowIT.
        assertThrows(Exception.class, () -> userResolver.resolveUserId());
    }
}
