package dev.formulatrainer.media;

import dev.formulatrainer.common.error.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "app.media.storage-type", havingValue = "local", matchIfMissing = true)
class FileSystemImageStorage implements ImageStorage {

    private final MediaProperties properties;

    private final ImageFileProcessor imageFileProcessor;

    FileSystemImageStorage(MediaProperties properties, ImageFileProcessor imageFileProcessor) {
        this.properties = properties;
        this.imageFileProcessor = imageFileProcessor;
    }

    @PostConstruct
    void createStorageDirectory() throws IOException {
        Files.createDirectories(properties.storagePath());
    }

    @Override
    public StoredImage store(MultipartFile file) {
        try (ImageFileProcessor.PreparedImage image = imageFileProcessor.prepare(file, properties.storagePath())) {
            String storageKey = UUID.randomUUID() + "." + image.format()
                .extension();
            Files.move(image.path(), resolve(storageKey), StandardCopyOption.ATOMIC_MOVE);
            return image.toStoredImage(storageKey);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store image file", exception);
        }
    }

    @Override
    public Resource read(String storageKey) {
        Resource resource = new FileSystemResource(resolve(storageKey));
        if (!resource.exists()) {
            throw new ResourceNotFoundException("Image not found");
        }
        return resource;
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            // Best-effort cleanup; orphan cleanup can retry later.
        }
    }

    private Path resolve(String storageKey) {
        Path resolved = properties.storagePath()
            .resolve(storageKey)
            .normalize();
        if (!resolved.startsWith(
            properties.storagePath()
                .normalize())) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return resolved;
    }
}
