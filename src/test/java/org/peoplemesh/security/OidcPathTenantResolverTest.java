package org.peoplemesh.security;

import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OidcPathTenantResolverTest {

    @Test
    void resolve_alwaysReturnsNull() {
        OidcPathTenantResolver resolver = new OidcPathTenantResolver();
        RoutingContext ctx = mock(RoutingContext.class);

        assertNull(resolver.resolve(ctx));
    }
}
