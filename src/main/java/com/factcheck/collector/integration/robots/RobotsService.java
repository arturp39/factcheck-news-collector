package com.factcheck.collector.integration.robots;

import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RobotsService {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final BaseRobotRules ALLOW_ALL_RULES =
            new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final SimpleRobotRulesParser parser = new SimpleRobotRulesParser();

    private final Map<String, BaseRobotRules> rulesCache = new ConcurrentHashMap<>();

    private final String userAgent;

    public RobotsService(
            @Value("${crawler.user-agent:FactCheckCollector/1.0 (+https://example.com)}")
            String userAgent
    ) {
        this.userAgent = userAgent;
    }

    public boolean isAllowed(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String scheme = uri.getScheme();

            if (host == null || scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return true;
            }

            String key = scheme + "://" + host;

            // Cache robots.txt per host to avoid hammering sites
            BaseRobotRules rules = rulesCache.computeIfAbsent(key, this::fetchRulesForHost);

            return rules.isAllowed(url);

        } catch (Exception e) {
            log.warn("Failed to evaluate robots.txt for url={}. Defaulting to ALLOW. Reason: {}",
                    url, e.toString());
            return true;
        }
    }

    private BaseRobotRules fetchRulesForHost(String baseUrl) {
        String robotsUrl = baseUrl + "/robots.txt";
        log.info("Fetching robots.txt from {}", robotsUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(robotsUrl))
                .GET()
                .header("User-Agent", userAgent)
                .timeout(TIMEOUT)
                .build();

        try {
            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                byte[] body = response.body();
                return parser.parseContent(
                        robotsUrl,
                        body,
                        "text/plain",
                        userAgent
                );
            } else if (status == 404) {
                log.info("No robots.txt (404) for {}. Treating as all allowed.", baseUrl);
                return ALLOW_ALL_RULES;
            } else {
                log.warn("Non-OK status {} while fetching robots.txt from {}. Defaulting to ALLOW.",
                        status, robotsUrl);
                return ALLOW_ALL_RULES;
            }

        } catch (IOException | InterruptedException e) {
            log.warn("Error fetching robots.txt from {}. Defaulting to ALLOW. Reason: {}",
                    robotsUrl, e.toString());
            return ALLOW_ALL_RULES;
        }
    }
}