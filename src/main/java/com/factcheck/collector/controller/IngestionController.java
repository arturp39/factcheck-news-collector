package com.factcheck.collector.controller;

import com.factcheck.collector.dto.IngestionLogPageResponse;
import com.factcheck.collector.dto.IngestionRunResponse;
import com.factcheck.collector.service.IngestionQueryService;
import com.factcheck.collector.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;
    private final IngestionQueryService ingestionQueryService;

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

        return ResponseEntity.ok(ingestionQueryService.listLogs(page, size));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<IngestionRunResponse> getRun(@PathVariable("id") Long runId) {
        return ResponseEntity.ok(ingestionQueryService.getRun(runId));
    }
}
