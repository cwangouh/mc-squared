package dev.formulatrainer.media;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(Path storagePath, long maxSizeBytes, int maxWidthPx, int maxHeightPx) {
}
