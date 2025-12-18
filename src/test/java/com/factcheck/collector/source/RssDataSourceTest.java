package com.factcheck.collector.source;

import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.integration.fetcher.ArticleContentExtractor;
import com.factcheck.collector.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RssDataSourceTest {

    @Mock SourceRepository sourceRepository;
    @Mock ArticleContentExtractor contentExtractor;

    @Mock HttpClient httpClient;
    @Mock HttpResponse<InputStream> httpResponse;

    RssDataSource rssDataSource;

    @BeforeEach
    void setUp() {
        rssDataSource = new RssDataSource(sourceRepository, contentExtractor);

        ReflectionTestUtils.setField(rssDataSource, "userAgent", "TestAgent/1.0");
        ReflectionTestUtils.setField(rssDataSource, "rssTimeoutSeconds", 1);
        ReflectionTestUtils.setField(rssDataSource, "logPerArticle", true);

        ReflectionTestUtils.setField(rssDataSource, "httpClient", httpClient);
    }

    @Test
    void fetchData_noFeeds_returnsEmpty() {
        when(sourceRepository.findAllByEnabledTrueAndType(SourceType.RSS)).thenReturn(List.of());
        assertTrue(rssDataSource.fetchData().isEmpty());
        verifyNoInteractions(contentExtractor);
    }

    @Test
    void fetchData_feedNon2xx_skipsFeed() throws Exception {
        Source feed = Source.builder().id(1L).name("Feed1").type(SourceType.RSS).url("https://example.com/rss").enabled(true).build();
        when(sourceRepository.findAllByEnabledTrueAndType(SourceType.RSS)).thenReturn(List.of(feed));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(503);

        List<NewsArticleDto> out = rssDataSource.fetchData();
        assertTrue(out.isEmpty());
        verify(contentExtractor, never()).extractMainText(anyString());
    }

    @Test
    void fetchData_strictMode_onlyKeepsEntriesWithExtractedFullText() throws Exception {
        Source feed = Source.builder().id(10L).name("BBC").type(SourceType.RSS).url("https://example.com/rss").enabled(true).build();
        when(sourceRepository.findAllByEnabledTrueAndType(SourceType.RSS)).thenReturn(List.of(feed));

        String rssXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Test</title>
                    <item>
                      <title>Title 1</title>
                      <link>https://example.com/a1</link>
                      <description><![CDATA[<p>desc 1</p>]]></description>
                      <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
                    </item>
                    <item>
                      <title></title>
                      <link>https://example.com/a2</link>
                    </item>
                    <item>
                      <title>Title 3</title>
                      <link>https://example.com/a3</link>
                      <description><![CDATA[<p>desc 3</p>]]></description>
                    </item>
                  </channel>
                </rss>
                """;

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(rssXml.getBytes(StandardCharsets.UTF_8)));

        when(contentExtractor.extractMainText("https://example.com/a1")).thenReturn("FULL TEXT A1");
        when(contentExtractor.extractMainText("https://example.com/a3")).thenReturn("   "); // strict => skip

        List<NewsArticleDto> out = rssDataSource.fetchData();

        assertEquals(1, out.size());
        NewsArticleDto dto = out.get(0);
        assertEquals(10L, dto.getSourceId());
        assertEquals("BBC", dto.getSourceName());
        assertEquals("https://example.com/a1", dto.getExternalUrl());
        assertEquals("Title 1", dto.getTitle());
        assertNotNull(dto.getPublishedAt());
        assertEquals("FULL TEXT A1", dto.getContent());

        verify(contentExtractor).extractMainText("https://example.com/a1");
        verify(contentExtractor).extractMainText("https://example.com/a3");
    }

    @Test
    void fetchData_blankFeedUrl_skips() {
        Source feed = Source.builder().id(1L).name("X").type(SourceType.RSS).url("   ").enabled(true).build();
        when(sourceRepository.findAllByEnabledTrueAndType(SourceType.RSS)).thenReturn(List.of(feed));

        List<NewsArticleDto> out = rssDataSource.fetchData();
        assertTrue(out.isEmpty());
        verifyNoInteractions(contentExtractor);
    }
}