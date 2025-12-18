package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.repository.IngestionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionLogService {

    private final IngestionLogRepository ingestionLogRepository;

    public Long start(UUID correlationId, Instant startedAt) {
        IngestionLog logEntry = IngestionLog.builder()
                .status(IngestionStatus.RUNNING)
                .correlationId(correlationId.toString())
                .startedAt(startedAt)
                .build();
        ingestionLogRepository.save(logEntry);
        return logEntry.getId();
    }

    public void success(Long logId, Instant completedAt, int candidates, int processedOk) {
        IngestionLog entry = ingestionLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalStateException("IngestionLog not found: " + logId));

        entry.setCompletedAt(completedAt);
        entry.setArticlesFetched(candidates);
        entry.setArticlesProcessed(processedOk);
        entry.setArticlesFailed(Math.max(0, candidates - processedOk));
        entry.setStatus(entry.getArticlesFailed() == 0 ? IngestionStatus.SUCCESS : IngestionStatus.PARTIAL);

        ingestionLogRepository.save(entry);
    }

    public void fail(UUID correlationId, Instant completedAt, Exception e) {
        IngestionLog entry = IngestionLog.builder()
                .status(IngestionStatus.FAILED)
                .correlationId(correlationId.toString())
                .startedAt(completedAt)
                .completedAt(completedAt)
                .errorDetails(e.toString())
                .build();
        ingestionLogRepository.save(entry);
    }
}
