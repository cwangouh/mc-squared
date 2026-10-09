package dev.formulatrainer.media;

record StoredImage(
    String storageKey,
    String originalFilename,
    String contentType,
    long sizeBytes,
    int widthPx,
    int heightPx) {
}
