package com.factcheck.collector.controller;

import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.repository.SourceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/admin/sources")
@RequiredArgsConstructor
public class SourceAdminController {

    private final SourceRepository sourceRepository;

    @GetMapping
    public ResponseEntity<List<SourceResponse>> listSources() {
        List<SourceResponse> sources = sourceRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(SourceResponse::from)
                .toList();
        return ResponseEntity.ok(sources);
    }

    @PostMapping
    public ResponseEntity<SourceResponse> createSource(@RequestBody @Valid SourceCreateRequest request) {
        Source source = Source.builder()
                .name(request.name())
                .type(request.type())
                .url(request.url())
                .category(request.category() != null ? request.category() : "general")
                .enabled(request.enabled() != null ? request.enabled() : true)
                .reliabilityScore(request.reliabilityScore() != null ? request.reliabilityScore() : 0.5)
                .build();

        try {
            Source saved = sourceRepository.save(source);
            return ResponseEntity.ok(SourceResponse.from(saved));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Source URL already exists: " + request.url());
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SourceResponse> updateSource(
            @PathVariable("id") Long id,
            @RequestBody @Valid SourceUpdateRequest request
    ) {
        Source source = sourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + id));

        if (request.name() != null) source.setName(request.name());
        if (request.type() != null) source.setType(request.type());
        if (request.url() != null) source.setUrl(request.url());
        if (request.category() != null) source.setCategory(request.category());
        if (request.enabled() != null) source.setEnabled(request.enabled());
        if (request.reliabilityScore() != null) source.setReliabilityScore(request.reliabilityScore());

        try {
            Source saved = sourceRepository.save(source);
            return ResponseEntity.ok(SourceResponse.from(saved));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Source URL already exists: " + request.url());
        }
    }

    public record SourceCreateRequest(
            @NotBlank String name,
            @NotNull SourceType type,
            @NotBlank String url,
            String category,
            Boolean enabled,
            @Min(0) @Max(1) Double reliabilityScore
    ) {}

    public record SourceUpdateRequest(
            String name,
            SourceType type,
            String url,
            String category,
            Boolean enabled,
            @Min(0) @Max(1) Double reliabilityScore
    ) {}

    public record SourceResponse(
            Long id,
            String name,
            SourceType type,
            String url,
            String category,
            boolean enabled,
            double reliabilityScore,
            Instant lastFetchedAt,
            Instant lastSuccessAt,
            int failureCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static SourceResponse from(Source s) {
            return new SourceResponse(
                    s.getId(),
                    s.getName(),
                    s.getType(),
                    s.getUrl(),
                    s.getCategory(),
                    s.isEnabled(),
                    s.getReliabilityScore(),
                    s.getLastFetchedAt(),
                    s.getLastSuccessAt(),
                    s.getFailureCount(),
                    s.getCreatedAt(),
                    s.getUpdatedAt()
            );
        }
    }
}

