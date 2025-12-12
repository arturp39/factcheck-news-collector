package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.IngestionLog;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.IngestionStatus;
import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.exception.FetchException;
import com.factcheck.collector.integration.fetcher.SourceFetcher;
import com.factcheck.collector.repository.ArticleRepository;
import com.factcheck.collector.repository.IngestionLogRepository;
import com.factcheck.collector.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceIngestionServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private IngestionLogRepository ingestionLogRepository;

    @Mock
    private ArticleProcessingService articleProcessingService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private WeaviateIndexingService weaviateIndexingService;

    @Mock
    private SourceFetcher fetcher;

    @Test
    void ingestSingleSourceRecordsFetchFailure() throws FetchException {
        Source source = Source.builder()
                .id(7L)
                .name("Fetch Failure Source")
                .type(SourceType.RSS)
                .url("https://example.com/rss")
                .build();

        when(fetcher.supports(SourceType.RSS)).thenReturn(true);
        when(fetcher.fetch(source)).thenThrow(new FetchException("boom"));

        SourceIngestionService ingestionService = new SourceIngestionService(
                sourceRepository,
                articleRepository,
                ingestionLogRepository,
                List.of(fetcher),
                articleProcessingService,
                embeddingService,
                weaviateIndexingService
        );

        ingestionService.ingestSingleSource(source, "corr-fail");

        ArgumentCaptor<IngestionLog> logCaptor = ArgumentCaptor.forClass(IngestionLog.class);
        verify(ingestionLogRepository, atLeastOnce()).save(logCaptor.capture());

        IngestionLog finalLog = logCaptor.getAllValues().getLast();
        assertThat(finalLog.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(finalLog.getErrorDetails()).contains("Fetch error: boom");
        assertThat(finalLog.getCorrelationId()).isEqualTo("corr-fail");

        verifyNoInteractions(articleRepository, articleProcessingService, embeddingService, weaviateIndexingService);
    }
}