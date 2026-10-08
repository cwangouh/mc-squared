package dev.formulatrainer.card;

import dev.formulatrainer.card.api.CardResponse;
import dev.formulatrainer.card.api.CardSaveRequest;
import dev.formulatrainer.common.error.ResourceNotFoundException;
import dev.formulatrainer.deck.Deck;
import dev.formulatrainer.deck.DeckRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardService {

    private final CardRepository cardRepository;

    private final DeckRepository deckRepository;

    public CardService(CardRepository cardRepository, DeckRepository deckRepository) {
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
    }

    @Transactional(readOnly = true)
    public List<CardResponse> list(UUID deckId) {
        ensureDeckExists(deckId);
        return cardRepository.findAllByDeckIdOrderByCreatedAtAscIdAsc(deckId)
            .stream()
            .map(CardMapper::toResponse)
            .toList();
    }

    @Transactional
    public CardResponse create(UUID deckId, CardSaveRequest request) {
        validateTextSides(request);
        Deck deck = findDeck(deckId);
        Card card = cardRepository.save(new Card(request.frontContent(), request.backContent()));
        deck.addCard(card);
        return CardMapper.toResponse(card);
    }

    @Transactional(readOnly = true)
    public CardResponse get(UUID cardId) {
        return CardMapper.toResponse(findCard(cardId));
    }

    @Transactional
    public CardResponse update(UUID cardId, CardSaveRequest request) {
        validateTextSides(request);
        Card card = findCard(cardId);
        card.setFrontContent(request.frontContent());
        card.setBackContent(request.backContent());
        return CardMapper.toResponse(card);
    }

    @Transactional
    public void delete(UUID cardId) {
        Card card = findCard(cardId);
        cardRepository.delete(card);
    }

    private void ensureDeckExists(UUID deckId) {
        if (!deckRepository.existsById(deckId)) {
            throw new ResourceNotFoundException("Deck not found");
        }
    }

    private Deck findDeck(UUID deckId) {
        return deckRepository.findById(deckId)
            .orElseThrow(() -> new ResourceNotFoundException("Deck not found"));
    }

    private Card findCard(UUID cardId) {
        return cardRepository.findById(cardId)
            .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
    }

    private static void validateTextSides(CardSaveRequest request) {
        if (isBlank(request.frontContent())) {
            throw new IllegalArgumentException("Front content is required");
        }
        if (isBlank(request.backContent())) {
            throw new IllegalArgumentException("Back content is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim()
            .isEmpty();
    }

}
