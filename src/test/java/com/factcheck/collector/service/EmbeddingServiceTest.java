package com.factcheck.collector.service;

import com.factcheck.collector.integration.nlp.NlpServiceClient;
import com.factcheck.collector.integration.nlp.dto.EmbedRequest;
import com.factcheck.collector.integration.nlp.dto.EmbedResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private NlpServiceClient nlpServiceClient;

    @InjectMocks
    private EmbeddingService embeddingService;

    @Test
    void embedChunksSendsCorrelationIdAndReturnsEmbeddings() {
        List<String> chunks = List.of("first", "second");

        EmbedResponse embedResponse = new EmbedResponse();
        embedResponse.setEmbeddings(List.of(
                List.of(0.1d, 0.2d),
                List.of(0.3d, 0.4d)
        ));

        when(nlpServiceClient.embed(org.mockito.ArgumentMatchers.any(EmbedRequest.class)))
                .thenReturn(embedResponse);

        List<List<Double>> embeddings = embeddingService.embedChunks(chunks, "corr-123");

        ArgumentCaptor<EmbedRequest> requestCaptor = ArgumentCaptor.forClass(EmbedRequest.class);
        verify(nlpServiceClient).embed(requestCaptor.capture());

        EmbedRequest request = requestCaptor.getValue();
        assertThat(request.getTexts()).containsExactlyElementsOf(chunks);
        assertThat(request.getCorrelationId()).isEqualTo("corr-123");
        assertThat(embeddings).containsExactlyElementsOf(embedResponse.getEmbeddings());
    }
}