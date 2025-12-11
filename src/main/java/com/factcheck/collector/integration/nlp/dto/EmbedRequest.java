package com.factcheck.collector.integration.nlp.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmbedRequest {
    private List<String> texts;
    private String correlationId;
}
