package dev.formulatrainer.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.formulatrainer.common.error.ResourceNotFoundException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageTest {

    private static final String BUCKET = "formula-trainer-test";

    @Mock
    private S3Client s3Client;

    private S3ImageStorage imageStorage;

    @BeforeEach
    void setUp() {
        MediaProperties properties = new MediaProperties(
            MediaProperties.StorageType.S3,
            Path.of("unused"),
            5 * 1024 * 1024,
            4096,
            4096,
            new MediaProperties.S3Properties(
                "https://s3.example.com",
                "region-1",
                BUCKET,
                "access-key",
                "secret-key",
                true));
        imageStorage = new S3ImageStorage(s3Client, properties, new ImageFileProcessor(properties));
    }

    @Test
    void storesValidatedImageInConfiguredBucket() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(
                PutObjectResponse.builder()
                    .build());
        MockMultipartFile file = new MockMultipartFile("file", "formula.png", "image/png", pngBytes());

        StoredImage storedImage = imageStorage.store(file);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(
            request.getValue()
                .bucket())
            .isEqualTo(BUCKET);
        assertThat(
            request.getValue()
                .key())
            .isEqualTo(storedImage.storageKey());
        assertThat(
            request.getValue()
                .contentType())
            .isEqualTo("image/png");
        assertThat(storedImage.storageKey()).endsWith(".png");
        assertThat(storedImage.originalFilename()).isEqualTo("formula.png");
        assertThat(storedImage.widthPx()).isEqualTo(16);
        assertThat(storedImage.heightPx()).isEqualTo(16);
    }

    @Test
    void readsAndDeletesImageFromConfiguredBucket() throws Exception {
        byte[] content = pngBytes();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenReturn(
                ResponseBytes.fromByteArray(
                    GetObjectResponse.builder()
                        .build(),
                    content));

        Resource resource = imageStorage.read("image.png");
        imageStorage.delete("image.png");

        assertThat(resource.getContentAsByteArray()).isEqualTo(content);
        ArgumentCaptor<GetObjectRequest> getRequest = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(getRequest.capture());
        assertThat(
            getRequest.getValue()
                .bucket())
            .isEqualTo(BUCKET);
        assertThat(
            getRequest.getValue()
                .key())
            .isEqualTo("image.png");
        verify(s3Client).deleteObject(
            DeleteObjectRequest.builder()
                .bucket(BUCKET)
                .key("image.png")
                .build());
    }

    @Test
    void reportsMissingS3ObjectAsNotFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
            .thenThrow(
                NoSuchKeyException.builder()
                    .statusCode(404)
                    .message("missing")
                    .build());

        assertThatThrownBy(() -> imageStorage.read("missing.png"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Image not found");
    }

    private static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
