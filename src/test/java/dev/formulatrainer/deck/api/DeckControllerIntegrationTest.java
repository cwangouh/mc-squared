package dev.formulatrainer.deck.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.formulatrainer.card.Card;
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
class DeckControllerIntegrationTest {

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
    void anonymousUserCannotListDecks() throws Exception {
        Client client = new Client();

        HttpResponse<String> response = client.get("/api/v1/admin/decks");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void adminCanCreateListGetUpdateAndDeleteDeck() throws Exception {
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();

        HttpResponse<String> createResponse = client.post(
            "/api/v1/admin/decks",
            deckBody("  Limits  ", "Important formulas"),
            csrfToken);

        assertThat(createResponse.statusCode()).isEqualTo(201);
        UUID deckId = uuidField(createResponse.body(), "id");
        UUID publicId = uuidField(createResponse.body(), "publicId");
        assertThat(stringField(createResponse.body(), "title")).isEqualTo("Limits");
        assertThat(stringField(createResponse.body(), "description")).isEqualTo("Important formulas");
        assertThat(numberField(createResponse.body(), "cardCount")).isZero();
        assertThat(stringField(createResponse.body(), "publicPath")).isEqualTo("/decks/" + publicId);
        assertThat(
            createResponse.headers()
                .firstValue("Location"))
            .contains("/api/v1/admin/decks/" + deckId);

        HttpResponse<String> getResponse = client.get("/api/v1/admin/decks/" + deckId);
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(uuidField(getResponse.body(), "publicId")).isEqualTo(publicId);

        HttpResponse<String> updateResponse = client.put(
            "/api/v1/admin/decks/" + deckId,
            deckBody("Derivatives", null),
            csrfToken);

        assertThat(updateResponse.statusCode()).isEqualTo(200);
        assertThat(stringField(updateResponse.body(), "title")).isEqualTo("Derivatives");
        assertThat(updateResponse.body()).contains("\"description\":null");
        assertThat(uuidField(updateResponse.body(), "publicId")).isEqualTo(publicId);
        assertThat(stringField(updateResponse.body(), "publicPath")).isEqualTo("/decks/" + publicId);

        HttpResponse<String> listResponse = client.get("/api/v1/admin/decks");
        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.body()).contains("\"title\":\"Derivatives\"");

        HttpResponse<String> deleteResponse = client.delete("/api/v1/admin/decks/" + deckId, csrfToken);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> afterDeleteResponse = client.get("/api/v1/admin/decks/" + deckId);
        assertThat(afterDeleteResponse.statusCode()).isEqualTo(404);
        assertThat(afterDeleteResponse.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void listReturnsNewestDecksFirst() throws Exception {
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();

        client.post("/api/v1/admin/decks", deckBody("Old", null), csrfToken);
        client.post("/api/v1/admin/decks", deckBody("New", null), csrfToken);

        HttpResponse<String> response = client.get("/api/v1/admin/decks");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(
            response.body()
                .indexOf("\"title\":\"New\""))
            .isLessThan(
                response.body()
                    .indexOf("\"title\":\"Old\""));
    }

    @Test
    void unknownDeckIdReturnsNotFound() throws Exception {
        Client client = new Client();
        client.login();

        HttpResponse<String> response = client.get("/api/v1/admin/decks/" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void deletingDeckKeepsCardsAndRemovesOnlyDeckRelation() throws Exception {
        Deck deck = new Deck("Shared cards", null);
        Card card = cardRepository.save(new Card("front", "back"));
        deck.addCard(card);
        deckRepository.saveAndFlush(deck);
        UUID deckId = deck.getId();
        UUID cardId = card.getId();

        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();

        HttpResponse<String> response = client.delete("/api/v1/admin/decks/" + deckId, csrfToken);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(cardRepository.existsById(cardId)).isTrue();
        assertThat(deckCardRows()).isZero();
    }

    private long deckCardRows() {
        Long count = jdbcTemplate.queryForObject("select count(*) from deck_cards", Long.class);
        assertThat(count).isNotNull();
        return count;
    }

    private String deckBody(String title, String description) {
        String descriptionJson = description == null ? "null" : "\"%s\"".formatted(description);
        return """
            {
              "title": "%s",
              "description": %s
            }
            """.formatted(title, descriptionJson);
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
            return stringField(response.body(), "token");
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
        return UUID.fromString(stringField(json, fieldName));
    }

    private static String stringField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static long numberField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\":(\\d+)");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

}
