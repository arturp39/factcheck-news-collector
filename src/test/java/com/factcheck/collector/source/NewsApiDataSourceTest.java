package com.factcheck.collector.source;

import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.repository.SourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsApiDataSourceTest {

    @Mock SourceRepository sourceRepository;
    @Mock RestTemplate restTemplate;

    ObjectMapper mapper = new ObjectMapper();

    com.factcheck.collector.source.NewsApiDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new com.factcheck.collector.source.NewsApiDataSource(sourceRepository, restTemplate, mapper);

        ReflectionTestUtils.setField(dataSource, "apiKey", "test-key");
        ReflectionTestUtils.setField(dataSource, "baseUrl", "https://newsapi.local/v2/top-headlines");
        ReflectionTestUtils.setField(dataSource, "pageSize", 2);
        ReflectionTestUtils.setField(dataSource, "maxPagesPerBatch", 1);
        ReflectionTestUtils.setField(dataSource, "maxSourcesPerRequest", 20);
        ReflectionTestUtils.setField(dataSource, "matchBySourceName", false);
    }

    @Test
    void fetchData_noConfiguredSources_returnsEmpty() {
        when(sourceRepository.findAllByEnabledTrueAndType(SourceType.NEWSAPI)).thenReturn(List.of());
        assertTrue(dataSource.fetchData().isEmpty());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void fetchData_emptyApiKey_returnsEmpty() {
        ReflectionTestUtils.setField(dataSource, "apiKey", "  ");
        when(sourceRepository.findAllByEnabledTrueAndType(SourceType.NEWSAPI))
                .thenReturn(List.of(Source.builder().id(1L).type(SourceType.NEWSAPI).url("bbc-news").name("BBC").enabled(true).build()));

        assertTrue(dataSource.fetchData().isEmpty());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void fetchData_mapsArticlesByProviderId() {
        Source bbc = Source.builder().id(11L).type(SourceType.NEWSAPI).url("bbc-news").name("BBC News").enabled(true).build();
        when(sourceRepository.findAllByEnabledTrueAndType(SourceType.NEWSAPI)).thenReturn(List.of(bbc));

        String json = """
                {
                  "status": "ok",
                  "articles": [
                    {
                      "source": {"id":"bbc-news","name":"BBC News"},
                      "author":"John",
                      "title":"Hello",
                      "description":"Desc",
                      "content":"Content",
                      "url":"https://example.com/x",
                      "publishedAt":"2024-01-01T00:00:00Z"
                    },
                    {
                      "source": {"id":"unknown","name":"Unknown"},
                      "title":"SkipMe",
                      "url":"https://example.com/y"
                    }
                  ]
                }
                """;

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        List<NewsArticleDto> out = dataSource.fetchData();
        assertEquals(1, out.size());
        NewsArticleDto dto = out.get(0);

        assertEquals(11L, dto.getSourceId());
        assertEquals("BBC News", dto.getSourceName());
        assertEquals("https://example.com/x", dto.getExternalUrl());
        assertEquals("Hello", dto.getTitle());
        assertEquals("John", dto.getAuthor());
        assertEquals("Desc", dto.getDescription());
        assertEquals("Content", dto.getContent());
        assertNotNull(dto.getPublishedAt());
    }

    @Test
    void fetchData_429StopsBatch_withoutSleepingLong() {
        Source bbc = Source.builder()
                .id(11L).type(SourceType.NEWSAPI).url("bbc-news").name("BBC News").enabled(true)
                .build();
        when(sourceRepository.findAllByEnabledTrueAndType(SourceType.NEWSAPI)).thenReturn(List.of(bbc));

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "0"); // => sleep(0)

        HttpClientErrorException ex = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many",
                headers,
                new byte[0],
                StandardCharsets.UTF_8
        );

        assertTrue(ex instanceof HttpClientErrorException.TooManyRequests);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(ex);

        List<NewsArticleDto> out = dataSource.fetchData();
        assertTrue(out.isEmpty());
    }

}