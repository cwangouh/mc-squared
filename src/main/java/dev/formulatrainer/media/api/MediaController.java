package dev.formulatrainer.media.api;

import dev.formulatrainer.card.api.CardResponse;
import dev.formulatrainer.media.CardSide;
import dev.formulatrainer.media.MediaService;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PutMapping(value = "/api/v1/admin/cards/{cardId}/images/{side}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CardResponse upload(
            @PathVariable("cardId") UUID cardId,
            @PathVariable("side") String side,
            @RequestParam("file") MultipartFile file,
            @Size(max = 500, message = "Alternative text must be at most 500 characters") @RequestParam(value = "altText", required = false) String altText) {
        return mediaService.upload(cardId, parseSide(side), file, altText);
    }

    @DeleteMapping("/api/v1/admin/cards/{cardId}/images/{side}")
    public ResponseEntity<Void> delete(
            @PathVariable("cardId") UUID cardId,
            @PathVariable("side") String side) {
        mediaService.delete(cardId, parseSide(side));
        return ResponseEntity.noContent()
                .build();
    }

    @GetMapping({ "/api/v1/media/{mediaId}", "/media/{mediaId}" })
    public ResponseEntity<Resource> read(@PathVariable("mediaId") UUID mediaId) {
        MediaService.StoredMedia media = mediaService.read(mediaId);
        return ResponseEntity.ok()
                .contentType(media.contentType())
                .body(media.resource());
    }

    private static CardSide parseSide(String side) {
        return switch (side.toLowerCase()) {
            case "front" -> CardSide.FRONT;
            case "back" -> CardSide.BACK;
            default -> throw new IllegalArgumentException("Unsupported card side");
        };
    }

}
