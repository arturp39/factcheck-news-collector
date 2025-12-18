package com.factcheck.collector.service;

import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.processor.NewsProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SourceIngestionServiceTest {

    @Mock NewsProcessor newsProcessor;
    @Mock ArticleIngestionPipeline pipeline;
    @Mock IngestionLogService ingestionLogService;

    @Test
    void ingestOnce_happyPath_callsSuccess() {
        SourceIngestionService s = new SourceIngestionService(newsProcessor, pipeline, ingestionLogService);
        UUID cid = UUID.randomUUID();

        when(ingestionLogService.start(eq(cid), any(Instant.class))).thenReturn(123L);
        when(newsProcessor.process()).thenReturn(List.of(new NewsArticleDto()));
        when(pipeline.ingestAll(anyList(), eq(cid))).thenReturn(1);

        s.ingestOnce(cid);

        verify(ingestionLogService).success(eq(123L), any(Instant.class), eq(1), eq(1));
        verify(ingestionLogService, never()).fail(any(), any(), any());
    }

    @Test
    void ingestOnce_failure_callsFailAndRethrows() {
        SourceIngestionService s = new SourceIngestionService(newsProcessor, pipeline, ingestionLogService);
        UUID cid = UUID.randomUUID();

        when(ingestionLogService.start(eq(cid), any(Instant.class))).thenReturn(123L);
        when(newsProcessor.process()).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> s.ingestOnce(cid));

        verify(ingestionLogService).fail(eq(cid), any(Instant.class), any(Exception.class));
    }
}