package dev.formulatrainer.card;

import dev.formulatrainer.card.api.CardResponse;
import dev.formulatrainer.card.api.CardSaveRequest;
import dev.formulatrainer.common.error.ResourceNotFoundException;
import dev.formulatrainer.deck.Deck;
import dev.formulatrainer.deck.DeckRepository;
import dev.formulatrainer.media.CardSide;
import dev.formulatrainer.media.MediaService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
j
@Service
public class CardService {

    private final CardRepository cardRepository;

    private final DeckRepository deckRepository;

    private final MediaService mediaService;

    public CardService(CardRepository cardRepository, DeckRepository deckRepository, MediaService mediaService) {
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
        this.mediaService = mediaService;
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

    @Transactional
    public CardResponse create(
            UUID deckId,
            CardSaveRequest request,
            MultipartFile frontImage,
            String frontImageAltText,
            MultipartFile backImage,
            String backImageAltText) {

        validateSideHasContent(request.frontContent(), frontImage, "Front content or image is required");
        validateSideHasContent(request.backContent(), backImage, "Back content or image is required");

        Deck deck = findDeck(deckId);
        Card card = cardRepository.save(new Card(request.frontContent(), request.backContent()));

        deck.addCard(card);
        if (hasFile(frontImage)) {
            mediaService.upload(card.getId(), CardSide.FRONT, frontImage, frontImageAltText);
        }
        if (hasFile(backImage)) {
            mediaService.upload(card.getId(), CardSide.BACK, backImage, backImageAltText);
        }
        return CardMapper.toResponse(findCard(card.getId()));
    }

    @Transactional(readOnly = true)
    public CardResponse get(UUID cardId) {
        return CardMapper.toResponse(findCard(cardId));
    }

    @Transactional
    public CardResponse update(UUID cardId, CardSaveRequest request) {
        Card card = findCard(cardId);
        validateSideHasContent(request.frontContent(), hasMedia(card, CardSide.FRONT), "Front side cannot be empty");
        validateSideHasContent(request.backContent(), hasMedia(card, CardSide.BACK), "Back side cannot be empty");
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

    private static void validateSideHasContent(String content, MultipartFile file, String message) {
        if (isBlank(content) && !hasFile(file)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateSideHasContent(String content, boolean hasMedia, String message) {
        if (isBlank(content) && !hasMedia) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean hasMedia(Card card, CardSide side) {
        return card.getMedia()
                .stream()
                .anyMatch(media -> media.getSide() == side);
    }

    private static boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim()
                .isEmpty();
    }

}
