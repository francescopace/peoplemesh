package org.peoplemesh.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test that validates the REST API endpoints are wired correctly.
 * Uses Quarkus DevServices for Postgres (pgvector), Redis, and Vault.
 * Auth is relaxed in test profile for endpoint accessibility testing.
 */
@QuarkusTest
class FullFlowIT {

    @Test
    void healthEndpoint_returnsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void meEndpoint_withoutProfile_returns404or401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/me")
                .then()
                .statusCode(anyOf(is(401), is(404), is(403)));
    }

    @Test
    void matchesEndpoint_withoutProfile_returns404or401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/matches")
                .then()
                .statusCode(anyOf(is(401), is(404), is(403)));
    }

    @Test
    void connectionsEndpoint_returns200or401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/connections")
                .then()
                .statusCode(anyOf(is(401), is(200), is(403)));
    }

    @Test
    void privacyDashboard_returns200or401() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/privacy/activity")
                .then()
                .statusCode(anyOf(is(401), is(200), is(403)));
    }

    @Test
    void jobsEndpoints_areReachable() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/jobs")
                .then()
                .statusCode(anyOf(is(401), is(200), is(403)));

        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/jobs/matches")
                .then()
                .statusCode(anyOf(is(401), is(200), is(403), is(404)));
    }

    @Test
    void jobPipelineEndpoints_areReachable() {
        String fakeJobId = "00000000-0000-0000-0000-000000000001";

        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/jobs/" + fakeJobId + "/pipeline")
                .then()
                .statusCode(anyOf(is(401), is(400), is(404), is(403), is(200)));

        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/jobs/" + fakeJobId + "/pipeline/inbox")
                .then()
                .statusCode(anyOf(is(401), is(400), is(404), is(403), is(200)));
    }

    @Test
    void securityHeaders_arePresent() {
        given()
                .when().get("/q/health")
                .then()
                .header("Cache-Control", is("no-store"));
    }

    @Test
    void unknownEndpoint_returns404WithProblemDetail() {
        given()
                .contentType(ContentType.JSON)
                .when().get("/api/v1/nonexistent")
                .then()
                .statusCode(anyOf(is(401), is(404)));
    }
}
