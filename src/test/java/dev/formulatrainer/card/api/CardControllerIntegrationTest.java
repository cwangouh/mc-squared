package dev.formulatrainer.card.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.formulatrainer.card.CardRepository;
import dev.formulatrainer.deck.Deck;
import dev.formulatrainer.deck.DeckRepository;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CardControllerIntegrationTest {

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
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        deckRepository.deleteAll();
        cardRepository.deleteAll();
    }

    @Test
    void anonymousUserCannotListCards() throws Exception {
        Deck deck = deckRepository.save(new Deck("Protected", null));
        Client client = new Client();

        HttpResponse<String> response = client.get("/api/v1/admin/decks/" + deck.getId() + "/cards");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void adminCanCreateListGetUpdateAndDeleteTextCard() throws Exception {
        Deck deck = deckRepository.save(new Deck("Formulas", null));
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();
        String frontContent = """
            Производная:

            $$
            f'(x) = \\lim_{h \\to 0} \\frac{f(x+h)-f(x)}{h}
            $$
            """;
        String backContent = "Ответ: скорость изменения функции. Привет, мир.";

        HttpResponse<String> createResponse = client.post(
            "/api/v1/admin/decks/" + deck.getId() + "/cards",
            cardBody(frontContent, backContent),
            csrfToken);

        assertThat(createResponse.statusCode()).isEqualTo(201);
        UUID cardId = uuidField(createResponse.body(), "id");
        assertThat(createResponse.body()).contains("\"frontImage\":null");
        assertThat(createResponse.body()).contains("\"backImage\":null");
        assertThat(escapedField(createResponse.body(), "frontContent")).isEqualTo(jsonEscaped(frontContent));
        assertThat(escapedField(createResponse.body(), "backContent")).isEqualTo(jsonEscaped(backContent));
        assertThat(
            createResponse.headers()
                .firstValue("Location"))
            .contains("/api/v1/admin/cards/" + cardId);

        HttpResponse<String> listResponse = client.get("/api/v1/admin/decks/" + deck.getId() + "/cards");
        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.body()).contains(cardId.toString());

        HttpResponse<String> getResponse = client.get("/api/v1/admin/cards/" + cardId);
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(escapedField(getResponse.body(), "frontContent")).isEqualTo(jsonEscaped(frontContent));

        String updatedFront = "Интеграл: $\\int x dx$";
        String updatedBack = "Ответ:\n$x^2 / 2 + C$";
        HttpResponse<String> updateResponse = client.put(
            "/api/v1/admin/cards/" + cardId,
            cardBody(updatedFront, updatedBack),
            csrfToken);

        assertThat(updateResponse.statusCode()).isEqualTo(200);
        assertThat(escapedField(updateResponse.body(), "frontContent")).isEqualTo(jsonEscaped(updatedFront));
        assertThat(escapedField(updateResponse.body(), "backContent")).isEqualTo(jsonEscaped(updatedBack));

        HttpResponse<String> deleteResponse = client.delete("/api/v1/admin/cards/" + cardId, csrfToken);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);
        assertThat(cardRepository.existsById(cardId)).isFalse();
        assertThat(deckCardRows()).isZero();
    }

    @Test
    void cardListUsesCreationOrderOldestFirst() throws Exception {
        Deck deck = deckRepository.save(new Deck("Ordered", null));
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();

        client.post("/api/v1/admin/decks/" + deck.getId() + "/cards", cardBody("Old front", "Old back"), csrfToken);
        client.post("/api/v1/admin/decks/" + deck.getId() + "/cards", cardBody("New front", "New back"), csrfToken);

        HttpResponse<String> response = client.get("/api/v1/admin/decks/" + deck.getId() + "/cards");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(
            response.body()
                .indexOf("\"frontContent\":\"Old front\""))
            .isLessThan(
                response.body()
                    .indexOf("\"frontContent\":\"New front\""));
    }

    @Test
    void cardIsNotCreatedForMissingDeck() throws Exception {
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();

        HttpResponse<String> response = client.post(
            "/api/v1/admin/decks/" + UUID.randomUUID() + "/cards",
            cardBody("front", "back"),
            csrfToken);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void unknownCardIdReturnsNotFound() throws Exception {
        Client client = new Client();
        client.login();

        HttpResponse<String> response = client.get("/api/v1/admin/cards/" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void blankTextSideIsRejected() throws Exception {
        Deck deck = deckRepository.save(new Deck("Validation", null));
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();

        HttpResponse<String> response = client.post(
            "/api/v1/admin/decks/" + deck.getId() + "/cards",
            cardBody("   ", "back"),
            csrfToken);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"INVALID_REQUEST\"");
        assertThat(response.body()).contains("Front content is required");
    }

    private long deckCardRows() {
        Long count = jdbcTemplate.queryForObject("select count(*) from deck_cards", Long.class);
        assertThat(count).isNotNull();
        return count;
    }

    private String cardBody(String frontContent, String backContent) {
        return """
            {
              "frontContent": "%s",
              "backContent": "%s"
            }
            """.formatted(jsonEscaped(frontContent), jsonEscaped(backContent));
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

        void login() throws Exception {
            String csrfToken = fetchCsrfToken();
            HttpResponse<String> response = post(
                "/api/v1/auth/login",
                """
                    {
                      "username": "admin",
                      "password": "admin"
                    }
                    """,
                csrfToken);
            assertThat(response.statusCode()).isEqualTo(204);
        }

        String fetchCsrfToken() throws Exception {
            HttpResponse<String> response = get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            return escapedField(response.body(), "token");
        }

        HttpResponse<String> get(String path) throws Exception {
            HttpRequest request = baseRequest(path).GET()
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String body, String csrfToken) throws Exception {
            HttpRequest request = baseRequest(path).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-CSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> put(String path, String body, String csrfToken) throws Exception {
            HttpRequest request = baseRequest(path).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-CSRF-TOKEN", csrfToken)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> delete(String path, String csrfToken) throws Exception {
            HttpRequest request = baseRequest(path).header("X-CSRF-TOKEN", csrfToken)
                .DELETE()
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        private HttpRequest.Builder baseRequest(String path) {
            return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));
        }
    }

    private static UUID uuidField(String json, String fieldName) {
        return UUID.fromString(escapedField(json, fieldName));
    }

    private static String escapedField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\":\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String jsonEscaped(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

}
