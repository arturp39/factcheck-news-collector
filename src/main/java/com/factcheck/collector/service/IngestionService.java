package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final SourceRepository sourceRepository;
    private final SourceIngestionService sourceIngestionService;

    @Value("${ingestion.max-parallel-sources:3}")
    private int maxParallelSources;

    public void ingestAllSources(String correlationId) {
        List<Source> sources = sourceRepository.findByEnabledTrue();
        if (sources.isEmpty()) {
            log.info("No enabled sources to ingest, correlationId={}", correlationId);
            return;
        }

        log.info("Starting ingestion for {} sources, correlationId={}",
                sources.size(), correlationId);

        ExecutorService executor = Executors.newFixedThreadPool(maxParallelSources);
        List<Future<?>> futures = new ArrayList<>();

        for (Source source : sources) {
            futures.add(executor.submit(() ->
                    sourceIngestionService.ingestSingleSource(source, correlationId)));
        }

        executor.shutdown();

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Ingestion interrupted, correlationId={}", correlationId, ie);
            } catch (ExecutionException ee) {
                log.warn("Ingestion task failed, correlationId={}", correlationId, ee.getCause());
            }
        }

        log.info("Ingestion run finished, correlationId={}", correlationId);
    }
}