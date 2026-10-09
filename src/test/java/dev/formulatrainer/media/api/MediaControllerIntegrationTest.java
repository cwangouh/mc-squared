package dev.formulatrainer.media.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.formulatrainer.card.CardRepository;
import dev.formulatrainer.deck.Deck;
import dev.formulatrainer.deck.DeckRepository;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
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
class MediaControllerIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void adminCanUploadReadReplaceAndDeleteImage() throws Exception {
        Deck deck = deckRepository.save(new Deck("Images", null));
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();
        UUID cardId = client.createTextCard(deck.getId(), csrfToken);

        HttpResponse<String> uploadResponse = client.putMultipart(
            "/api/v1/admin/cards/" + cardId + "/images/front",
            csrfToken,
            MultipartBody.create()
                .file("file", "front.png", "image/png", pngBytes(Color.RED))
                .field("altText", "Red formula image"));

        assertThat(uploadResponse.statusCode()).isEqualTo(200);
        UUID mediaId = frontImageId(uploadResponse.body());
        assertThat(uploadResponse.body()).contains("\"frontImage\":{");
        assertThat(uploadResponse.body()).contains("\"altText\":\"Red formula image\"");
        assertThat(storedFileCount()).isEqualTo(1);

        HttpResponse<byte[]> publicResponse = client.getBytes("/api/v1/media/" + mediaId);
        assertThat(publicResponse.statusCode()).isEqualTo(200);
        assertThat(
            publicResponse.headers()
                .firstValue("Content-Type"))
            .contains("image/png");
        assertThat(publicResponse.body()).isNotEmpty();

        HttpResponse<String> replaceResponse = client.putMultipart(
            "/api/v1/admin/cards/" + cardId + "/images/front",
            csrfToken,
            MultipartBody.create()
                .file("file", "../path-injection.png", "image/png", pngBytes(Color.BLUE))
                .field("altText", "Blue image"));

        assertThat(replaceResponse.statusCode()).isEqualTo(200);
        assertThat(frontImageId(replaceResponse.body())).isEqualTo(mediaId);
        assertThat(replaceResponse.body()).contains("\"altText\":\"Blue image\"");
        assertThat(storedFileCount()).isEqualTo(1);

