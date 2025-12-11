package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.integration.robots.RobotsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenericJsoupContentExtractor implements ArticleContentExtractor {

    private static final int TIMEOUT_MS = (int) Duration.ofSeconds(10).toMillis();
    private static final String USER_AGENT = "FactCheckCollector/1.0 (+https://example.com)";

    private final RobotsService robotsService;

    @Override
    public String extractMainText(String url) {
        if (!robotsService.isAllowed(url)) {
            log.info("Skipping article extraction for {} because robots.txt disallows it", url);
            return "";
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            Element container = selectMainContainer(doc);
            if (container == null) {
                log.warn("No container element found for {}", url);
                return "";
            }

            List<String> paragraphs = extractCleanParagraphs(container);
            if (paragraphs.isEmpty()) {
                log.warn("No meaningful text extracted from {}", url);
                return "";
            }

            return String.join("\n\n", paragraphs);

        } catch (SocketTimeoutException e) {
            log.warn("Timeout while fetching article {}", url);
            return "";
        } catch (Exception e) {
            log.warn("Failed to fetch/parse article {}", url, e);
            return "";
        }
    }

    private Element selectMainContainer(Document doc) {
        Element container = doc.selectFirst("article");
        if (container != null) return container;

        container = doc.selectFirst("[itemprop=articleBody]");
        if (container != null) return container;

        container = doc.selectFirst("main");
        if (container != null) return container;

        container = doc.selectFirst(
                "div[id*=content], div[class*=content], " +
                        "div[id*=article], div[class*=article], " +
                        "section[id*=content], section[class*=content]"
        );
        if (container != null) return container;

        return doc.body();
    }

    private List<String> extractCleanParagraphs(Element container) {
        Elements paragraphElements = container.select("p");
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();

        for (Element p : paragraphElements) {
            if (isBoilerplate(p)) {
                continue;
            }

            String text = p.text();
            if (text == null) {
                continue;
            }

            text = text.trim();
            if (text.isEmpty()) {
                continue;
            }

            if (seen.add(text)) {
                result.add(text);
            }
        }

        return result;
    }

    private boolean isBoilerplate(Element p) {
        String ownClasses = p.className();
        String ownId = p.id();
        StringBuilder context = new StringBuilder((ownClasses + " " + ownId).toLowerCase());

        Element parent = p.parent();
        int depth = 0;
        while (parent != null && depth < 3) {
            context.append(" ")
                    .append(parent.className())
                    .append(" ")
                    .append(parent.id());
            parent = parent.parent();
            depth++;
        }

        String ctx = context.toString().toLowerCase();

        String[] boilerplateMarkers = {
                "footer", "header", "nav", "breadcrumb", "menu",
                "subscribe", "newsletter", "promo", "banner",
                "advert", "ad-", "ad_", "ads", "sponsor",
                "share", "social", "related", "recommended",
                "comments", "comment", "cookie"
        };

        for (String marker : boilerplateMarkers) {
            if (ctx.contains(marker)) {
                return true;
            }
        }

        return false;
    }
}