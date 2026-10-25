package dev.formulatrainer.media;

import dev.formulatrainer.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@ConditionalOnProperty(name = "app.media.storage-type", havingValue = "s3")
class S3ImageStorage implements ImageStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3ImageStorage.class);

    private final S3Client s3Client;

    private final MediaProperties properties;

    private final ImageFileProcessor imageFileProcessor;

    S3ImageStorage(S3Client s3Client, MediaProperties properties, ImageFileProcessor imageFileProcessor) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.imageFileProcessor = imageFileProcessor;
    }

    @Override
    public StoredImage store(MultipartFile file) {
        try (ImageFileProcessor.PreparedImage image = imageFileProcessor.prepare(file, null)) {
            String storageKey = UUID.randomUUID() + "." + image.format()
                .extension();
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket())
                .key(storageKey)
                .contentType(
                    image.format()
                        .contentType())
                .build();
            s3Client.putObject(request, RequestBody.fromFile(image.path()));
            return image.toStoredImage(storageKey);
        } catch (SdkException exception) {
            throw new IllegalStateException("Failed to store image in S3", exception);
        }
    }

    @Override
    public Resource read(String storageKey) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket())
                .key(storageKey)
                .build();
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return new ByteArrayResource(response.asByteArray());
        } catch (NoSuchKeyException exception) {
            throw new ResourceNotFoundException("Image not found");
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ResourceNotFoundException("Image not found");
            }
            throw new IllegalStateException("Failed to read image from S3", exception);
        } catch (SdkException exception) {
            throw new IllegalStateException("Failed to read image from S3", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket())
                .key(storageKey)
                .build();
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            LOGGER.warn("Failed to delete image {} from S3; it may require orphan cleanup", storageKey, exception);
        }
    }

    private String bucket() {
        return properties.s3()
            .bucket();
    }
}
