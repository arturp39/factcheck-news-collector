package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.dto.SourceCreateRequest;
import com.factcheck.collector.dto.SourceResponse;
import com.factcheck.collector.dto.SourceUpdateRequest;
import com.factcheck.collector.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceRepository sourceRepository;

    public List<SourceResponse> listSources() {
        return sourceRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SourceResponse createSource(SourceCreateRequest request) {
        Source source = Source.builder()
                .name(request.name())
                .type(request.type())
                .url(request.url())
                .category(request.category() != null ? request.category() : "general")
                .enabled(request.enabled() != null ? request.enabled() : true)
                .reliabilityScore(request.reliabilityScore() != null ? request.reliabilityScore() : 0.5)
                .build();

        try {
            return toResponse(sourceRepository.save(source));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Source URL already exists: " + request.url(), e);
        }
    }

    public SourceResponse updateSource(Long id, SourceUpdateRequest request) {
        Source source = sourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source not found: " + id));

        if (request.name() != null) source.setName(request.name());
        if (request.type() != null) source.setType(request.type());
        if (request.url() != null) source.setUrl(request.url());
        if (request.category() != null) source.setCategory(request.category());
        if (request.enabled() != null) source.setEnabled(request.enabled());
        if (request.reliabilityScore() != null) source.setReliabilityScore(request.reliabilityScore());

        try {
            return toResponse(sourceRepository.save(source));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Source URL already exists: " + request.url(), e);
        }
    }

    private SourceResponse toResponse(Source s) {
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
