package com.factcheck.collector.controller;

import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.dto.SourceResponse;
import com.factcheck.collector.service.SourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SourceAdminController.class)
class SourceAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SourceService sourceService;

    @Test
    void listSources_returnsSources() throws Exception {
        SourceResponse s = new SourceResponse(
                1L, "BBC", SourceType.RSS, "https://example.com/rss", "top",
                true, 0.85, Instant.now(), Instant.now(), 0, Instant.now(), Instant.now()
        );

        when(sourceService.listSources()).thenReturn(List.of(s));

        mockMvc.perform(get("/admin/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].type").value("RSS"));
    }

    @Test
    void createSource_createsSource() throws Exception {
        SourceResponse saved = new SourceResponse(
                2L, "NPR", SourceType.RSS, "https://npr.org/rss", "top",
                true, 0.8, Instant.now(), Instant.now(), 0, Instant.now(), Instant.now()
        );

        when(sourceService.createSource(org.mockito.ArgumentMatchers.any())).thenReturn(saved);

        String payload = """
                {
                  "name": "NPR",
                  "type": "RSS",
                  "url": "https://npr.org/rss",
                  "category": "top",
                  "enabled": true,
                  "reliabilityScore": 0.8
                }
                """;

        mockMvc.perform(post("/admin/sources")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.name").value("NPR"));
    }

    @Test
    void updateSource_updatesFields() throws Exception {
        SourceResponse updated = new SourceResponse(
                3L, "Old", SourceType.RSS, "https://old", "new",
                false, 0.5, Instant.now(), Instant.now(), 0, Instant.now(), Instant.now()
        );

        when(sourceService.updateSource(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(updated);

        String payload = """
                { "enabled": false, "category": "new" }
                """;

        mockMvc.perform(patch("/admin/sources/{id}", 3L)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.category").value("new"));
    }
}
