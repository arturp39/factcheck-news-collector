package com.factcheck.collector.scheduler;

import com.factcheck.collector.service.SourceIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionScheduler {

    private final SourceIngestionService sourceIngestionService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Vilnius")
    public void run() {
        UUID correlationId = UUID.randomUUID();
        log.info("Scheduled ingestion started correlationId={}", correlationId);
        sourceIngestionService.ingestOnce(correlationId);
        log.info("Scheduled ingestion finished correlationId={}", correlationId);
    }
}