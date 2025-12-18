package com.factcheck.collector.processor;

import com.factcheck.collector.aggregator.NewsAggregator;
import com.factcheck.collector.domain.dto.NewsArticleDto;
import com.factcheck.collector.repository.ArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
public class NewsProcessor extends BaseProcessor<NewsArticleDto> {

    private final NewsAggregator aggregator;
    private final ArticleRepository articleRepository;

    public NewsProcessor(NewsAggregator aggregator, ArticleRepository articleRepository) {
        this.aggregator = aggregator;
        this.articleRepository = articleRepository;
    }

    @Override
    protected List<NewsArticleDto> fetchData() {
        return aggregator.aggregateAsync();
    }

    @Override
    protected List<NewsArticleDto> validate(List<NewsArticleDto> data) {
        Instant nowPlus1d = Instant.now().plusSeconds(86400);

        return data.stream()
                .filter(a -> a.getSourceId() != null)
                .filter(a -> validateField(a.toString(), "externalUrl", a.getExternalUrl()))
                .filter(a -> validateField(a.toString(), "title", a.getTitle()))
                .filter(a -> a.getPublishedAt() == null || !a.getPublishedAt().isAfter(nowPlus1d))
                .toList();
    }


    @Override
    protected List<NewsArticleDto> map(List<NewsArticleDto> data) {
        List<NewsArticleDto> normalized = data.stream()
                .map(NewsProcessor::normalize)
                .toList();

        Map<String, NewsArticleDto> byUrl = new LinkedHashMap<>();
        for (var a : normalized) {
            if (a.getExternalUrl() != null) byUrl.putIfAbsent(a.getExternalUrl(), a);
        }
        return List.copyOf(byUrl.values());
    }

    @Override
    protected List<NewsArticleDto> filterExisting(List<NewsArticleDto> data) {
        if (data.isEmpty()) return data;

        Set<String> urls = new HashSet<>();
        for (var a : data) {
            if (a.getExternalUrl() != null) urls.add(a.getExternalUrl());
        }

        Set<String> existing = articleRepository.findExistingUrls(urls);
        if (existing.isEmpty()) return data;

        return data.stream()
                .filter(a -> a.getExternalUrl() == null || !existing.contains(a.getExternalUrl()))
                .toList();
    }

    private static NewsArticleDto normalize(NewsArticleDto in) {
        NewsArticleDto out = new NewsArticleDto();
        out.setSourceId(in.getSourceId());
        out.setSourceName(clean(in.getSourceName()));
        out.setTitle(clean(in.getTitle()));
        out.setAuthor(clean(in.getAuthor()));
        out.setDescription(clean(in.getDescription()));
        out.setContent(in.getContent());
        out.setPublishedAt(in.getPublishedAt());
        out.setExternalUrl(canonicalizeUrl(in.getExternalUrl()));
        return out;
    }

    private static String clean(String s) {
        return s == null ? null : s.trim().replaceAll("\\s+", " ");
    }

    private static String canonicalizeUrl(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();

            if (scheme == null || host == null) return url.trim();

            URI cleaned = new URI(scheme.toLowerCase(), uri.getUserInfo(), host.toLowerCase(),
                    uri.getPort(), path, null, null);

            String s = cleaned.toString();
            return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
        } catch (Exception e) {
            return url.trim();
        }
    }
}