        HttpResponse<String> deleteResponse = client.delete(
            "/api/v1/admin/cards/" + cardId + "/images/front",
            csrfToken);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);
        assertThat(storedFileCount()).isZero();

        HttpResponse<String> deletedMediaResponse = client.get("/api/v1/media/" + mediaId);
        assertThat(deletedMediaResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void multipartCreateAllowsImageOnlySideAndPreventsDeletingItsOnlyContent() throws Exception {
        Deck deck = deckRepository.save(new Deck("Image-only", null));
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();

        HttpResponse<String> createResponse = client.postMultipart(
            "/api/v1/admin/decks/" + deck.getId() + "/cards",
            csrfToken,
            MultipartBody.create()
                .json("card", """
                    {
                      "frontContent": "",
                      "backContent": "Back text"
                    }
                    """)
                .file("frontImage", "front.png", "image/png", pngBytes(Color.GREEN))
                .field("frontImageAltText", "Only front content"));

        assertThat(createResponse.statusCode()).isEqualTo(201);
        UUID cardId = uuidField(createResponse.body(), "id");
        assertThat(createResponse.body()).contains("\"frontContent\":\"\"");
        assertThat(createResponse.body()).contains("\"frontImage\":{");

        HttpResponse<String> deleteResponse = client.delete(
            "/api/v1/admin/cards/" + cardId + "/images/front",
            csrfToken);

        assertThat(deleteResponse.statusCode()).isEqualTo(400);
        assertThat(deleteResponse.body()).contains("\"code\":\"INVALID_REQUEST\"");
        assertThat(storedFileCount()).isEqualTo(1);
    }

    @Test
    void corruptedImageIsRejected() throws Exception {
        Deck deck = deckRepository.save(new Deck("Broken", null));
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();
        UUID cardId = client.createTextCard(deck.getId(), csrfToken);

        HttpResponse<String> response = client.putMultipart(
            "/api/v1/admin/cards/" + cardId + "/images/front",
            csrfToken,
            MultipartBody.create()
                .file("file", "broken.png", "image/png", "not-an-image".getBytes(StandardCharsets.UTF_8)));

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).contains("\"code\":\"UNSUPPORTED_MEDIA_TYPE\"");
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void oversizedImageIsRejectedBeforeDecode() throws Exception {
        Deck deck = deckRepository.save(new Deck("Big", null));
        Client client = new Client();
        client.login();
        String csrfToken = client.fetchCsrfToken();
        UUID cardId = client.createTextCard(deck.getId(), csrfToken);

        HttpResponse<String> response = client.putMultipart(
            "/api/v1/admin/cards/" + cardId + "/images/front",
            csrfToken,
            MultipartBody.create()
                .file("file", "too-large.png", "image/png", new byte[5_242_881]));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
        assertThat(storedFileCount()).isZero();
    }

    private int storedFileCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from media", Integer.class);
        assertThat(count).isNotNull();
        return count;
    }

    private static byte[] pngBytes(Color color) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color.getRGB());
        image.setRGB(1, 0, color.getRGB());
        image.setRGB(0, 1, color.getRGB());
        image.setRGB(1, 1, color.getRGB());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
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
            HttpResponse<String> response = postJson(
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

        UUID createTextCard(UUID deckId, String csrfToken) throws Exception {
            HttpResponse<String> response = postJson(
                "/api/v1/admin/decks/" + deckId + "/cards",
                """
                    {
                      "frontContent": "Front text",
                      "backContent": "Back text"
                    }
                    """,
                csrfToken);
            assertThat(response.statusCode()).isEqualTo(201);
            return uuidField(response.body(), "id");
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

        HttpResponse<byte[]> getBytes(String path) throws Exception {
            HttpRequest request = baseRequest(path).GET()
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }

        HttpResponse<String> postJson(String path, String body, String csrfToken) throws Exception {
            HttpRequest request = baseRequest(path).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-CSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> postMultipart(String path, String csrfToken, MultipartBody body) throws Exception {
            HttpRequest request = baseRequest(path).header("Content-Type", body.contentType())
                .header("X-CSRF-TOKEN", csrfToken)
                .POST(body.publisher())
                .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> putMultipart(String path, String csrfToken, MultipartBody body) throws Exception {
            HttpRequest request = baseRequest(path).header("Content-Type", body.contentType())
                .header("X-CSRF-TOKEN", csrfToken)
                .PUT(body.publisher())
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

    private static class MultipartBody {

        private final String boundary = "----formula-trainer-" + UUID.randomUUID();

        private final List<byte[]> parts = new ArrayList<>();

        static MultipartBody create() {
            return new MultipartBody();
        }

        MultipartBody field(String name, String value) {
            parts.add(
                ("""
                    --%s\r
                    Content-Disposition: form-data; name="%s"\r
                    \r
                    %s\r
                    """).formatted(boundary, name, value)
                    .getBytes(StandardCharsets.UTF_8));
            return this;
        }

        MultipartBody json(String name, String value) {
            parts.add(
                ("""
                    --%s\r
                    Content-Disposition: form-data; name="%s"\r
                    Content-Type: application/json\r
                    \r
                    %s\r
                    """).formatted(boundary, name, value)
                    .getBytes(StandardCharsets.UTF_8));
            return this;
        }

        MultipartBody file(String name, String filename, String contentType, byte[] bytes) {
            parts.add(
                ("""
                    --%s\r
                    Content-Disposition: form-data; name="%s"; filename="%s"\r
                    Content-Type: %s\r
                    \r
                    """).formatted(boundary, name, filename, contentType)
                    .getBytes(StandardCharsets.UTF_8));
            parts.add(bytes);
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
            return this;
        }

        HttpRequest.BodyPublisher publisher() {
            List<byte[]> allParts = new ArrayList<>(parts);
            allParts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return HttpRequest.BodyPublishers.ofByteArrays(allParts);
        }

        String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

    }

    private static UUID uuidField(String json, String fieldName) {
        return UUID.fromString(stringField(json, fieldName));
    }

    private static UUID frontImageId(String json) {
        Pattern pattern = Pattern.compile("\"frontImage\":\\{\"id\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return UUID.fromString(matcher.group(1));
    }

    private static String stringField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static Path createMediaStoragePath() {
        try {
            return Files.createTempDirectory("formula-trainer-media-test-");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create media storage directory", exception);
        }
    }

}
