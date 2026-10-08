package dev.formulatrainer.deck;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeckRepository extends JpaRepository<Deck, UUID> {

    @EntityGraph(attributePaths = "cards")
    java.util.List<Deck> findAllByOrderByCreatedAtDescIdAsc();

    @Override
    @EntityGraph(attributePaths = "cards")
    Optional<Deck> findById(UUID id);

    Optional<Deck> findByPublicId(UUID publicId);

}
