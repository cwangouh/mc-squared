package dev.formulatrainer.media;

enum ImageFormat {

    JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp");

    private final String extension;

    private final String contentType;

    ImageFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    String extension() {
        return extension;
    }

    String contentType() {
        return contentType;
    }

    static ImageFormat fromImageIoName(String name) {
        return switch (name.toLowerCase()) {
            case "jpg", "jpeg" -> JPEG;
            case "png" -> PNG;
            case "webp", "webp-lossless", "webp-lossy" -> WEBP;
            default -> throw new IllegalArgumentException("Unsupported image format");
        };
    }

}
