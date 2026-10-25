package dev.formulatrainer.media;

import dev.formulatrainer.common.error.PayloadTooLargeException;
import dev.formulatrainer.common.error.UnsupportedMediaFileException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class ImageFileProcessor {

    private final MediaProperties properties;

    ImageFileProcessor(MediaProperties properties) {
        this.properties = properties;
    }

    PreparedImage prepare(MultipartFile file, Path temporaryDirectory) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (file.getSize() > properties.maxSizeBytes()) {
            throw new PayloadTooLargeException("Image file is too large");
        }

        Path temporaryFile = null;
        try {
            temporaryFile = temporaryDirectory == null
                ? Files.createTempFile("formula-trainer-upload-", ".tmp")
                : Files.createTempFile(temporaryDirectory, "upload-", ".tmp");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }

            ImageMetadata metadata = readMetadata(temporaryFile);
            if (metadata.widthPx() > properties.maxWidthPx() || metadata.heightPx() > properties.maxHeightPx()) {
                throw new PayloadTooLargeException("Image dimensions are too large");
            }

            Path preparedFile = temporaryFile;
            temporaryFile = null;
            return new PreparedImage(
                preparedFile,
                file.getOriginalFilename(),
                metadata.format(),
                file.getSize(),
                metadata.widthPx(),
                metadata.heightPx());
        } catch (PayloadTooLargeException | UnsupportedMediaFileException | IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare image file", exception);
        } finally {
            deleteIfExists(temporaryFile);
        }
    }

    private static ImageMetadata readMetadata(Path file) throws IOException {
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

    record PreparedImage(
        Path path,
        String originalFilename,
        ImageFormat format,
        long sizeBytes,
        int widthPx,
        int heightPx) implements AutoCloseable {

        StoredImage toStoredImage(String storageKey) {
            return new StoredImage(
                storageKey,
                originalFilename,
                format.contentType(),
                sizeBytes,
                widthPx,
                heightPx);
        }

        @Override
        public void close() {
            deleteIfExists(path);
        }
    }

    private record ImageMetadata(ImageFormat format, int widthPx, int heightPx) {
    }
}
