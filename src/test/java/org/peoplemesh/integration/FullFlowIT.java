package org.peoplemesh.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.service.SessionService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Inject
    NodeRepository nodeRepository;

    @Test
    void healthEndpoint_returns200AndUpStatus() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void infoEndpoint_isPublic_returns200WithAuthProviders() {
        given()
                .when().get("/api/v1/info")
                .then()
                .statusCode(200)
                .body("authProviders.loginProviders", notNullValue())
                .body("authProviders.profileImportProviders", notNullValue());
    }

    @Test
    void authIdentityEndpoint_withoutAuth_returns204() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/auth/identity")
                .then()
                .statusCode(204);
    }

    @Test
    void matchesEndpoint_requiresAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/matches/me")
                .then()
                .statusCode(401);
    }

    @Test
    void privacyDashboard_requiresAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/me/activity")
                .then()
                .statusCode(401);
    }

    @Test
    void matchesMeEndpoint_requiresAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/matches/me")
                .then()
                .statusCode(401);
    }

    @Test
    void healthEndpoint_includesNoStoreCacheControlHeader() {
        given()
                .when().get("/q/health")
                .then()
                .header("Cache-Control", is("no-store"));
    }

    @Test
    void unknownApiRoute_withoutAuth_isInterceptedAs401() {
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
                .when().get("/api/v1/auth/identity")
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
    void protectedEndpoint_withInvalidPmSessionCookie_returns401Unauthorized() throws Exception {
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
    void cvImportEndpoint_withInvalidMultipart_returns400() throws Exception {
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

    @Test
    void meUpdate_onlyAffectsAuthenticatedUsersOwnProfile() throws Exception {
        UserIdentity userA = createUser("github");
        UserIdentity userB = createUser("google");
        String cookieA = sessionService.encodeSession(userA.nodeId, userA.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, cookieA)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "identity": {
                            "birth_date": "1992-04-12"
                          }
                        }
                        """)
                .when().put("/api/v1/me")
                .then()
                .statusCode(200);

        assertEquals("1992-04-12", readBirthDate(userA.nodeId));
        assertNull(readBirthDate(userB.nodeId));
    }

    @Test
    void mePatch_onlyAffectsAuthenticatedUsersOwnProfile() throws Exception {
        UserIdentity userA = createUser("github");
        UserIdentity userB = createUser("google");
        String cookieA = sessionService.encodeSession(userA.nodeId, userA.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, cookieA)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/merge-patch+json")
                .body("""
                        {
                          "identity": {
                            "birth_date": "1993-05-22"
                          }
                        }
                        """)
                .when().patch("/api/v1/me")
                .then()
                .statusCode(200);

        assertEquals("1993-05-22", readBirthDate(userA.nodeId));
        assertNull(readBirthDate(userB.nodeId));
    }

    @Test
    void mePatch_nullClearsBirthDate() throws Exception {
        UserIdentity user = createUser("github");
        String cookie = sessionService.encodeSession(user.nodeId, user.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "identity": {
                            "birth_date": "1992-04-12"
                          }
                        }
                        """)
                .when().put("/api/v1/me")
                .then()
                .statusCode(200);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/merge-patch+json")
                .body("""
                        {
                          "identity": {
                            "birth_date": null
                          }
                        }
                        """)
                .when().patch("/api/v1/me")
                .then()
                .statusCode(200);

        assertNull(readBirthDate(user.nodeId));
    }

    @Test
    void mePatch_replacesSkillsTechnicalArray() throws Exception {
        UserIdentity user = createUser("github");
        String cookie = sessionService.encodeSession(user.nodeId, user.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "professional": {
                            "skills_technical": ["Java", "Kotlin"]
                          }
                        }
                        """)
                .when().put("/api/v1/me")
                .then()
                .statusCode(200);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/merge-patch+json")
                .body("""
                        {
                          "professional": {
                            "skills_technical": ["Rust"]
                          }
                        }
                        """)
                .when().patch("/api/v1/me")
                .then()
                .statusCode(200);

        assertEquals(List.of("rust"), readSkillsTechnical(user.nodeId));
    }

    @Test
    void mePatch_rejectsNonObjectPayload() throws Exception {
        UserIdentity user = createUser("github");
        String cookie = sessionService.encodeSession(user.nodeId, user.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/merge-patch+json")
                .body("""
                        ["invalid"]
                        """)
                .when().patch("/api/v1/me")
                .then()
                .statusCode(400);
    }

    @Test
    void mePatch_cannotOverrideOAuthManagedDisplayName() throws Exception {
        UserIdentity user = createUser("github");
        String cookie = sessionService.encodeSession(user.nodeId, user.oauthProvider);
        String beforeTitle = readNodeTitle(user.nodeId);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/merge-patch+json")
                .body("""
                        {
                          "identity": {
                            "display_name": "Injected Name"
                          }
                        }
                        """)
                .when().patch("/api/v1/me")
                .then()
                .statusCode(200);

        assertEquals(beforeTitle, readNodeTitle(user.nodeId));
    }

    @Test
    void mePatch_withWrongContentType_returns415() throws Exception {
        UserIdentity user = createUser("github");
        String cookie = sessionService.encodeSession(user.nodeId, user.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "identity": {
                            "birth_date": "1992-04-12"
                          }
                        }
                        """)
                .when().patch("/api/v1/me")
                .then()
                .statusCode(415);
    }

    @Test
    void nodesWriteRoutes_areNotAvailable() throws Exception {
        UserIdentity nonAdmin = createUser("github", false);
        String nonAdminCookie = sessionService.encodeSession(nonAdmin.nodeId, nonAdmin.oauthProvider);
        MeshNode target = createProjectNode(nonAdmin.nodeId, "Original");

        given()
                .cookie(SessionService.COOKIE_NAME, nonAdminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "node_type": "PROJECT",
                          "title": "Blocked Create",
                          "description": "Desc",
                          "tags": ["x"],
                          "structured_data": {"a":"b"},
                          "country": "IT"
                        }
                        """)
                .when().post("/api/v1/nodes")
                .then()
                .statusCode(404);

        given()
                .cookie(SessionService.COOKIE_NAME, nonAdminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "node_type": "PROJECT",
                          "title": "Blocked Update",
                          "description": "Desc",
                          "tags": ["x"],
                          "structured_data": {"a":"b"},
                          "country": "IT"
                        }
                        """)
                .when().put("/api/v1/nodes/" + target.id)
                .then()
                .statusCode(405);
    }

    @Test
    void cannotUpdateAnotherUsersProfileViaNodesRoute_routeNotAllowed() throws Exception {
        UserIdentity attacker = createUser("github", false);
        UserIdentity victim = createUser("google", false);
        String attackerCookie = sessionService.encodeSession(attacker.nodeId, attacker.oauthProvider);

        String beforeTitle = readNodeTitle(victim.nodeId);

        given()
                .cookie(SessionService.COOKIE_NAME, attackerCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "node_type": "USER",
                          "title": "Hacked",
                          "description": "Injected",
                          "tags": [],
                          "structured_data": {"birth_date":"2000-01-01"},
                          "country": "IT"
                        }
                        """)
                .when().put("/api/v1/nodes/" + victim.nodeId)
                .then()
                .statusCode(405);

        assertEquals(beforeTitle, readNodeTitle(victim.nodeId));
    }

    @Test
    void nonAdmin_isForbiddenOnSkillsWriteEndpoints_returns403() throws Exception {
        UserIdentity nonAdmin = createUser("github", false);
        String nonAdminCookie = sessionService.encodeSession(nonAdmin.nodeId, nonAdmin.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, nonAdminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/octet-stream")
                .body("category,name\nBackend,Java\n".getBytes())
                .when().post("/api/v1/skills/import")
                .then()
                .statusCode(403);

        given()
                .cookie(SessionService.COOKIE_NAME, nonAdminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .when().post("/api/v1/skills/cleanup-unused")
                .then()
                .statusCode(403);
    }

    @Test
    void admin_canManageSkillsWriteEndpoints_returns200() throws Exception {
        UserIdentity admin = createUser("github", true);
        String adminCookie = sessionService.encodeSession(admin.nodeId, admin.oauthProvider);

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/octet-stream")
                .body("category,name\nBackend,Java\n".getBytes())
                .when().post("/api/v1/skills/import")
                .then()
                .statusCode(200)
                .body("imported", greaterThanOrEqualTo(1));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .when().post("/api/v1/skills/cleanup-unused")
                .then()
                .statusCode(200)
                .body("deleted", greaterThanOrEqualTo(0));
    }

    private UserIdentity createUser(String provider) throws Exception {
        return createUser(provider, false);
    }

    private UserIdentity createUser(String provider, boolean isAdmin) throws Exception {
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
            user.isAdmin = isAdmin;
            user.persist();

            userTransaction.commit();
            return user;
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    private MeshNode createProjectNode(UUID ownerUserId, String title) throws Exception {
        userTransaction.begin();
        try {
            MeshNode node = new MeshNode();
            node.nodeType = NodeType.PROJECT;
            node.title = title;
            node.description = "desc";
            node.tags = new ArrayList<>();
            node.structuredData = new LinkedHashMap<>();
            node.country = "IT";
            node.searchable = true;
            node.persist();
            userTransaction.commit();
            return node;
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    private String readNodeTitle(UUID nodeId) throws Exception {
        userTransaction.begin();
        try {
            MeshNode node = nodeRepository.findById(nodeId).orElse(null);
            String title = node != null ? node.title : null;
            userTransaction.commit();
            return title;
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    private String readBirthDate(UUID nodeId) throws Exception {
        userTransaction.begin();
        try {
            MeshNode node = nodeRepository.findById(nodeId).orElse(null);
            String birthDate = null;
            if (node != null && node.structuredData != null) {
                birthDate = (String) node.structuredData.get("birth_date");
            }
            userTransaction.commit();
            return birthDate;
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    private List<String> readSkillsTechnical(UUID nodeId) throws Exception {
        userTransaction.begin();
        try {
            MeshNode node = nodeRepository.findById(nodeId).orElse(null);
            List<String> technicalSkills = node != null && node.tags != null
                    ? new ArrayList<>(node.tags)
                    : List.of();
            userTransaction.commit();
            return technicalSkills;
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }
}
