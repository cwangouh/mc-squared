package dev.formulatrainer.media;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
    StorageType storageType,
    Path storagePath,
    long maxSizeBytes,
    int maxWidthPx,
    int maxHeightPx,
    S3Properties s3) {

    public enum StorageType {
        LOCAL, S3
    }

    public record S3Properties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess) {
    }
}
