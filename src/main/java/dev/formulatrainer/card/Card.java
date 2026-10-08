package dev.formulatrainer.card;

import dev.formulatrainer.deck.Deck;
import dev.formulatrainer.media.Media;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "front_content", nullable = false, columnDefinition = "text")
    private String frontContent = "";

    @Column(name = "back_content", nullable = false, columnDefinition = "text")
    private String backContent = "";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(mappedBy = "cards", fetch = LAZY)
    private Set<Deck> decks = new LinkedHashSet<>();

    @OneToMany(mappedBy = "card", cascade = ALL, orphanRemoval = true, fetch = LAZY)
    private List<Media> media = new ArrayList<>();

    protected Card() {
    }

    public Card(String frontContent, String backContent) {
        this.frontContent = frontContent == null ? "" : frontContent;
        this.backContent = backContent == null ? "" : backContent;
    }

    public UUID getId() {
        return id;
    }

    public String getFrontContent() {
        return frontContent;
    }

    public void setFrontContent(String frontContent) {
        this.frontContent = frontContent == null ? "" : frontContent;
    }

    public String getBackContent() {
        return backContent;
    }

    public void setBackContent(String backContent) {
        this.backContent = backContent == null ? "" : backContent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Deck> getDecks() {
        return decks;
    }

    public List<Media> getMedia() {
        return media;
    }

    @PreRemove
    private void removeFromDecks() {
        for (Deck deck : new HashSet<>(decks)) {
            deck.getCards()
                .remove(this);
        }
        decks.clear();
    }

}
