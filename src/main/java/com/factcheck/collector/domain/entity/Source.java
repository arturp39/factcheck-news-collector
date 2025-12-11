package com.factcheck.collector.domain.entity;

import com.factcheck.collector.domain.enums.SourceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "sources", schema = "content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SourceType type;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String category = "general";

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "reliability_score", nullable = false)
    @Builder.Default
    private double reliabilityScore = 0.50;

    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Builder.Default
    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
