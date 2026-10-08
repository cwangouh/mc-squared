package dev.formulatrainer.card;

import dev.formulatrainer.card.api.CardResponse;
import dev.formulatrainer.media.CardSide;
import dev.formulatrainer.media.Media;
import dev.formulatrainer.media.api.MediaResponse;

class CardMapper {

    private CardMapper() {
    }

    static CardResponse toResponse(Card card) {
        return new CardResponse(
            card.getId(),
            card.getFrontContent(),
            card.getBackContent(),
            mediaResponse(card, CardSide.FRONT),
            mediaResponse(card, CardSide.BACK),
            card.getCreatedAt(),
            card.getUpdatedAt());
    }

    private static MediaResponse mediaResponse(Card card, CardSide side) {
        return card.getMedia()
            .stream()
            .filter(media -> media.getSide() == side)
            .findFirst()
            .map(CardMapper::mediaResponse)
            .orElse(null);
    }

    private static MediaResponse mediaResponse(Media media) {
        return new MediaResponse(media.getId(), "/api/v1/media/" + media.getId(), media.getAltText());
    }

}
