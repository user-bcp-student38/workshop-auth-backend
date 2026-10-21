package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\",\"ignored\":true}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", instanceOf(String.class))
                .body("expiresIn", instanceOf(Integer.class))
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void logsInWithTrainerAndKeepsLegacyTokenShape() {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"trainer\",\"password\":\"Trainer123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("trainer"))
                .body("user.displayName", is("Workshop Trainer"))
                .body("user.role", is("TRAINER"))
                .body("token", instanceOf(String.class))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("expiresIn", is(3600))
                .extract().path("token");

        String decodedToken = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
        String[] tokenParts = decodedToken.split("\\|", -1);
        assertTrue(tokenParts.length == 4, "legacy token has four segments");
        assertTrue(tokenParts[0].equals("trainer"), "legacy token subject matches the authenticated user");
        assertTrue(tokenParts[1].equals("TRAINER"), "legacy token role matches the authenticated user");
        assertTrue(tokenParts[2].matches("\\d+"), "legacy token expiry is numeric");
        assertTrue(!tokenParts[3].isBlank(), "legacy token includes its final segment");
    }

    @Test
    void logsInWithoutOptionalClientField() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void returns400WhenUsernameIsMissingEvenIfPasswordIsAlsoMissing() {
        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"))
                .body("token", nullValue());
    }

    @Test
    void returns400WhenUsernameIsBlank() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"   \",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"))
                .body("token", nullValue());
    }

    @Test
    void returns400WhenPasswordIsMissing() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"))
                .body("token", nullValue());
    }

    @Test
    void returns400WhenPasswordIsBlank() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\" \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"))
                .body("token", nullValue());
    }

    @Test
    void returns401ForInvalidPasswordWithoutReturningToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"wrong\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"))
                .body("token", nullValue());
    }

    @Test
    void returns401ForUnknownUserWithoutReturningToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"unknown\",\"password\":\"not-empty\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user unknown or password"))
                .body("token", nullValue());
    }

    @Test
    void returns400WhenResourceReceivesNullRequest() {
        Response response = new AuthResource().login(null);

        assertEquals(400, response.getStatus());
        assertEquals(Map.of("message", "Request is null"), response.getEntity());
    }

    @Test
    void returnsExisting500ContractWhenResourceThrowsWithoutMessage() {
        AuthResource resource = new AuthResource();
        resource.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new RuntimeException();
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";

        Response response = resource.login(request);

        assertEquals(500, response.getStatus());
        assertEquals(Map.of("message", "Unknown error", "exception", "java.lang.RuntimeException"), response.getEntity());
    }

    @Test
    void returnsPersistedUserDataWithHtmlEscapedDisplayName() {
        AuthResource resource = new AuthResource();
        resource.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                return new UserRecord(99, "synthetic", "<b>Trainer</b>", "TRAINER");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "synthetic";
        request.password = "test-password";

        Response response = resource.login(request);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        @SuppressWarnings("unchecked")
        Map<String, String> user = (Map<String, String>) body.get("user");
        assertEquals("synthetic", user.get("username"));
        assertEquals("&lt;b&gt;Trainer&lt;/b&gt;", user.get("displayName"));
        assertEquals("TRAINER", user.get("role"));
    }

    @Test
    void healthEndpointRemainsPublicAndReportsStatusAndChecks() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks", notNullValue());
    }
}
