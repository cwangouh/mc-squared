package dev.formulatrainer.media;

import dev.formulatrainer.card.Card;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    Optional<Media> findByStorageKey(String storageKey);

    Optional<Media> findByCardAndSide(Card card, CardSide side);

}
