package com.factcheck.collector.processor;

import com.factcheck.collector.aggregator.NewsAggregator;
import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsProcessorTest {

    @Mock NewsAggregator aggregator;
    @Mock ArticleRepository articleRepository;

    @Test
    void process_validatesDedupsAndFiltersExisting() {
        NewsProcessor processor = new NewsProcessor(aggregator, articleRepository);

        Instant now = Instant.now();

        NewsArticleDto good1 = new NewsArticleDto();
        good1.setSourceId(1L);
        good1.setExternalUrl("https://EXAMPLE.com/a?utm=1");
        good1.setTitle("  Hello   world ");
        good1.setPublishedAt(now);

        NewsArticleDto goodDup = new NewsArticleDto();
        goodDup.setSourceId(1L);
        goodDup.setExternalUrl("https://example.com/a/");
        goodDup.setTitle("Hello world");
        goodDup.setPublishedAt(now);

        NewsArticleDto missingSource = new NewsArticleDto();
        missingSource.setSourceId(null);
        missingSource.setExternalUrl("https://example.com/b");
        missingSource.setTitle("B");

        NewsArticleDto future = new NewsArticleDto();
        future.setSourceId(1L);
        future.setExternalUrl("https://example.com/f");
        future.setTitle("F");
        future.setPublishedAt(now.plusSeconds(90000)); // > now + 1d => invalid

        when(aggregator.aggregateAsync()).thenReturn(List.of(good1, goodDup, missingSource, future));

        when(articleRepository.findExistingUrls(Set.of("https://example.com/a")))
                .thenReturn(Set.of()); // none exist

        List<NewsArticleDto> out = processor.process();
        assertEquals(1, out.size());

        NewsArticleDto kept = out.get(0);
        assertEquals("https://example.com/a", kept.getExternalUrl());
        assertEquals("Hello world", kept.getTitle());
    }

    @Test
    void process_filtersExistingUrls() {
        NewsProcessor processor = new NewsProcessor(aggregator, articleRepository);

        NewsArticleDto a = new NewsArticleDto();
        a.setSourceId(1L);
        a.setExternalUrl("https://example.com/a");
        a.setTitle("A");

        when(aggregator.aggregateAsync()).thenReturn(List.of(a));
        when(articleRepository.findExistingUrls(Set.of("https://example.com/a")))
                .thenReturn(Set.of("https://example.com/a"));

        List<NewsArticleDto> out = processor.process();
        assertTrue(out.isEmpty());
    }
}