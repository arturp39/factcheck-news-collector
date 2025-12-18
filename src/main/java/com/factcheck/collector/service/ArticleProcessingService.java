package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.integration.nlp.NlpServiceClient;
import com.factcheck.collector.integration.nlp.dto.PreprocessResponse;
import com.factcheck.collector.util.ChunkingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleProcessingService {

    private final NlpServiceClient nlpClient;

    @Value("${ingestion.sentences-per-chunk:4}")
    private int sentencesPerChunk;

    @Value("${ingestion.max-characters-per-chunk:1200}")
    private int maxCharactersPerChunk;

    public List<String> createChunks(Article article, String fullText, String correlationId) {
        log.info("Processing article id={} correlationId={}", article.getId(), correlationId);
        PreprocessResponse response = nlpClient.preprocess(fullText, correlationId);
        List<String> sentences = response.getSentences();
        if (sentences == null || sentences.isEmpty()) {
            throw new IllegalStateException("No sentences returned from NLP preprocess for article id=" + article.getId());
        }
        return ChunkingUtils.chunkSentences(sentences, sentencesPerChunk, maxCharactersPerChunk);
    }
}
