package dev.formulatrainer.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import dev.formulatrainer.card.api.CardSaveRequest;
import dev.formulatrainer.deck.api.DeckCreateRequest;
import dev.formulatrainer.media.api.MediaUploadRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.user.name=test-admin", "spring.security.user.password=test-password"})
@ActiveProfiles("test")
@Testcontainers
@Import(ApiErrorContractTest.ContractProbeController.class)
class ApiErrorContractTest {

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

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void invalidDeckRequestUsesUnifiedFieldErrorFormat() throws Exception {
        String body = """
            {
              "title": "",
              "description": "%s"
            }
            """.formatted("x".repeat(2001));

        HttpResponse<String> response = post("/api/v1/test/decks", body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"status\":400");
        assertThat(response.body()).contains("\"code\":\"VALIDATION_ERROR\"");
        assertThat(response.body()).contains("\"field\":\"description\"");
        assertThat(response.body()).contains("\"field\":\"title\"");
        assertThat(response.body()).doesNotContain("MethodArgumentNotValidException");
    }

    @Test
    void invalidCardContentLengthIsReportedAsFieldError() throws Exception {
        String body = """
            {
              "frontContent": "%s",
              "backContent": "ok"
            }
            """.formatted("x".repeat(20001));

        HttpResponse<String> response = post("/api/v1/test/cards", body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_ERROR\"");
        assertThat(response.body()).contains("\"field\":\"frontContent\"");
    }

    @Test
    void invalidMediaAltTextLengthIsReportedAsFieldError() throws Exception {
        String body = """
            {
              "altText": "%s"
            }
            """.formatted("x".repeat(501));

        HttpResponse<String> response = put("/api/v1/test/media", body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_ERROR\"");
        assertThat(response.body()).contains("\"field\":\"altText\"");
    }

    @Test
    void invalidUuidPathVariableUsesUnifiedErrorFormat() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/decks/not-a-uuid");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"status\":400");
        assertThat(response.body()).contains("\"code\":\"INVALID_REQUEST\"");
        assertThat(response.body()).doesNotContain("MethodArgumentTypeMismatchException");
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalDetails() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/unexpected");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("\"code\":\"INTERNAL_ERROR\"");
        assertThat(response.body()).doesNotContain("IllegalStateException");
        assertThat(response.body()).doesNotContain("stackTrace");
    }

    @Test
    void openApiDescriptionIsAvailable() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"openapi\"");
        assertThat(response.body()).contains("\"title\":\"Formula Trainer API\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = baseRequest(path).GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = baseRequest(path).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .POST(
                HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body) throws Exception {
        HttpRequest request = baseRequest(path).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .PUT(
                HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header(
                "Authorization", basicAuthHeader());
    }

    private String basicAuthHeader() {
        String credentials = "test-admin:test-password";
        String encoded = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class ContractProbeController {

        @PostMapping("/decks")
        void validateDeck(
            @Valid
            @RequestBody
            DeckCreateRequest request) {
        }

        @PostMapping("/cards")
        void validateCard(
            @Valid
            @RequestBody
            CardSaveRequest request) {
        }

        @PutMapping("/media")
        void validateMedia(
            @Valid
            @RequestBody
            MediaUploadRequest request) {
        }

        @GetMapping("/decks/{deckId}")
        UUID deck(@PathVariable
        UUID deckId) {
            return deckId;
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("Sensitive internal failure");
        }

    }

}
