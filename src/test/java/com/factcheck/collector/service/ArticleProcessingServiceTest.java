package com.factcheck.collector.service;

import com.factcheck.collector.domain.entity.Article;
import com.factcheck.collector.integration.nlp.NlpServiceClient;
import com.factcheck.collector.integration.nlp.dto.PreprocessResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleProcessingServiceTest {

    @Mock
    private NlpServiceClient nlpServiceClient;

    @InjectMocks
    private ArticleProcessingService articleProcessingService;

    @Test
    void createChunksDelegatesToNlpClientAndChunksSentences() {
        Article article = Article.builder()
                .id(11L)
                .build();

        PreprocessResponse preprocessResponse = new PreprocessResponse();
        preprocessResponse.setSentences(List.of(
                "Sentence one",
                "Sentence two",
                "Sentence three",
                "Sentence four",
                "Sentence five"
        ));

        when(nlpServiceClient.preprocess("full text", "cid-123"))
                .thenReturn(preprocessResponse);

        List<String> chunks = articleProcessingService.createChunks(article, "full text", "cid-123");

        verify(nlpServiceClient).preprocess("full text", "cid-123");
        assertThat(chunks)
                .hasSize(2)
                .containsExactly(
                        "Sentence one Sentence two Sentence three Sentence four",
                        "Sentence five"
                );
    }
}