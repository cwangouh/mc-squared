package dev.formulatrainer.card.api;

import dev.formulatrainer.card.CardService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/api/v1/admin/decks/{deckId}/cards")
    public List<CardResponse> list(@PathVariable("deckId") UUID deckId) {
        return cardService.list(deckId);
    }

    @PostMapping("/api/v1/admin/decks/{deckId}/cards")
    public ResponseEntity<CardResponse> create(
            @PathVariable("deckId") UUID deckId,
            @Valid @RequestBody CardSaveRequest request) {
        CardResponse response = cardService.create(deckId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/cards/" + response.id()))
                .body(response);
    }

    @PostMapping(value = "/api/v1/admin/decks/{deckId}/cards", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CardResponse> createMultipart(
            @PathVariable("deckId") UUID deckId,
            @Valid @RequestPart("card") CardSaveRequest request,
            @RequestPart(value = "frontImage", required = false) MultipartFile frontImage,
            @RequestParam(value = "frontImageAltText", required = false) String frontImageAltText,
            @RequestPart(value = "backImage", required = false) MultipartFile backImage,
            @RequestParam(value = "backImageAltText", required = false) String backImageAltText) {
        CardResponse response = cardService.create(
                deckId, request, frontImage, frontImageAltText, backImage, backImageAltText);
        return ResponseEntity.created(URI.create("/api/v1/admin/cards/" + response.id()))
                .body(response);
    }

    @GetMapping("/api/v1/admin/cards/{cardId}")
    public CardResponse get(@PathVariable("cardId") UUID cardId) {
        return cardService.get(cardId);
    }

    @PutMapping("/api/v1/admin/cards/{cardId}")
    public CardResponse update(
            @PathVariable("cardId") UUID cardId,
            @Valid @RequestBody CardSaveRequest request) {
        return cardService.update(cardId, request);
    }

    @DeleteMapping("/api/v1/admin/cards/{cardId}")
    public ResponseEntity<Void> delete(@PathVariable("cardId") UUID cardId) {
        cardService.delete(cardId);
        return ResponseEntity.noContent()
                .build();
    }

}
