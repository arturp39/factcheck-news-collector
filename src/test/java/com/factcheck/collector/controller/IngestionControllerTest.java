package com.factcheck.collector.controller;

import com.factcheck.collector.dto.IngestionLogPageResponse;
import com.factcheck.collector.dto.IngestionRunResponse;
import com.factcheck.collector.service.IngestionQueryService;
import com.factcheck.collector.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @MockitoBean
    private IngestionQueryService ingestionQueryService;

    @Test
    void runIngestion_usesProvidedCorrelationId() throws Exception {
        String correlationId = "test-cid-123";

        mockMvc.perform(post("/admin/ingestion/run")
                        .param("correlationId", correlationId))
                .andExpect(status().isOk())
                .andExpect(content().string("Ingestion started, correlationId=" + correlationId));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ingestionService).ingestAllSources(captor.capture());
        assertThat(captor.getValue()).isEqualTo(correlationId);
    }

    @Test
    void runIngestion_generatesCorrelationIdIfMissing() throws Exception {
        String body = mockMvc.perform(post("/admin/ingestion/run"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String cid = body.replace("Ingestion started, correlationId=", "").trim();
        assertThat(cid).isNotBlank();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ingestionService).ingestAllSources(captor.capture());
        assertThat(captor.getValue()).isEqualTo(cid);
        UUID.fromString(cid);
    }

    @Test
    void runIngestionForSource_callsService() throws Exception {
        mockMvc.perform(post("/admin/ingestion/run/{sourceId}", 10L)
                        .param("correlationId", "cid-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ingestion started for sourceId=10, correlationId=cid-1"));

        verify(ingestionService).ingestSource(10L, "cid-1");
    }

    @Test
    void listLogs_returnsPage() throws Exception {
        IngestionRunResponse run = new IngestionRunResponse(
                5L, 1L, "BBC",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T01:00:00Z"),
                1, 1, 0,
                "SUCCESS",
                null,
                "cid-x"
        );

        when(ingestionQueryService.listLogs(0, 20))
                .thenReturn(new IngestionLogPageResponse(0, 20, 1, 1, List.of(run)));

        mockMvc.perform(get("/admin/ingestion/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(5L))
                .andExpect(jsonPath("$.items[0].sourceId").value(1L))
                .andExpect(jsonPath("$.items[0].sourceName").value("BBC"));
    }

    @Test
    void getRun_returnsRun() throws Exception {
        IngestionRunResponse run = new IngestionRunResponse(
                7L, 2L, "NPR",
                null, null,
                0, 0, 0,
                "PARTIAL",
                null,
                "cid-y"
        );

        when(ingestionQueryService.getRun(7L)).thenReturn(run);

        mockMvc.perform(get("/admin/ingestion/runs/{id}", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7L))
                .andExpect(jsonPath("$.sourceId").value(2L))
                .andExpect(jsonPath("$.status").value("PARTIAL"));
    }
}
