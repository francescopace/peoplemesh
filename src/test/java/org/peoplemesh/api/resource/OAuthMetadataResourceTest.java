package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class OAuthMetadataResourceTest {

    OAuthMetadataResource resource = new OAuthMetadataResource();

    @Test
    void oauthProtectedResource_returnsProtectedResourceMetadata() {
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test/"));

        Response response = resource.oauthProtectedResource(uriInfo);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("https://api.peoplemesh.test/mcp", body.get("resource"));
        assertEquals(List.of("https://api.peoplemesh.test"), body.get("authorization_servers"));
        assertEquals(List.of("header"), body.get("bearer_methods_supported"));
        assertEquals(List.of("openid", "profile"), body.get("scopes_supported"));
    }

    @Test
    void oauthAuthorizationServer_returnsOauthServerMetadata() {
        UriInfo uriInfo = Mockito.mock(UriInfo.class);
        Mockito.when(uriInfo.getBaseUri()).thenReturn(URI.create("https://api.peoplemesh.test////"));

        Response response = resource.oauthAuthorizationServer(uriInfo);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = assertInstanceOf(Map.class, response.getEntity());
        assertEquals("https://api.peoplemesh.test", body.get("issuer"));
        assertEquals("https://api.peoplemesh.test/oauth/authorize", body.get("authorization_endpoint"));
        assertEquals("https://api.peoplemesh.test/oauth/token", body.get("token_endpoint"));
        assertEquals("https://api.peoplemesh.test/oauth/register", body.get("registration_endpoint"));
        assertEquals(List.of("code"), body.get("response_types_supported"));
        assertEquals(List.of("authorization_code"), body.get("grant_types_supported"));
        assertEquals(List.of("none", "client_secret_post"), body.get("token_endpoint_auth_methods_supported"));
        assertEquals(List.of("S256"), body.get("code_challenge_methods_supported"));
    }
}
