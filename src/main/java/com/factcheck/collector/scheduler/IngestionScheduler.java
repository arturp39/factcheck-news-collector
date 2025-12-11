package com.factcheck.collector.scheduler;

import com.factcheck.collector.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionScheduler {

    private final IngestionService ingestionService;

    @Scheduled(cron = "0 0 6 * * *", zone = "Europe/Vilnius")
    public void runIngestion() {
        String correlationId = UUID.randomUUID().toString();
        log.info("Starting scheduled ingestion, correlationId={}", correlationId);
        ingestionService.ingestAllSources(correlationId);
    }
}
