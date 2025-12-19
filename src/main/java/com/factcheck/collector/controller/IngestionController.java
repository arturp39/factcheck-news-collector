package com.factcheck.collector.controller;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;
    private final IngestionLogRepository ingestionLogRepository;

    @PostMapping("/run")
    public ResponseEntity<String> runIngestion(
            @RequestParam(required = false) String correlationId
    ) {
        String cid = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        log.info("Manual ingestion trigger, correlationId={}", cid);
        ingestionService.ingestAllSources(cid);

        return ResponseEntity.ok("Ingestion started, correlationId=" + cid);
    }

    @PostMapping("/run/{sourceId}")
    public ResponseEntity<String> runIngestionForSource(
            @PathVariable("sourceId") Long sourceId,
            @RequestParam(required = false) String correlationId
    ) {
        String cid = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        log.info("Manual ingestion trigger for sourceId={}, correlationId={}", sourceId, cid);
        ingestionService.ingestSource(sourceId, cid);

        return ResponseEntity.ok("Ingestion started for sourceId=" + sourceId + ", correlationId=" + cid);
    }

    @GetMapping("/logs")
    public ResponseEntity<IngestionLogPageResponse> listLogs(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }

        Page<IngestionLog> result = ingestionLogRepository.findAll(PageRequest.of(page, size));
        return ResponseEntity.ok(IngestionLogPageResponse.from(result, page, size));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<IngestionRunResponse> getRun(@PathVariable("id") Long runId) {
        IngestionLog run = ingestionLogRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion run not found: " + runId));
        return ResponseEntity.ok(IngestionRunResponse.from(run));
    }

    public record IngestionLogPageResponse(
            int page,
            int size,
            long totalElements,
            int totalPages,
            java.util.List<IngestionRunResponse> items
    ) {
        static IngestionLogPageResponse from(Page<IngestionLog> pageResult, int page, int size) {
            var items = pageResult.getContent().stream().map(IngestionRunResponse::from).toList();
            return new IngestionLogPageResponse(page, size, pageResult.getTotalElements(), pageResult.getTotalPages(), items);
        }
    }

    public record IngestionRunResponse(
            Long id,
            Long sourceId,
            String sourceName,
            Instant startedAt,
            Instant completedAt,
            int articlesFetched,
            int articlesProcessed,
            int articlesFailed,
            String status,
            String errorDetails,
            String correlationId
    ) {
        static IngestionRunResponse from(IngestionLog log) {
            Source source = log.getSource();
            Long sourceId = source != null ? source.getId() : null;
            String sourceName = source != null ? source.getName() : null;
            String status = log.getStatus() != null ? log.getStatus().name() : null;

            return new IngestionRunResponse(
                    log.getId(),
                    sourceId,
                    sourceName,
                    log.getStartedAt(),
                    log.getCompletedAt(),
                    log.getArticlesFetched(),
                    log.getArticlesProcessed(),
                    log.getArticlesFailed(),
                    status,
                    log.getErrorDetails(),
                    log.getCorrelationId()
            );
        }
    }
}
