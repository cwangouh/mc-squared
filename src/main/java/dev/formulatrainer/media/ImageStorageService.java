package dev.formulatrainer.media;

import dev.formulatrainer.common.error.PayloadTooLargeException;
import dev.formulatrainer.common.error.UnsupportedMediaFileException;
import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorageService {

    private final MediaProperties properties;

    public ImageStorageService(MediaProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void createStorageDirectory() throws IOException {
        Files.createDirectories(properties.storagePath());
    }

    StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (file.getSize() > properties.maxSizeBytes()) {
            throw new PayloadTooLargeException("Image file is too large");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(properties.storagePath(), "upload-", ".tmp");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            ImageMetadata metadata = readMetadata(tempFile);
            if (metadata.widthPx() > properties.maxWidthPx() || metadata.heightPx() > properties.maxHeightPx()) {
                throw new PayloadTooLargeException("Image dimensions are too large");
            }

            String storageKey = UUID.randomUUID() + "." + metadata.format()
                    .extension();
            Path target = resolve(storageKey);
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
            tempFile = null;

            return new StoredImage(
                    storageKey,
                    file.getOriginalFilename(),
                    metadata.format()
                            .contentType(),
                    file.getSize(),
                    metadata.widthPx(),
                    metadata.heightPx());
        } catch (PayloadTooLargeException | UnsupportedMediaFileException | IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store image file", exception);
        } finally {
            deleteIfExists(tempFile);
        }
    }

    Path path(String storageKey) {
        return resolve(storageKey);
    }

    void delete(String storageKey) {
        deleteIfExists(resolve(storageKey));
    }

    private ImageMetadata readMetadata(Path file) throws IOException {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(file.toFile())) {
            if (imageInputStream == null) {
                throw new UnsupportedMediaFileException("Unsupported image file");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new UnsupportedMediaFileException("Unsupported image file");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                ImageFormat format = ImageFormat.fromImageIoName(reader.getFormatName());
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                verifyCanDecode(reader);
                return new ImageMetadata(format, width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void verifyCanDecode(ImageReader reader) throws IOException {
        BufferedImage image = reader.read(0);
        if (image == null) {
            throw new UnsupportedMediaFileException("Unsupported image file");
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

    private static void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            // Best-effort cleanup; orphan cleanup can retry later.
        }
    }

    private record ImageMetadata(ImageFormat format, int widthPx, int heightPx) {
    }

}
