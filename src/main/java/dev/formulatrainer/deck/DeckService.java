package dev.formulatrainer.deck;

import dev.formulatrainer.common.error.ResourceNotFoundException;
import dev.formulatrainer.deck.api.DeckCreateRequest;
import dev.formulatrainer.deck.api.DeckResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeckService {

    private final DeckRepository deckRepository;

    public DeckService(DeckRepository deckRepository) {
        this.deckRepository = deckRepository;
    }

    @Transactional(readOnly = true)
    public List<DeckResponse> list() {
        return deckRepository.findAllByOrderByCreatedAtDescIdAsc()
            .stream()
            .map(DeckMapper::toResponse)
            .toList();
    }

    @Transactional
    public DeckResponse create(DeckCreateRequest request) {
        Deck deck = new Deck(normalizeTitle(request.title()), request.description());
        return DeckMapper.toResponse(deckRepository.save(deck));
    }

    @Transactional(readOnly = true)
    public DeckResponse get(UUID deckId) {
        return DeckMapper.toResponse(findDeck(deckId));
    }

    @Transactional
    public DeckResponse update(UUID deckId, DeckCreateRequest request) {
        Deck deck = findDeck(deckId);
        deck.setTitle(normalizeTitle(request.title()));
        deck.setDescription(request.description());
        return DeckMapper.toResponse(deck);
    }

    @Transactional
    public void delete(UUID deckId) {
        Deck deck = findDeck(deckId);
        deck.getCards()
            .clear();
        deckRepository.delete(deck);
    }

    private Deck findDeck(UUID deckId) {
        return deckRepository.findById(deckId)
            .orElseThrow(() -> new ResourceNotFoundException("Deck not found"));
    }

    private static String normalizeTitle(String title) {
        return title.trim();
    }

}
