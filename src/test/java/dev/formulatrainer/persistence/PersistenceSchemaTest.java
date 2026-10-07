package dev.formulatrainer.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.formulatrainer.card.Card;
import dev.formulatrainer.card.CardRepository;
import dev.formulatrainer.deck.Deck;
import dev.formulatrainer.deck.DeckRepository;
import dev.formulatrainer.media.CardSide;
import dev.formulatrainer.media.Media;
import dev.formulatrainer.media.MediaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PersistenceSchemaTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void cardCanBelongToMultipleDecks() {
        Card card = cardRepository.save(new Card("front", "back"));
        Deck firstDeck = new Deck("First deck", "One");
        Deck secondDeck = new Deck("Second deck", "Two");
        firstDeck.addCard(card);
        secondDeck.addCard(card);

        deckRepository.save(firstDeck);
        deckRepository.save(secondDeck);
        deckRepository.flush();

        Integer linkCount = jdbcTemplate.queryForObject("select count(*) from deck_cards where card_id = ?",
                Integer.class, card.getId());
        assertThat(linkCount).isEqualTo(2);
    }

    @Test
    @Transactional
    void deletingDeckDoesNotDeleteLinkedCard() {
        Card card = cardRepository.save(new Card("front", "back"));
        Deck deck = new Deck("Deck", null);
        deck.addCard(card);
        deckRepository.saveAndFlush(deck);
        UUID cardId = card.getId();

        deckRepository.delete(deck);
        deckRepository.flush();

        assertThat(cardRepository.existsById(cardId)).isTrue();
        Integer linkCount = jdbcTemplate.queryForObject("select count(*) from deck_cards", Integer.class);
        assertThat(linkCount).isZero();
    }

    @Test
    @Transactional
    void deletingCardDeletesLinkedMediaAndDeckCardLinks() {
        Card card = cardRepository.save(new Card("front", "back"));
        Deck deck = new Deck("Deck", null);
        deck.addCard(card);
        Media media = new Media(card, CardSide.FRONT, "cards/front.png", "front.png", "image/png", 128L, 16, 16,
                "front");
        card.getMedia().add(media);
        deckRepository.save(deck);
        mediaRepository.save(media);
        cardRepository.flush();
        mediaRepository.flush();
        UUID cardId = card.getId();

        cardRepository.delete(card);
        cardRepository.flush();

        Integer linkCount = jdbcTemplate.queryForObject("select count(*) from deck_cards where card_id = ?",
                Integer.class, cardId);
        Integer mediaCount = jdbcTemplate.queryForObject("select count(*) from media where card_id = ?", Integer.class,
                cardId);
        assertThat(linkCount).isZero();
        assertThat(mediaCount).isZero();
    }

    @Test
    @Transactional
    void deckPublicIdIsUnique() {
        UUID publicId = UUID.randomUUID();
        Deck firstDeck = new Deck("First", null);
        firstDeck.setPublicId(publicId);
        Deck secondDeck = new Deck("Second", null);
        secondDeck.setPublicId(publicId);

        deckRepository.saveAndFlush(firstDeck);
        deckRepository.save(secondDeck);

        assertThatThrownBy(() -> deckRepository.flush()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void mediaSideIsUniquePerCard() {
        Card card = cardRepository.save(new Card("front", "back"));
        mediaRepository.save(new Media(card, CardSide.FRONT, "one.png", "one.png", "image/png", 128L, 16, 16, null));
        mediaRepository.save(new Media(card, CardSide.FRONT, "two.png", "two.png", "image/png", 128L, 16, 16, null));

        assertThatThrownBy(() -> mediaRepository.flush()).isInstanceOf(DataIntegrityViolationException.class);
    }

}
