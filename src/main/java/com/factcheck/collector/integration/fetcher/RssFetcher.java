package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.exception.FetchException;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssFetcher implements SourceFetcher {

    private final ArticleContentExtractor contentExtractor;
    @Value("${crawler.user-agent:FactCheckCollector/1.0 (+https://example.com)}")
    private String userAgent;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public List<RawArticle> fetch(Source source) throws FetchException {
        log.info("Fetching RSS from source id={} url={}", source.getId(), source.getUrl());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(source.getUrl()))
                    .GET()
                    .header("User-Agent", userAgent)
                    .build();

            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FetchException(
                        "RSS HTTP status " + response.statusCode() + " for " + source.getUrl(), null
                );
            }

            try (InputStream is = response.body();
                 XmlReader reader = new XmlReader(is)) {

                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(reader);

                List<RawArticle> result = new ArrayList<>();

                for (SyndEntry entry : feed.getEntries()) {

                    String link = entry.getLink();
                    String title = entry.getTitle();

                    if (link == null || title == null || title.isBlank()) {
                        continue;
                    }

                    String description = entry.getDescription() != null
                            ? entry.getDescription().getValue()
                            : "";

                    Date pubDate = entry.getPublishedDate();
                    Instant published = pubDate != null
                            ? pubDate.toInstant()
                            : Instant.now();

                    String fullText = contentExtractor.extractMainText(link);

                    String rawText = (fullText != null && !fullText.isBlank())
                            ? fullText
                            : description;

                    if (rawText == null || rawText.isBlank()) {
                        log.debug("Skipping RSS item with no usable text: {}", link);
                        continue;
                    }

                    result.add(RawArticle.builder()
                            .externalUrl(link)
                            .title(title)
                            .description(description)
                            .rawText(rawText)
                            .publishedDate(published)
                            .build());
                }

                log.info("Fetched {} RSS items from source id={}", result.size(), source.getId());
                return result;
            }

        } catch (Exception e) {
            throw new FetchException("Failed to fetch RSS from " + source.getUrl(), e);
        }
    }

    @Override
    public boolean supports(SourceType type) {
        return type == SourceType.RSS;
    }
}