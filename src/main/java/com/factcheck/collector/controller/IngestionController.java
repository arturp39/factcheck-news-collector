package com.factcheck.collector.controller;

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
}