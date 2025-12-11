package com.factcheck.collector.integration.nlp.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmbedResponse {

    private List<List<Double>> embeddings;

    private String correlationId;
}
