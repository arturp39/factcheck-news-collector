package com.factcheck.collector.integration.robots;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsServiceTest {

    @Test
    void disallowsPathsDefinedInRobots() {
        RobotsService service = new RobotsService("TestBot");
        var parser = new crawlercommons.robots.SimpleRobotRulesParser();
        var rules = parser.parseContent(
                "https://example.com/robots.txt",
                """
                        User-agent: *
                        Disallow: /blocked
                        """.getBytes(),
                "text/plain",
                "TestBot"
        );
        ConcurrentHashMap<String, crawlercommons.robots.BaseRobotRules> cache = new ConcurrentHashMap<>();
        cache.put("https://example.com", rules);
        ReflectionTestUtils.setField(service, "rulesCache", cache);

        assertThat(service.isAllowed("https://example.com/allowed/page")).isTrue();
        assertThat(service.isAllowed("https://example.com/blocked")).isFalse();
    }

    @Test
    void allowsWhenRobotsMissing() {
        RobotsService service = new RobotsService("TestBot");
        var allowAll = new crawlercommons.robots.SimpleRobotRules(
                crawlercommons.robots.SimpleRobotRules.RobotRulesMode.ALLOW_ALL
        );
        Map<String, crawlercommons.robots.BaseRobotRules> cache = new ConcurrentHashMap<>();
        cache.put("https://no-robots.example.com", allowAll);
        ReflectionTestUtils.setField(service, "rulesCache", cache);

        assertThat(service.isAllowed("https://no-robots.example.com/anything")).isTrue();
    }
}
