package com.factcheck.collector.integration.fetcher;

import com.factcheck.collector.integration.robots.RobotsService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GenericJsoupContentExtractorTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void extractMainText_skipsBoilerplateAndDuplicates() throws Exception {
        String html = """
                <html>
                  <body>
                    <nav>Subscribe now</nav>
                    <article>
                      <p>Main paragraph one.</p>
                      <p class="promo">Promo text</p>
                      <p>Main paragraph one.</p>
                      <p>Main paragraph two.</p>
                    </article>
                  </body>
                </html>
                """;
        server.createContext("/page", exchange -> {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        RobotsService robotsService = Mockito.mock(RobotsService.class);
        when(robotsService.isAllowed(baseUrl + "/page")).thenReturn(true);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService);
        ReflectionTestUtils.setField(extractor, "userAgent", "TestAgent/1.0");

        String text = extractor.extractMainText(baseUrl + "/page");

        assertThat(text).contains("Main paragraph one.\n\nMain paragraph two.");
        assertThat(text).doesNotContain("Promo");
    }

    @Test
    void extractMainText_returnsEmptyWhenDisallowedByRobots() {
        RobotsService robotsService = Mockito.mock(RobotsService.class);
        when(robotsService.isAllowed(baseUrl + "/page")).thenReturn(false);

        GenericJsoupContentExtractor extractor = new GenericJsoupContentExtractor(robotsService);
        String text = extractor.extractMainText(baseUrl + "/page");

        assertThat(text).isEmpty();
    }
}
