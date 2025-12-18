package com.factcheck.collector.controller;

import com.factcheck.collector.service.SourceIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final SourceIngestionService sourceIngestionService;

    @PostMapping("/run")
    public ResponseEntity<IngestionRunResponse> runOnce(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader
    ) {
        UUID correlationId = parseOrNew(correlationIdHeader);

        sourceIngestionService.ingestOnce(correlationId);

        return ResponseEntity.ok(new IngestionRunResponse(
                correlationId.toString(),
                "STARTED_AND_FINISHED"
        ));
    }

    private static UUID parseOrNew(String v) {
        try {
            return (v == null || v.isBlank()) ? UUID.randomUUID() : UUID.fromString(v.trim());
        } catch (Exception ignored) {
            return UUID.randomUUID();
        }
    }

    public record IngestionRunResponse(String correlationId, String status) {}
}