package dev.formulatrainer.card.api;

import dev.formulatrainer.card.CardService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/api/v1/admin/decks/{deckId}/cards")
    public List<CardResponse> list(@PathVariable
    UUID deckId) {
        return cardService.list(deckId);
    }

    @PostMapping("/api/v1/admin/decks/{deckId}/cards")
    public ResponseEntity<CardResponse> create(
        @PathVariable
        UUID deckId,
        @Valid
        @RequestBody
        CardSaveRequest request) {
        CardResponse response = cardService.create(deckId, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/cards/" + response.id()))
            .body(response);
    }

    @GetMapping("/api/v1/admin/cards/{cardId}")
    public CardResponse get(@PathVariable
    UUID cardId) {
        return cardService.get(cardId);
    }

    @PutMapping("/api/v1/admin/cards/{cardId}")
    public CardResponse update(
        @PathVariable
        UUID cardId,
        @Valid
        @RequestBody
        CardSaveRequest request) {
        return cardService.update(cardId, request);
    }

    @DeleteMapping("/api/v1/admin/cards/{cardId}")
    public ResponseEntity<Void> delete(@PathVariable
    UUID cardId) {
        cardService.delete(cardId);
        return ResponseEntity.noContent()
            .build();
    }

}
