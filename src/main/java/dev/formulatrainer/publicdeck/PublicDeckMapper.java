package dev.formulatrainer.publicdeck;

import dev.formulatrainer.card.Card;
import dev.formulatrainer.card.CardMapper;
import dev.formulatrainer.card.api.CardResponse;
import dev.formulatrainer.deck.Deck;
import dev.formulatrainer.publicdeck.api.PublicDeckResponse;
import java.util.Comparator;
import java.util.List;

class PublicDeckMapper {

    private static final Comparator<Card> CARD_ORDER = Comparator.comparing(Card::getCreatedAt)
        .thenComparing(Card::getId);

    private PublicDeckMapper() {
    }

    static PublicDeckResponse toResponse(Deck deck) {
        List<CardResponse> cards = deck.getCards()
            .stream()
            .sorted(CARD_ORDER)
            .map(CardMapper::toResponse)
            .toList();
        return new PublicDeckResponse(
            deck.getPublicId(),
            deck.getTitle(),
            deck.getDescription(),
            cards.size(),
            cards,
            deck.getCreatedAt(),
            deck.getUpdatedAt());
    }

}
