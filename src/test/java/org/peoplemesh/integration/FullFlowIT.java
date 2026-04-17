package org.peoplemesh.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.SkillCatalog;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.service.SessionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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

    @Test
    void meUpdate_updatesOnlyCurrentUserProfile_notOtherUsers() throws Exception {
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
    void nonAdmin_cannotCreateOrUpdateNodes() throws Exception {
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
                .statusCode(403);

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
                .statusCode(403);
    }

    @Test
    void nonAdmin_cannotUpdateAnotherUsersProfileNode() throws Exception {
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
                .statusCode(403);

        assertEquals(beforeTitle, readNodeTitle(victim.nodeId));
    }

    @Test
    void nonAdmin_cannotManageSkillsWriteEndpoints() throws Exception {
        UserIdentity nonAdmin = createUser("github", false);
        String nonAdminCookie = sessionService.encodeSession(nonAdmin.nodeId, nonAdmin.oauthProvider);
        SkillCatalog catalog = createCatalog("Catalog ReadOnly");

        given()
                .cookie(SessionService.COOKIE_NAME, nonAdminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Blocked Catalog",
                          "description": "Desc",
                          "source": "manual"
                        }
                        """)
                .when().post("/api/v1/skills")
                .then()
                .statusCode(403);

        given()
                .cookie(SessionService.COOKIE_NAME, nonAdminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Blocked Update",
                          "description": "Desc",
                          "source": "manual"
                        }
                        """)
                .when().put("/api/v1/skills/" + catalog.id)
                .then()
                .statusCode(403);

        given()
                .cookie(SessionService.COOKIE_NAME, nonAdminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/octet-stream")
                .body("category,name\nBackend,Java\n".getBytes())
                .when().post("/api/v1/skills/" + catalog.id + "/import")
                .then()
                .statusCode(403);

        given()
                .cookie(SessionService.COOKIE_NAME, nonAdminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .when().delete("/api/v1/skills/" + catalog.id)
                .then()
                .statusCode(403);
    }

    @Test
    void admin_canManageSkillsWriteEndpoints() throws Exception {
        UserIdentity admin = createUser("github", true);
        String adminCookie = sessionService.encodeSession(admin.nodeId, admin.oauthProvider);

        String catalogId = given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Admin Catalog",
                          "description": "Desc",
                          "source": "manual"
                        }
                        """)
                .when().post("/api/v1/skills")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "Admin Catalog Updated",
                          "description": "Desc 2",
                          "source": "manual"
                        }
                        """)
                .when().put("/api/v1/skills/" + catalogId)
                .then()
                .statusCode(200)
                .body("name", is("Admin Catalog Updated"));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType("application/octet-stream")
                .body("category,name\nBackend,Java\n".getBytes())
                .when().post("/api/v1/skills/" + catalogId + "/import")
                .then()
                .statusCode(200)
                .body("imported", greaterThanOrEqualTo(1));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .header("X-Requested-With", "XMLHttpRequest")
                .when().delete("/api/v1/skills/" + catalogId)
                .then()
                .statusCode(204);
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
            node.createdBy = ownerUserId;
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

    private SkillCatalog createCatalog(String name) throws Exception {
        userTransaction.begin();
        try {
            SkillCatalog catalog = new SkillCatalog();
            catalog.name = name;
            catalog.description = "desc";
            catalog.levelScale = Map.of("1", "Beginner", "5", "Expert");
            catalog.source = "manual";
            catalog.createdAt = Instant.now();
            catalog.updatedAt = Instant.now();
            catalog.persist();
            userTransaction.commit();
            return catalog;
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    private String readNodeTitle(UUID nodeId) throws Exception {
        userTransaction.begin();
        try {
            MeshNode node = MeshNode.findById(nodeId);
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
            MeshNode node = MeshNode.findById(nodeId);
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
}
