package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.exception.FetchException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ArticleContentExtractor {

    private final HttpClient httpClient;

    @Value("${crawler.user-agent:FactCheckCollector/1.0 (+https://example.com)}")
    private String userAgent;

    @Value("${crawler.article-timeout-seconds:15}")
    private int timeoutSeconds;

    @Value("${crawler.max-html-bytes:2097152}")
    private int maxHtmlBytes;

    @Value("${crawler.min-text-length:400}")
    private int minTextLength;

    @Value("${crawler.warn-cooldown-ms:60000}")
    private long warnCooldownMs;

    @Value("${crawler.host-backoff-max-ms:300000}") // 5 min cap
    private long hostBackoffMaxMs;

    private static final Pattern MULTI_WS = Pattern.compile("\\s+");

    private final ConcurrentHashMap<String, Long> lastWarnByHost = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> hostBackoffUntilMs = new ConcurrentHashMap<>();

    private static final List<String> REMOVE_SELECTORS = List.of(
            "script", "style", "noscript", "svg", "canvas",
            "header", "footer", "nav", "aside",
            "form", "button", "input",
            "[role=banner]", "[role=navigation]", "[role=contentinfo]"
    );

    private static final List<String> BAD_CLASS_ID_HINTS = List.of(
            "cookie", "consent", "subscribe", "newsletter",
            "promo", "advert", "ads", "banner", "paywall",
            "share", "social", "comment", "related", "recommend"
    );

    public ArticleContentExtractor() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(7))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public String extractMainText(String url) {
        if (url == null || url.isBlank()) return null;

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            debugOrWarn(url, "Invalid URL", e);
            return null;
        }

        String host = uri.getHost() == null ? "unknown" : uri.getHost();
        long now = System.currentTimeMillis();
        long backoffUntil = hostBackoffUntilMs.getOrDefault(host, 0L);
        if (now < backoffUntil) {
            log.debug("Extractor: host backoff active host={} untilMs={} url={}", host, backoffUntil, url);
            return null;
        }

        try {
            FetchResult fr = fetchHtml(uri);
            if (fr == null || fr.body == null || fr.body.length == 0) return null;

            String html = new String(fr.body, fr.charset != null ? fr.charset : StandardCharsets.UTF_8);

            String text = extractFromHtml(html, url);
            if (text == null) return null;

            text = normalize(text);
            if (text == null || text.length() < minTextLength) {
                log.debug("Extractor: text too short len={} url={}", (text == null ? 0 : text.length()), url);
                return null;
            }
            return text;

        } catch (FetchException fe) {
            debugOrWarn(url, fe.getMessage(), fe);
            return null;
        } catch (Exception e) {
            debugOrWarn(url, "Extractor failed", e);
            return null;
        }
    }

    private FetchResult fetchHtml(URI uri) {
        log.debug("Extractor: GET url={}", uri);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();

        try {
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int status = resp.statusCode();
            String ct = resp.headers().firstValue("Content-Type").orElse("");

            if (status == 429) {
                long ms = parseRetryAfterMs(resp.headers().firstValue("Retry-After").orElse(null));
                ms = Math.min(ms, hostBackoffMaxMs);

                String host = uri.getHost() == null ? "unknown" : uri.getHost();
                hostBackoffUntilMs.put(host, System.currentTimeMillis() + ms);

                throw new FetchException("Non-2xx status=429 retryAfterMs=" + ms + " contentType=" + ct + " url=" + uri, null);
            }

            if (status < 200 || status >= 300) {
                throw new FetchException("Non-2xx status=" + status + " contentType=" + ct + " url=" + uri, null);
            }

            String cts = ct.toLowerCase(Locale.ROOT);
            if (!cts.isBlank() && !(cts.contains("text/html") || cts.contains("application/xhtml+xml"))) {
                throw new FetchException("Non-HTML contentType=" + ct + " url=" + uri, null);
            }

            Charset charset = parseCharsetFromContentType(ct).orElse(StandardCharsets.UTF_8);

            try (InputStream in = resp.body()) {
                byte[] body = readUpTo(in, maxHtmlBytes);
                if (body == null) {
                    throw new FetchException("Body exceeded maxHtmlBytes=" + maxHtmlBytes + " url=" + uri, null);
                }

                log.debug("Extractor: OK url={} status={} bytes={} contentType='{}' charset={}",
                        uri, status, body.length, ct, charset);

                return new FetchResult(body, charset);
            }

        } catch (FetchException e) {
            throw e;
        } catch (Exception e) {
            throw new FetchException("Failed to fetch HTML url=" + uri + " cause=" + e.getClass().getSimpleName(), e);
        }
    }

    private static byte[] readUpTo(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private String extractFromHtml(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);

        for (String sel : REMOVE_SELECTORS) {
            doc.select(sel).remove();
        }
        removeByHints(doc);

        String bestArticle = bestTextFromElements(doc.select("article"));
        if (bestArticle != null && bestArticle.length() >= minTextLength) return bestArticle;

        Elements mainCandidates = doc.select("main, [role=main], #content, #main, .content, .main, .article, .post, .entry-content");
        String bestMain = bestTextFromElements(mainCandidates);
        if (bestMain != null && bestMain.length() >= minTextLength) return bestMain;

        Element body = doc.body();
        if (body == null) return null;

        Element best = null;
        int bestScore = 0;

        for (Element el : body.select("div, section")) {
            Elements ps = el.select("p");
            if (ps.size() < 3) continue;

            int score = 0;
            for (Element p : ps) {
                String t = p.text();
                if (t != null) score += t.length();
            }

            if (score > bestScore) {
                bestScore = score;
                best = el;
            }
        }

        if (best == null) {
            String t = body.text();
            return (t == null || t.isBlank()) ? null : t;
        }

        StringBuilder sb = new StringBuilder(bestScore + 256);
        for (Element p : best.select("p")) {
            String t = p.text();
            if (t == null) continue;
            t = t.trim();
            if (t.length() < 30) continue;
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(t);
        }

        return sb.isEmpty() ? null : sb.toString();
    }

    private void removeByHints(Document doc) {
        for (Element el : doc.getAllElements()) {
            String cls = el.className() == null ? "" : el.className();
            String id = el.id() == null ? "" : el.id();

            String hay = (cls + " " + id).toLowerCase(Locale.ROOT);
            for (String hint : BAD_CLASS_ID_HINTS) {
                if (hay.contains(hint)) {
                    el.remove();
                    break;
                }
            }
        }
    }

    private String bestTextFromElements(Elements els) {
        if (els == null || els.isEmpty()) return null;

        String best = null;
        int bestLen = 0;

        for (Element el : els) {
            StringBuilder sb = new StringBuilder();
            for (Element p : el.select("p")) {
                String t = p.text();
                if (t == null) continue;
                t = t.trim();
                if (t.length() < 30) continue;
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(t);
            }

            String txt = sb.isEmpty() ? el.text() : sb.toString();
            if (txt != null && txt.length() > bestLen) {
                bestLen = txt.length();
                best = txt;
            }
        }
        return best;
    }

    private String normalize(String s) {
        if (s == null) return null;
        String x = s.trim();
        if (x.isEmpty()) return null;

        String[] parts = x.split("\\R+");
        StringBuilder sb = new StringBuilder(x.length());

        for (String p : parts) {
            String line = MULTI_WS.matcher(p).replaceAll(" ").trim();
            if (line.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(line);
        }
        return sb.toString();
    }

    private Optional<Charset> parseCharsetFromContentType(String contentType) {
        if (contentType == null) return Optional.empty();
        String ct = contentType.toLowerCase(Locale.ROOT);
        int i = ct.indexOf("charset=");
        if (i < 0) return Optional.empty();
        String cs = ct.substring(i + "charset=".length()).trim();
        int semi = cs.indexOf(';');
        if (semi >= 0) cs = cs.substring(0, semi).trim();
        cs = cs.replace("\"", "").trim();
        try {
            return Optional.of(Charset.forName(cs));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private void debugOrWarn(String url, String msg, Exception e) {
        String host = safeHost(url);
        long now = System.currentTimeMillis();
        long last = lastWarnByHost.getOrDefault(host, 0L);

        if (now - last >= warnCooldownMs) {
            lastWarnByHost.put(host, now);
            log.warn("Extractor: {} url={} err={}", msg, url, e.toString());
        } else {
            log.debug("Extractor: {} url={} err={}", msg, url, e.toString());
        }
    }

    private static String safeHost(String url) {
        try {
            return URI.create(url).getHost() == null ? "unknown" : URI.create(url).getHost();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static long parseRetryAfterMs(String retryAfter) {
        try {
            if (retryAfter == null || retryAfter.isBlank()) return 5_000L;

            long sec = Long.parseLong(retryAfter.trim());
            if (sec > 0) return Math.min(sec * 1000L, 300_000L);

            return 5_000L;
        } catch (Exception ignored) {
            return 5_000L;
        }
    }

    private record FetchResult(byte[] body, Charset charset) {}
}