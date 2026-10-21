# Testing del Backend

## Matriz de cobertura de endpoints

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | Credenciales validas (student/Workshop123!) | Camino feliz | 200, token no nulo, user.username=student |
| POST /auth/login | Login sin campo opcional client | Caso borde | 200, mismo contrato de respuesta que camino feliz |
| POST /auth/login | Request null | Error documentado (400) | 400, body.message="Request is null" |
| POST /auth/login | username ausente | Error documentado (400) | 400, body.message="Username is required" |
| POST /auth/login | username en blanco | Caso borde | 400, body.message="Username is required" |
| POST /auth/login | password ausente | Error documentado (400) | 400, body.message="Password is required" |
| POST /auth/login | password en blanco | Caso borde | 400, body.message="Password is required" |
| POST /auth/login | credenciales invalidas | Error documentado (401) | 401, body.message="Invalid user <username> or password" |
| POST /auth/login | excepcion interna controlada | Error documentado (500) | 500 con mensaje generico y sin detalles internos |
| GET /q/health | Estado saludable por defecto | Camino feliz | 200, status="UP", checks presente |
| GET /q/health | Query param inesperado | Caso borde | 200, status="UP" |
| GET /q/health | Readiness forzada a DOWN | Error documentado (503) | 503, status="DOWN", checks presente |

## Codigo de tests

### Archivos

- src/test/java/com/bcp/workshop/auth/AuthResourceTest.java
- src/test/java/com/bcp/workshop/auth/HealthEndpointTest.java
- src/test/java/com/bcp/workshop/auth/TestHealthToggle.java

### Test de login (fragmentos agregados)

```java
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
void returns400WhenUsernameIsBlank() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"   \",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("message", is("Username is required"));
}

@Test
void returns400WhenPasswordIsBlank() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\" \",\"client\":\"angular-workshop\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("message", is("Password is required"));
}

@Test
void errorResponseIsGenericAndDoesNotExposeInternalDetails() {
    AuthResource resource = new AuthResource();
    resource.authService = new AuthService() {
        @Override
        public LoginResponse login(String username, String password) {
            throw new RuntimeException("boom");
        }
    };

    LoginRequest request = new LoginRequest();
    request.username = "student";
    request.password = "Workshop123!";

    Response response = resource.login(request);
    assertEquals(500, response.getStatus());

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) response.getEntity();
    assertEquals("Internal server error", body.get("message"));
    assertTrue(!body.containsKey("exception"));
}
```

### Test de health (archivo completo)

```java
@QuarkusTest
class HealthEndpointTest {
    @AfterEach
    void clearHealthOverride() {
        TestHealthToggle.forceDown(false);
    }

    @Test
    void healthEndpointReturnsUpInHappyPath() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks", notNullValue());
    }

    @Test
    void healthEndpointIgnoresUnexpectedQueryParamAsEdgeCase() {
        given()
                .queryParam("source", "integration-test")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void healthEndpointReturns503WhenAReadinessCheckIsDown() {
        TestHealthToggle.forceDown(true);

        given()
                .when().get("/q/health")
                .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body("checks", notNullValue());
    }
}
```

```java
@Readiness
@ApplicationScoped
public class TestHealthToggle implements HealthCheck {
    private static final AtomicBoolean FORCE_DOWN = new AtomicBoolean(false);

    static void forceDown(boolean value) {
        FORCE_DOWN.set(value);
    }

    @Override
    public HealthCheckResponse call() {
        if (FORCE_DOWN.get()) {
            return HealthCheckResponse.down("test-readiness-toggle");
        }
        return HealthCheckResponse.up("test-readiness-toggle");
    }
}
```

## Ejecucion real

### Comando de validacion principal

- Directorio: C:/Users/Usuario/Documents/T60146-jonatan-moreno-leyva/reqflow-workspace/workshop-auth-backend
- Comando: .tools/maven/bin/mvn.cmd verify
- Codigo de salida: 0

### Resultados de tests (surefire)

- Tests run: 18
- Failures: 0
- Errors: 0
- Skipped: 0

### Cobertura (JaCoCo)

- Metodo de medicion: reporte XML generado por jacoco-maven-plugin en target/site/jacoco/jacoco.xml, agregando contadores de instruction y branch.
- Cobertura global (instruction): 94.66% (372/393)
- Cobertura global (branch): 79.41% (27/34)
- Cobertura paquete com.bcp.workshop.auth (instruction): 94.66% (372/393)
- Cobertura paquete com.bcp.workshop.auth (branch): 79.41% (27/34)

Validacion de criterio REGLAS.md (>=85% en codigo modificado): Cumplido por instruction coverage (94.66%).

## Fallos

- Ningun test en rojo en la ejecucion de verify.

## Pendientes

- Ninguno bloqueante para los endpoints documentados.

## Pruebas propuestas

- Agregar test HTTP de login con JSON malformado para cerrar la pregunta abierta de docs/ENDPOINTS.md sobre comportamiento de parser/Content-Type en runtime.
- Agregar aserciones de estructura minima de checks en /q/health para validar nombre/estado del check cuando hay DOWN.

## Documentos consultados

- docs/ENDPOINTS.md
- REGLAS.md
- docs/GOOD_PRACTICES.md
- PLAN.md
- HALLAZGOS.md
- src/main/java/com/bcp/workshop/auth/AuthResource.java
- src/main/java/com/bcp/workshop/auth/AuthService.java
- src/main/resources/application.properties
- pom.xml
