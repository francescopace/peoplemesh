package org.peoplemesh.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsrfHeaderFilterTest {

    @Mock
    ContainerRequestContext request;

    @Mock
    UriInfo uriInfo;

    private final CsrfHeaderFilter filter = new CsrfHeaderFilter();

    private void stubPathAndMethod(String path, String method) {
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
    }

    private void stubPath(String path) {
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
    }

    @Test
    void filter_nonApiPath_doesNotAbort() {
        stubPath("/app/page");
        filter.filter(request);
        verify(request, never()).abortWith(any());
    }

    @Test
    void filter_getMethod_doesNotAbort() {
        stubPathAndMethod("/api/resource", "GET");
        filter.filter(request);
        verify(request, never()).abortWith(any());
    }

    @Test
    void filter_headMethod_doesNotAbort() {
        stubPathAndMethod("/api/resource", "HEAD");
        filter.filter(request);
        verify(request, never()).abortWith(any());
    }

    @Test
    void filter_optionsMethod_doesNotAbort() {
        stubPathAndMethod("/api/resource", "OPTIONS");
        filter.filter(request);
        verify(request, never()).abortWith(any());
    }

    @Test
    void filter_oauthCallbackPath_doesNotAbort() {
        stubPathAndMethod("/api/v1/auth/callback/oidc", "POST");
        filter.filter(request);
        verify(request, never()).abortWith(any());
    }

    @Test
    void filter_maintenancePath_doesNotAbort() {
        stubPathAndMethod("/api/v1/maintenance/purge-consent-tokens", "POST");
        filter.filter(request);
        verify(request, never()).abortWith(any());
    }

    @Test
    void filter_postWithHeader_doesNotAbort() {
        stubPathAndMethod("/api/resource", "POST");
        when(request.getHeaderString("X-Requested-With")).thenReturn("XMLHttpRequest");
        filter.filter(request);
        verify(request, never()).abortWith(any());
    }

    @Test
    void filter_postWithoutHeader_abortsWithForbidden() {
        stubPathAndMethod("/api/resource", "POST");
        when(request.getHeaderString("X-Requested-With")).thenReturn(null);
        filter.filter(request);
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(request).abortWith(captor.capture());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), captor.getValue().getStatus());
    }

    @Test
    void filter_postWithBlankHeader_abortsWithForbidden() {
        stubPathAndMethod("/api/resource", "POST");
        when(request.getHeaderString("X-Requested-With")).thenReturn("   ");
        filter.filter(request);
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(request).abortWith(captor.capture());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), captor.getValue().getStatus());
    }
}
