package dev.formulatrainer.deck.api;

import dev.formulatrainer.deck.DeckService;
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
@RequestMapping("/api/v1/admin/decks")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @GetMapping
    public List<DeckResponse> list() {
        return deckService.list();
    }

    @PostMapping
    public ResponseEntity<DeckResponse> create(
            @Valid @RequestBody DeckCreateRequest request) {
        DeckResponse response = deckService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/decks/" + response.id())).body(response);
    }

    @GetMapping("/{deckId}")
    public DeckResponse get(@PathVariable UUID deckId) {
        return deckService.get(deckId);
    }

    @PutMapping("/{deckId}")
    public DeckResponse update(
            @PathVariable UUID deckId,
            @Valid @RequestBody DeckCreateRequest request) {
        return deckService.update(deckId, request);
    }

    @DeleteMapping("/{deckId}")
    public ResponseEntity<Void> delete(@PathVariable UUID deckId) {
        deckService.delete(deckId);
        return ResponseEntity.noContent().build();
    }

}
