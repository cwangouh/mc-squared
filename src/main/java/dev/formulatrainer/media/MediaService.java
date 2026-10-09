package dev.formulatrainer.media;

import dev.formulatrainer.card.Card;
import dev.formulatrainer.card.CardMapper;
import dev.formulatrainer.card.CardRepository;
import dev.formulatrainer.card.api.CardResponse;
import dev.formulatrainer.common.error.ResourceNotFoundException;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {

    private final CardRepository cardRepository;

    private final MediaRepository mediaRepository;

    private final ImageStorageService imageStorageService;

    public MediaService(
            CardRepository cardRepository, MediaRepository mediaRepository, ImageStorageService imageStorageService) {
        this.cardRepository = cardRepository;
        this.mediaRepository = mediaRepository;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public CardResponse upload(UUID cardId, CardSide side, MultipartFile file, String altText) {
        Card card = findCard(cardId);
        StoredImage image = imageStorageService.store(file);
        deleteFileAfterRollback(image.storageKey());
        String oldStorageKey = null;
        try {
            Media media = mediaRepository.findByCardAndSide(card, side)
                    .orElse(null);
            if (media == null) {
                media = new Media(
                        card,
                        side,
                        image.storageKey(),
                        image.originalFilename(),
                        image.contentType(),
                        image.sizeBytes(),
                        image.widthPx(),
                        image.heightPx(),
                        altText);
                card.getMedia()
                        .add(media);
                mediaRepository.save(media);
            } else {
                oldStorageKey = media.getStorageKey();
                media.replaceWith(image, altText);
            }
            deleteFileAfterCommit(oldStorageKey);
            return CardMapper.toResponse(card);
        } catch (RuntimeException exception) {
            imageStorageService.delete(image.storageKey());
            throw exception;
        }
    }

    @Transactional
    public void delete(UUID cardId, CardSide side) {
        Card card = findCard(cardId);
        Media media = mediaRepository.findByCardAndSide(card, side)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        if (side == CardSide.FRONT && isBlank(card.getFrontContent())) {
            throw new IllegalArgumentException("Front side cannot be empty");
        }
        if (side == CardSide.BACK && isBlank(card.getBackContent())) {
            throw new IllegalArgumentException("Back side cannot be empty");
        }

        card.getMedia()
                .remove(media);
        mediaRepository.delete(media);
        deleteFileAfterCommit(media.getStorageKey());
    }

    @Transactional(readOnly = true)
    public StoredMedia read(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
                
        Path path = imageStorageService.path(media.getStorageKey());
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new ResourceNotFoundException("Image not found");
        }
        return new StoredMedia(resource, MediaType.parseMediaType(media.getContentType()));
    }

    private Card findCard(UUID cardId) {
        return cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
    }

    private void deleteFileAfterCommit(String storageKey) {
        if (storageKey != null) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                imageStorageService.delete(storageKey);
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    imageStorageService.delete(storageKey);
                }

            });
        }
    }

    private void deleteFileAfterRollback(String storageKey) {
        if (storageKey != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        imageStorageService.delete(storageKey);
                    }
                }

            });
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim()
                .isEmpty();
    }

    public record StoredMedia(Resource resource, MediaType contentType) {
    }

}
