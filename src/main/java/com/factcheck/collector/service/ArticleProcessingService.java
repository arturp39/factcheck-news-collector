package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.integration.nlp.NlpServiceClient;
import com.factcheck.collector.integration.nlp.dto.PreprocessResponse;
import com.factcheck.collector.util.ChunkingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleProcessingService {

    private final NlpServiceClient nlpClient;

    public List<String> createChunks(Article article, String fullText, String correlationId) {
        log.info("Processing article id={} correlationId={}", article.getId(), correlationId);
        PreprocessResponse response = nlpClient.preprocess(fullText, correlationId);
        List<String> sentences = response.getSentences();
        return ChunkingUtils.chunkSentences(sentences);
    }
}
