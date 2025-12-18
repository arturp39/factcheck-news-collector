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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SourceType type;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Builder.Default
    @Column(nullable = false, length = 100)
    private String category = "general";

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "reliability_score")
    private Double reliabilityScore;

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