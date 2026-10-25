package dev.formulatrainer.media;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

interface ImageStorage {

    StoredImage store(MultipartFile file);

    Resource read(String storageKey);

    void delete(String storageKey);
}
