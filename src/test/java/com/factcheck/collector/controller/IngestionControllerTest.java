package com.factcheck.collector.controller;

import com.factcheck.collector.service.SourceIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean SourceIngestionService sourceIngestionService;

    @Test
    void runOnce_withoutHeader_generatesUuid_andCallsService() throws Exception {
        var res = mockMvc.perform(post("/api/ingestion/run")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED_AND_FINISHED"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andReturn();

        String json = res.getResponse().getContentAsString();
        String cid = objectMapper.readTree(json).path("correlationId").asText();
        assertThat(UUID.fromString(cid)).isNotNull();

        verify(sourceIngestionService).ingestOnce(any(UUID.class));
    }

    @Test
    void runOnce_withValidHeader_passesSameUuid() throws Exception {
        UUID cid = UUID.randomUUID();

        mockMvc.perform(post("/api/ingestion/run")
                        .header("X-Correlation-Id", cid.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").value(cid.toString()));

        verify(sourceIngestionService).ingestOnce(cid);
    }

    @Test
    void runOnce_withInvalidHeader_generatesNewUuid() throws Exception {
        mockMvc.perform(post("/api/ingestion/run")
                        .header("X-Correlation-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").isString());

        verify(sourceIngestionService).ingestOnce(any(UUID.class));
    }
}