package com.factcheck.collector.service;

import com.factcheck.collector.integration.nlp.NlpServiceClient;
import com.factcheck.collector.integration.nlp.dto.EmbedRequest;
import com.factcheck.collector.integration.nlp.dto.EmbedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final NlpServiceClient nlpClient;

    public List<List<Double>> embedChunks(List<String> chunks, String correlationId) {
        log.info("Requesting embeddings for {} chunks, correlationId={}", chunks.size(), correlationId);

        EmbedRequest req = new EmbedRequest();
        req.setTexts(chunks);
        req.setCorrelationId(correlationId);

        EmbedResponse resp = nlpClient.embed(req);
        return resp.getEmbeddings();
    }
}