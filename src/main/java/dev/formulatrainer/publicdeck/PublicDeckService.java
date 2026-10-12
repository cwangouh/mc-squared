package dev.formulatrainer.publicdeck;

import dev.formulatrainer.common.error.ResourceNotFoundException;
import dev.formulatrainer.deck.Deck;
import dev.formulatrainer.deck.DeckRepository;
import dev.formulatrainer.publicdeck.api.PublicDeckResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicDeckService {

    private final DeckRepository deckRepository;

    public PublicDeckService(DeckRepository deckRepository) {
        this.deckRepository = deckRepository;
    }

    @Transactional(readOnly = true)
    public PublicDeckResponse get(String publicId) {
        Deck deck = deckRepository.findByPublicId(parsePublicId(publicId))
            .orElseThrow(() -> new ResourceNotFoundException("Deck not found"));
        return PublicDeckMapper.toResponse(deck);
    }

    private static UUID parsePublicId(String publicId) {
        try {
            return UUID.fromString(publicId);
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Deck not found");
        }
    }

}
