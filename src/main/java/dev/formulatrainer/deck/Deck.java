package dev.formulatrainer.deck;

import dev.formulatrainer.card.Card;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "decks", uniqueConstraints = @UniqueConstraint(name = "uk_decks_public_id", columnNames = "public_id"))
public class Deck {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "public_id", nullable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "deck_cards", joinColumns = @JoinColumn(name = "deck_id"), inverseJoinColumns = @JoinColumn(name = "card_id"))
    private Set<Card> cards = new LinkedHashSet<>();

    protected Deck() {
    }

    public Deck(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void addCard(Card card) {
        cards.add(card);
        card.getDecks().add(this);
    }

    public void removeCard(Card card) {
        cards.remove(card);
        card.getDecks().remove(this);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public void setPublicId(UUID publicId) {
        this.publicId = publicId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Card> getCards() {
        return cards;
    }
}
