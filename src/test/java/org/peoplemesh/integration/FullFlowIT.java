package org.peoplemesh.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.service.SessionService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test that validates the REST API endpoints are wired correctly.
 * Uses Quarkus DevServices for Postgres (pgvector).
 * Auth is relaxed in test profile for endpoint accessibility testing.
 */
@QuarkusTest
class FullFlowIT {

    @Inject
    SessionService sessionService;

    @Inject
    UserTransaction userTransaction;

    @Test
    void healthEndpoint_returnsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void authProvidersEndpoint_isPublicAndReturns200() {
        given()
                .when().get("/api/v1/auth/providers")
                .then()
                .statusCode(200)
                .body("providers", notNullValue());
    }

    @Test
    void meEndpoint_withoutAuth_returns204NoProfile() {
        given()
                .contentType(ContentType.JSON)
                .queryParam("identity_only", "true")
                .when().get("/api/v1/me")
                .then()
                .statusCode(204);
    }

    @Test
    void matchesEndpoint_withoutAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/matches/me")
                .then()
                .statusCode(401);
    }

    @Test
    void privacyDashboard_withoutAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/me/activity")
                .then()
                .statusCode(401);
    }

    @Test
    void matchesMeEndpoint_withoutAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/matches/me")
                .then()
                .statusCode(401);
    }

    @Test
    void securityHeaders_arePresent() {
        given()
                .when().get("/q/health")
                .then()
                .header("Cache-Control", is("no-store"));
    }

    @Test
    void unknownEndpoint_withoutAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/nonexistent")
                .then()
                .statusCode(401);
    }

    @Test
    void authenticatedEndpoints_withValidPmSessionCookie_return200() throws Exception {
        UserIdentity user = createUser("github");
        String cookie = sessionService.encodeSession(user.nodeId, user.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .queryParam("identity_only", "true")
                .when().get("/api/v1/me")
                .then()
                .statusCode(200)
                .body("user_id", is(user.nodeId.toString()))
                .body("provider", is("github"));

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .when().get("/api/v1/matches/me")
                .then()
                .statusCode(200);
    }

    @Test
    void protectedEndpoint_withInvalidPmSessionCookie_returns401() throws Exception {
        UserIdentity user = createUser("github");
        String cookie = sessionService.encodeSession(user.nodeId, user.oauthProvider) + "tampered";

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .when().get("/api/v1/matches/me")
                .then()
                .statusCode(401);
    }

    @Test
    void cvImportEndpoints_areReachable() throws Exception {
        UserIdentity user = createUser("apple");
        String cookie = sessionService.encodeSession(user.nodeId, user.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .multiPart("wrong", "empty")
                .when().post("/api/v1/me/cv-import")
                .then()
                .statusCode(400);
    }

    private UserIdentity createUser(String provider) throws Exception {
        userTransaction.begin();
        try {
            MeshNode node = new MeshNode();
            node.nodeType = NodeType.USER;
            node.title = "IT User";
            node.description = "";
            node.tags = new ArrayList<>();
            node.structuredData = new LinkedHashMap<>();
            node.searchable = true;
            node.persist();

            UserIdentity user = new UserIdentity();
            user.nodeId = node.id;
            user.oauthProvider = provider;
            user.oauthSubject = provider + "-" + UUID.randomUUID();
            user.persist();

            userTransaction.commit();
            return user;
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }
}
