package com.factcheck.collector.controller;

import com.factcheck.collector.domain.entity.Source;
import com.factcheck.collector.domain.enums.SourceType;
import com.factcheck.collector.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
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
    private SourceRepository sourceRepository;

    @Test
    void listSources_returnsSources() throws Exception {
        Source s = Source.builder()
                .id(1L)
                .name("BBC")
                .type(SourceType.RSS)
                .url("https://example.com/rss")
                .category("top")
                .enabled(true)
                .reliabilityScore(0.85)
                .build();

        when(sourceRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id")))
                .thenReturn(List.of(s));

        mockMvc.perform(get("/admin/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].type").value("RSS"));
    }

    @Test
    void createSource_createsSource() throws Exception {
        Source saved = Source.builder()
                .id(2L)
                .name("NPR")
                .type(SourceType.RSS)
                .url("https://npr.org/rss")
                .category("top")
                .enabled(true)
                .reliabilityScore(0.8)
                .build();

        when(sourceRepository.save(org.mockito.ArgumentMatchers.any(Source.class))).thenReturn(saved);

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
        Source existing = Source.builder()
                .id(3L)
                .name("Old")
                .type(SourceType.RSS)
                .url("https://old")
                .category("old")
                .enabled(true)
                .reliabilityScore(0.5)
                .build();

        when(sourceRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(sourceRepository.save(existing)).thenReturn(existing);

        String payload = """
                { "enabled": false, "category": "new" }
                """;

        mockMvc.perform(patch("/admin/sources/{id}", 3L)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.category").value("new"));

        verify(sourceRepository).save(existing);
    }
}

