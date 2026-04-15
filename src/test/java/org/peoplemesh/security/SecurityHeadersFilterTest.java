package org.peoplemesh.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityHeadersFilterTest {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";
    private static final String PAGE_CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com;"
                    + " font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; img-src 'self' data: https://randomuser.me https://media.licdn.com https://*.googleusercontent.com https://avatars.githubusercontent.com;"
                    + " connect-src 'self';"
                    + " frame-ancestors 'none'";

    @Mock
    ContainerRequestContext request;

    @Mock
    ContainerResponseContext response;

    @Mock
    UriInfo uriInfo;

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();
    private MultivaluedMap<String, Object> headers;

    @BeforeEach
    void setUp() {
        headers = new MultivaluedHashMap<>();
        when(response.getHeaders()).thenReturn(headers);
        when(request.getUriInfo()).thenReturn(uriInfo);
    }

    @Test
    void filter_setsHSTSHeader() {
        when(uriInfo.getPath()).thenReturn("/");
        filter.filter(request, response);
        assertEquals("max-age=31536000; includeSubDomains; preload",
                headers.getFirst("Strict-Transport-Security"));
    }

    @Test
    void filter_setsXContentTypeOptions() {
        when(uriInfo.getPath()).thenReturn("/");
        filter.filter(request, response);
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
    }

    @Test
    void filter_apiPath_setsApiCSP() {
        when(uriInfo.getPath()).thenReturn("/api/v1/me");
        filter.filter(request, response);
        assertEquals(API_CSP, headers.getFirst("Content-Security-Policy"));
    }

    @Test
    void filter_nonApiPath_setsPageCSP() {
        when(uriInfo.getPath()).thenReturn("/dashboard");
        filter.filter(request, response);
        assertEquals(PAGE_CSP, headers.getFirst("Content-Security-Policy"));
    }

    @Test
    void filter_apiPath_setsCacheNoStore() {
        when(uriInfo.getPath()).thenReturn("/api/v1/me");
        filter.filter(request, response);
        assertEquals("no-store", headers.getFirst("Cache-Control"));
    }

    @Test
    void filter_nonApiPath_setsCacheNoCache() {
        when(uriInfo.getPath()).thenReturn("/dashboard");
        filter.filter(request, response);
        assertEquals("no-cache", headers.getFirst("Cache-Control"));
    }
}
