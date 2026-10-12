package dev.formulatrainer.publicdeck.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.formulatrainer.card.Card;
import dev.formulatrainer.card.CardRepository;
import dev.formulatrainer.deck.Deck;
import dev.formulatrainer.deck.DeckRepository;
import dev.formulatrainer.media.CardSide;
import dev.formulatrainer.media.Media;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PublicDeckControllerIntegrationTest {

    private static final Path mediaStoragePath = createMediaStoragePath();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.media.storage-path", () -> mediaStoragePath.toString());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabaseAndFiles() throws Exception {
        deckRepository.deleteAll();
        cardRepository.deleteAll();
        try (var files = Files.list(mediaStoragePath)) {
            files.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    @Test
    void anonymousUserCanReadPublicDeckWithCardsAndImages() throws Exception {
        Deck deck = deckRepository.saveAndFlush(new Deck("Public formulas", "Shared by link"));
        Card oldCard = cardRepository.saveAndFlush(new Card("Old front $x$", "Old back"));
        Thread.sleep(5);
        Card newCard = cardRepository.saveAndFlush(new Card("New front", ""));
        String storageKey = "public-front.png";
        Files.write(mediaStoragePath.resolve(storageKey), pngBytes());
        newCard.getMedia()
            .add(new Media(newCard, CardSide.BACK, storageKey, "front.png", "image/png", 68, 1, 1, "Formula image"));
        deck.addCard(oldCard);
        deck.addCard(newCard);
        cardRepository.saveAndFlush(newCard);
        deckRepository.saveAndFlush(deck);
        UUID publicId = deck.getPublicId();

        HttpResponse<String> response = get("/api/v1/public/decks/" + publicId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(uuidField(response.body(), "publicId")).isEqualTo(publicId);
        assertThat(response.body()).doesNotContain("\"id\":\"" + deck.getId() + "\"");
        assertThat(response.body()).contains("\"title\":\"Public formulas\"");
        assertThat(response.body()).contains("\"description\":\"Shared by link\"");
        assertThat(numberField(response.body(), "cardCount")).isEqualTo(2);
        assertThat(response.body()).contains("\"cards\":[");
        assertThat(
            response.body()
                .indexOf("\"frontContent\":\"Old front $x$\""))
            .isLessThan(
                response.body()
                    .indexOf("\"frontContent\":\"New front\""));
        assertThat(response.body()).contains("\"backContent\":\"\"");
        assertThat(response.body()).contains("\"backImage\":{");
        assertThat(response.body()).contains("\"altText\":\"Formula image\"");

        String imageUrl = stringField(response.body(), "url");
        HttpResponse<byte[]> imageResponse = getBytes(imageUrl);
        assertThat(imageResponse.statusCode()).isEqualTo(200);
        assertThat(
            imageResponse.headers()
                .firstValue("Content-Type"))
            .contains("image/png");
        assertThat(imageResponse.body()).isEqualTo(pngBytes());
    }

    @Test
    void emptyPublicDeckReturnsEmptyCardList() throws Exception {
        Deck deck = deckRepository.saveAndFlush(new Deck("Empty", null));

        HttpResponse<String> response = get("/api/v1/public/decks/" + deck.getPublicId());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"cardCount\":0");
        assertThat(response.body()).contains("\"cards\":[]");
        assertThat(response.body()).contains("\"description\":null");
    }

    @Test
    void unknownPublicIdReturnsNotFound() throws Exception {
        HttpResponse<String> response = get("/api/v1/public/decks/" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void deletedDeckPublicLinkReturnsNotFound() throws Exception {
        Deck deck = deckRepository.saveAndFlush(new Deck("Deleted", null));
        UUID publicId = deck.getPublicId();
        deckRepository.delete(deck);

        HttpResponse<String> response = get("/api/v1/public/decks/" + publicId);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = baseRequest(path).GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        HttpRequest request = baseRequest(path).GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path));
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

    private static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89,
                0x50,
                0x4e,
                0x47,
                0x0d,
                0x0a,
                0x1a,
                0x0a,
                0x00,
                0x00,
                0x00,
                0x0d,
                0x49,
                0x48,
                0x44,
                0x52,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x08,
                0x02,
                0x00,
                0x00,
                0x00,
                (byte) 0x90,
                0x77,
                0x53,
                (byte) 0xde,
                0x00,
                0x00,
                0x00,
                0x0c,
                0x49,
                0x44,
                0x41,
                0x54,
                0x08,
                (byte) 0xd7,
                0x63,
                (byte) 0xf8,
                (byte) 0xcf,
                (byte) 0xc0,
                0x00,
                0x00,
                0x03,
                0x01,
                0x01,
                0x00,
                0x18,
                (byte) 0xdd,
                (byte) 0x8d,
                (byte) 0xb0,
                0x00,
                0x00,
                0x00,
                0x00,
                0x49,
                0x45,
                0x4e,
                0x44,
                (byte) 0xae,
                0x42,
                0x60,
                (byte) 0x82};
    }

    private static Path createMediaStoragePath() {
        try {
            return Files.createTempDirectory("formula-trainer-public-media-test-");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create media storage directory", exception);
        }
    }

}
