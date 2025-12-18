package com.factcheck.collector.source;

import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.integration.fetcher.ArticleContentExtractor;
import com.factcheck.collector.repository.SourceRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssDataSource implements DataSource<NewsArticleDto> {

    private final SourceRepository sourceRepository;
    private final ArticleContentExtractor contentExtractor;

    @Value("${crawler.user-agent:FactCheckCollector/1.0 (+https://example.com)}")
    private String userAgent;

    @Value("${crawler.rss-timeout-seconds:12}")
    private int rssTimeoutSeconds;

    @Value("${crawler.rss-log-per-article:true}")
    private boolean logPerArticle;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(7))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Override
    public List<NewsArticleDto> fetchData() {
        List<Source> feeds = sourceRepository.findAllByEnabledTrueAndType(SourceType.RSS);
        if (feeds.isEmpty()) {
            log.info("RSS: no enabled feeds in DB; skipping.");
            return List.of();
        }

        List<NewsArticleDto> out = new ArrayList<>();
        int totalFeeds = 0;
        int totalEntriesSeen = 0;
        int totalExtractedOk = 0;
        int totalSkipped = 0;

        for (Source feedSource : feeds) {
            String feedUrl = feedSource.getUrl();
            if (feedUrl == null || feedUrl.isBlank()) {
                totalSkipped++;
                continue;
            }

            totalFeeds++;
            long t0 = System.currentTimeMillis();

            int seen = 0;
            int extractedOk = 0;
            int skipped = 0;

            log.info("RSS: fetching feed sourceId={} sourceName='{}' feedUrl={}",
                    feedSource.getId(), feedSource.getName(), feedUrl);

            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(feedUrl))
                        .timeout(Duration.ofSeconds(rssTimeoutSeconds))
                        .GET()
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/rss+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.1")
                        .build();

                HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
                int status = resp.statusCode();

                if (status < 200 || status >= 300) {
                    log.warn("RSS: feed fetch non-2xx status={} sourceId={} feedUrl={}",
                            status, feedSource.getId(), feedUrl);
                    continue;
                }

                try (InputStream is = resp.body(); XmlReader reader = new XmlReader(is)) {
                    SyndFeed feed = new SyndFeedInput().build(reader);

                    for (SyndEntry entry : feed.getEntries()) {
                        seen++;

                        String link = entry.getLink();
                        String title = entry.getTitle();

                        if (link == null || link.isBlank() || title == null || title.isBlank()) {
                            skipped++;
                            continue;
                        }

                        String description = entry.getDescription() != null ? entry.getDescription().getValue() : "";
                        Instant published = entry.getPublishedDate() != null
                                ? entry.getPublishedDate().toInstant()
                                : Instant.now();

                        if (logPerArticle) {
                            log.debug("RSS: extracting fulltext sourceId={} sourceName='{}' url={} title='{}'",
                                    feedSource.getId(), feedSource.getName(), link, abbreviate(title, 180));
                        }

                        String fullText = contentExtractor.extractMainText(link);

                        if (fullText == null || fullText.isBlank()) {
                            if (logPerArticle) {
                                log.debug("RSS: skip (no fulltext extracted) sourceId={} url={}",
                                        feedSource.getId(), link);
                            }
                            skipped++;
                            continue;
                        }

                        extractedOk++;

                        NewsArticleDto dto = new NewsArticleDto();
                        dto.setSourceId(feedSource.getId());
                        dto.setSourceName(feedSource.getName());
                        dto.setExternalUrl(link);
                        dto.setTitle(title);

                        dto.setDescription(Jsoup.parse(description).text().trim());
                        dto.setContent(fullText);
                        dto.setPublishedAt(published);

                        out.add(dto);
                    }
                }

                long tookMs = System.currentTimeMillis() - t0;
                log.info("RSS: feed done sourceId={} seen={} extractedOk={} skipped={} tookMs={}",
                        feedSource.getId(), seen, extractedOk, skipped, tookMs);

            } catch (Exception e) {
                log.warn("RSS: failed feed sourceId={} feedUrl={} err={}",
                        feedSource.getId(), feedUrl, e.toString());
            }

            totalEntriesSeen += seen;
            totalExtractedOk += extractedOk;
            totalSkipped += skipped;
        }

        log.info("RSS: fetched candidates={} feeds={} entriesSeen={} extractedOk={} skipped={}",
                out.size(), totalFeeds, totalEntriesSeen, totalExtractedOk, totalSkipped);

        return out;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }
}