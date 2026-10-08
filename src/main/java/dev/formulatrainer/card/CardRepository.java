package dev.formulatrainer.card;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRepository extends JpaRepository<Card, UUID> {

    @Query("""
        select card
        from Deck deck
        join deck.cards card
        where deck.id = :deckId
        order by card.createdAt asc, card.id asc
        """)
    @EntityGraph(attributePaths = "media")
    List<Card> findAllByDeckIdOrderByCreatedAtAscIdAsc(@Param("deckId")
    UUID deckId);

    @Override
    @EntityGraph(attributePaths = {"decks", "media"})
    Optional<Card> findById(UUID id);

}
