package com.factcheck.collector.service;

import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.processor.NewsProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SourceIngestionService {

    private final NewsProcessor newsProcessor;
    private final ArticleIngestionPipeline articleIngestionPipeline;
    private final IngestionLogService ingestionLogService;

    public void ingestOnce(UUID correlationId) {
        Instant start = Instant.now();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("corrId", correlationId.toString())) {

            Long logId = ingestionLogService.start(correlationId, start);

            List<NewsArticleDto> candidates = newsProcessor.process();
            log.info("Candidates after validation/dedup/filterExisting={}", candidates.size());

            int processedOk = articleIngestionPipeline.ingestAll(candidates, correlationId);

            ingestionLogService.success(logId, Instant.now(), candidates.size(), processedOk);
            log.info("Ingestion done. candidates={} processedOk={}", candidates.size(), processedOk);

        } catch (Exception e) {
            log.error("Ingestion failed correlationId={}", correlationId, e);
            ingestionLogService.fail(correlationId, Instant.now(), e);
            throw e;
        }
    }
}