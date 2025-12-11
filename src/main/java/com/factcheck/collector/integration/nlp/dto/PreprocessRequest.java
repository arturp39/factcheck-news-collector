package com.factcheck.collector.integration.nlp.dto;

import lombok.Data;

@Data
public class PreprocessRequest {
    private String text;
    private String correlationId;
}
