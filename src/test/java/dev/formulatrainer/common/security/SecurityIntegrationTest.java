package dev.formulatrainer.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Import(SecurityIntegrationTest.AdminProbeController.class)
class SecurityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private LoginAttemptLimiter loginAttemptLimiter;

    @BeforeEach
    void resetLoginLimiter() {
        loginAttemptLimiter.clear();
    }

    @Test
    void anonymousUserCannotReadCurrentAdmin() throws Exception {
        Client client = new Client();

        HttpResponse<String> response = client.get("/api/v1/auth/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void modifyingRequestWithoutCsrfIsForbidden() throws Exception {
        Client client = new Client();

        HttpResponse<String> response = client.post("/api/v1/auth/login", loginBody("admin", "admin"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void anonymousUserWithCsrfStillCannotCallAdminEndpoint() throws Exception {
        Client client = new Client();
        String csrfToken = client.fetchCsrfToken();

        HttpResponse<String> response = client.post("/api/v1/admin/probe", "{}", csrfToken);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void invalidPasswordIsRejected() throws Exception {
        Client client = new Client();
        String csrfToken = client.fetchCsrfToken();

        HttpResponse<String> response = client.post("/api/v1/auth/login", loginBody("admin", "wrong"), csrfToken);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void failedLoginAttemptsAreRateLimited() throws Exception {
        Client client = new Client();
        String csrfToken = client.fetchCsrfToken();
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpResponse<String> response = client.post("/api/v1/auth/login", loginBody("admin", "wrong"), csrfToken);
            assertThat(response.statusCode()).isEqualTo(401);
        }

        HttpResponse<String> response = client.post("/api/v1/auth/login", loginBody("admin", "wrong"), csrfToken);

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.body()).contains("\"code\":\"LOGIN_RATE_LIMITED\"");
    }

    @Test
    void adminCanLoginCallProtectedEndpointAndLogout() throws Exception {
        Client client = new Client();
        String csrfToken = client.fetchCsrfToken();

        HttpResponse<String> loginResponse = client.post("/api/v1/auth/login", loginBody("admin", "admin"), csrfToken);

        assertThat(loginResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> meResponse = client.get("/api/v1/auth/me");
        assertThat(meResponse.statusCode()).isEqualTo(200);
        assertThat(meResponse.body()).contains("\"username\":\"admin\"");

        HttpResponse<String> missingCsrfResponse = client.post("/api/v1/admin/probe", "{}");
        assertThat(missingCsrfResponse.statusCode()).isEqualTo(403);

        String freshCsrfToken = client.fetchCsrfToken();
        HttpResponse<String> adminResponse = client.post("/api/v1/admin/probe", "{}", freshCsrfToken);
        assertThat(adminResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> logoutResponse = client.post("/api/v1/auth/logout", "{}", freshCsrfToken);
        assertThat(logoutResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> afterLogoutResponse = client.get("/api/v1/auth/me");
        assertThat(afterLogoutResponse.statusCode()).isEqualTo(401);
    }

    @Test
    void publicReadPathDoesNotRequireAuthentication() throws Exception {
        Client client = new Client();

        HttpResponse<String> response = client.get("/api/v1/public/decks/not-yet-implemented");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("\"code\":\"UNAUTHORIZED\"");
    }

    private String loginBody(String username, String password) {
        return """
            {
              "username": "%s",
              "password": "%s"
            }
            """.formatted(username, password);
    }

    private class Client {

        private final HttpClient httpClient;

        Client() {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .build();
        }

        String fetchCsrfToken() throws Exception {
            HttpResponse<String> response = get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"headerName\":\"X-CSRF-TOKEN\"");
            return jsonStringField(response.body(), "token");
        }

        HttpResponse<String> get(String path) throws Exception {
            HttpRequest request = baseRequest(path).GET()
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String body) throws Exception {
            HttpRequest request = baseRequest(path).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(
                    HttpRequest.BodyPublishers.ofString(body))
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String body, String csrfToken) throws Exception {
            HttpRequest request = baseRequest(path).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header(
                    "X-CSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        private HttpRequest.Builder baseRequest(String path) {
            return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));
        }
    }

    private static String jsonStringField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @RestController
    @RequestMapping("/api/v1/admin/probe")
    static class AdminProbeController {

        @PostMapping
        ResponseEntity<Void> post() {
            return ResponseEntity.noContent()
                .build();
        }

        @GetMapping
        void get() {
        }
    }

}
