package dev.formulatrainer.deck;

import dev.formulatrainer.deck.api.DeckResponse;

class DeckMapper {

    private DeckMapper() {
    }

    static DeckResponse toResponse(Deck deck) {
        return new DeckResponse(
            deck.getId(),
            deck.getPublicId(),
            deck.getTitle(),
            deck.getDescription(),
            deck.getCards()
                .size(),
            "/decks/" + deck.getPublicId(),
            deck.getCreatedAt(),
            deck.getUpdatedAt());
    }

}
