package org.peoplemesh.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.service.CurrentUserService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserResolverTest {

    @Mock
    CurrentUserService currentUserService;

    @InjectMocks
    UserResolver userResolver;

    @Test
    void resolveUserId_delegatesToCurrentUserService() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        assertEquals(userId, userResolver.resolveUserId());
    }
}
