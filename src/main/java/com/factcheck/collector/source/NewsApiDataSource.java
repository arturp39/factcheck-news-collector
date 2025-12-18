package com.factcheck.collector.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsApiDataSource implements DataSource<NewsArticleDto> {

    private final SourceRepository sourceRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    @Value("${newsapi.api-key:}")
    private String apiKey;

    @Value("${newsapi.base-url:https://newsapi.org/v2/top-headlines}")
    private String baseUrl;

    @Value("${newsapi.page-size:50}")
    private int pageSize;

    @Value("${newsapi.max-pages-per-batch:1}")
    private int maxPagesPerBatch;

    @Value("${newsapi.max-sources-per-request:20}")
    private int maxSourcesPerRequest;

    @Value("${newsapi.match-by-source-name:false}")
    private boolean matchBySourceName;

    @Override
    public List<NewsArticleDto> fetchData() {
        List<Source> configured = sourceRepository.findAllByEnabledTrueAndType(SourceType.NEWSAPI);
        if (configured.isEmpty()) {
            log.info("NEWSAPI: no enabled sources in DB; skipping.");
            return List.of();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("NEWSAPI: api key empty; skipping.");
            return List.of();
        }

        List<Source> valid = configured.stream()
                .filter(s -> s.getUrl() != null && !s.getUrl().isBlank())
                .toList();

        if (valid.isEmpty()) {
            log.info("NEWSAPI: enabled sources exist but url(providerId) empty; skipping.");
            return List.of();
        }

        List<List<Source>> batches = partition(valid, maxSourcesPerRequest);
        List<NewsArticleDto> out = new ArrayList<>();

        log.info("NEWSAPI: starting fetch sources={} batches={} pageSize={} maxPagesPerBatch={}",
                valid.size(), batches.size(), pageSize, maxPagesPerBatch);

        for (int b = 0; b < batches.size(); b++) {
            List<Source> batch = batches.get(b);

            Map<String, Source> byProviderId = new HashMap<>();
            Map<String, Source> byName = new HashMap<>();
            for (Source s : batch) {
                byProviderId.put(s.getUrl().trim(), s);
                if (s.getName() != null) byName.put(s.getName().trim().toLowerCase(Locale.ROOT), s);
            }

            String sourcesParam = batch.stream()
                    .map(Source::getUrl)
                    .map(String::trim)
                    .collect(Collectors.joining(","));

            log.info("NEWSAPI: batch {}/{} sourcesCount={} (providerIds in Source.url)", (b + 1), batches.size(), batch.size());
            log.debug("NEWSAPI: batch sourcesParam={}", sourcesParam);

            for (int page = 1; page <= maxPagesPerBatch; page++) {
                JsonNode root = callTopHeadlines(sourcesParam, page, pageSize);
                if (root == null) break;

                JsonNode articles = root.path("articles");
                if (!articles.isArray() || articles.isEmpty()) break;

                int accepted = 0;
                for (JsonNode a : articles) {
                    Optional<NewsArticleDto> dtoOpt = toDto(a, byProviderId, byName);
                    if (dtoOpt.isPresent()) {
                        out.add(dtoOpt.get());
                        accepted++;
                    }
                }

                log.info("NEWSAPI: batch {}/{} page={} returned={} accepted={}",
                        (b + 1), batches.size(), page, articles.size(), accepted);

                if (articles.size() < pageSize) break;
            }
        }

        log.info("NEWSAPI: fetched candidates={}", out.size());
        return out;
    }

    private Optional<NewsArticleDto> toDto(JsonNode a,
                                           Map<String, Source> byProviderId,
                                           Map<String, Source> byName) {

        String externalUrl = txt(a, "url");
        String title = txt(a, "title");
        if (isBlank(externalUrl) || isBlank(title)) return Optional.empty();

        JsonNode srcNode = a.path("source");
        String providerId = txt(srcNode, "id");
        String providerName = txt(srcNode, "name");

        Source internal = null;

        if (!isBlank(providerId)) {
            internal = byProviderId.get(providerId.trim());
        }

        if (internal == null && matchBySourceName && !isBlank(providerName)) {
            internal = byName.get(providerName.trim().toLowerCase(Locale.ROOT));
        }

        if (internal == null) return Optional.empty();

        NewsArticleDto dto = new NewsArticleDto();
        dto.setSourceId(internal.getId());
        dto.setSourceName(internal.getName());
        dto.setExternalUrl(externalUrl.trim());
        dto.setTitle(title.trim());
        dto.setAuthor(trimToNull(txt(a, "author")));
        dto.setDescription(trimToNull(txt(a, "description")));
        dto.setContent(trimToNull(txt(a, "content")));
        dto.setPublishedAt(parseInstantOrNull(txt(a, "publishedAt")));

        log.debug("NEWSAPI: mapped providerId='{}' providerName='{}' -> sourceId={} sourceName='{}' url={}",
                providerId, providerName, internal.getId(), internal.getName(), dto.getExternalUrl());

        return Optional.of(dto);
    }

    private JsonNode callTopHeadlines(String sourcesParam, int page, int pageSize) {
        String url = baseUrl
                + "?sources=" + sourcesParam // IMPORTANT: no URLEncoder here
                + "&page=" + page
                + "&pageSize=" + pageSize
                + "&apiKey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8); // encode ONLY apiKey

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "FactCheckCollector/1.0"); // add UA

        headers.set("X-Api-Key", apiKey);

        try {
            ResponseEntity<String> res = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (res == null || !res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                log.warn("NEWSAPI: non-2xx status={} page={} url={}",
                        res != null ? res.getStatusCode() : null, page, url);
                return null;
            }

            JsonNode root = mapper.readTree(res.getBody());
            String status = root.path("status").asText();
            if (!"ok".equalsIgnoreCase(status)) {
                log.warn("NEWSAPI: non-ok status={} code={} message={} url={}",
                        status,
                        root.path("code").asText(null),
                        root.path("message").asText(null),
                        url);
                return null;
            }

            return root;

        } catch (HttpClientErrorException.TooManyRequests e) {
            long sleepMs = parseRetryAfterMs(e.getResponseHeaders() != null
                    ? e.getResponseHeaders().getFirst("Retry-After") : null);
            log.warn("NEWSAPI: 429. Sleeping {} ms then stopping this batch/page.", sleepMs);
            sleep(sleepMs);
            return null;
        } catch (Exception e) {
            log.warn("NEWSAPI: request failed page={} url={} err={}", page, url, e.toString());
            return null;
        }
    }

    private static String txt(JsonNode node, String field) {
        return (node != null && node.hasNonNull(field)) ? node.get(field).asText() : null;
    }

    private static Instant parseInstantOrNull(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Instant.parse(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static List<List<Source>> partition(List<Source> list, int size) {
        int n = Math.max(1, size);
        List<List<Source>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += n) {
            parts.add(list.subList(i, Math.min(i + n, list.size())));
        }
        return parts;
    }

    private static long parseRetryAfterMs(String retryAfter) {
        try {
            if (retryAfter == null) return 1_000L;
            return Math.min(Long.parseLong(retryAfter.trim()) * 1000L, 60_000L);
        } catch (Exception ignored) {
            return 1_000L;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}