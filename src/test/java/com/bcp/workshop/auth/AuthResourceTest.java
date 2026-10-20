package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    @Inject
    LegacyDatabase database;

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void logsInWithUsernameContainingApostrophe() throws Exception {
        database.findByCredentials("student", "Workshop123!");
        String username = "student's-" + UUID.randomUUID();
        String databaseUrl = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
        Files.createDirectories(Path.of("data"));
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, username);
            statement.setString(2, "test-password");
            statement.setString(3, "Test User");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }

        try {
            given()
                    .contentType("application/json")
                    .body(Map.of("username", username, "password", "test-password"))
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .body("user.username", is(username))
                    .body("token", notNullValue());
        } finally {
            try (Connection connection = DriverManager.getConnection(databaseUrl);
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM legacy_users WHERE username=?")) {
                statement.setString(1, username);
                statement.executeUpdate();
            }
        }
    }
}
