package com.factcheck.collector.controller;

import com.factcheck.collector.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

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
}
