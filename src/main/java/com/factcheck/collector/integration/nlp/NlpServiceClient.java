package com.factcheck.collector.integration.nlp;

import com.factcheck.collector.exception.NlpServiceException;
import com.factcheck.collector.integration.nlp.dto.EmbedRequest;
import com.factcheck.collector.integration.nlp.dto.EmbedResponse;
import com.factcheck.collector.integration.nlp.dto.PreprocessRequest;
import com.factcheck.collector.integration.nlp.dto.PreprocessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NlpServiceClient {

    private final RestTemplate restTemplate;

    @Value("${nlp-service.url}")
    private String baseUrl;

    public PreprocessResponse preprocess(String text, String correlationId) {
        try {
            PreprocessRequest req = new PreprocessRequest();
            req.setText(text);
            req.setCorrelationId(correlationId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String cid = (correlationId != null && !correlationId.isBlank())
                    ? correlationId
                    : UUID.randomUUID().toString();
            headers.set("X-Correlation-Id", cid);

            HttpEntity<PreprocessRequest> entity = new HttpEntity<>(req, headers);

            ResponseEntity<PreprocessResponse> resp = restTemplate.exchange(
                    baseUrl + "/preprocess",
                    HttpMethod.POST,
                    entity,
                    PreprocessResponse.class
            );

            if (resp == null || !resp.getStatusCode().is2xxSuccessful()) {
                throw new NlpServiceException(
                        "NLP preprocess failed: HTTP status " +
                                (resp != null ? resp.getStatusCode() : "null response")
                );
            }

            PreprocessResponse body = resp.getBody();
            if (body == null) {
                throw new NlpServiceException("NLP preprocess failed: empty response body");
            }

            return body;

        } catch (RestClientException e) {
            log.error("NLP preprocess call failed", e);
            throw new NlpServiceException("NLP preprocess failed", e);
        }
    }

    public EmbedResponse embed(EmbedRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String correlationId = request.getCorrelationId();
            String cid = (correlationId != null && !correlationId.isBlank())
                    ? correlationId
                    : UUID.randomUUID().toString();
            headers.set("X-Correlation-Id", cid);

            HttpEntity<EmbedRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<EmbedResponse> resp = restTemplate.exchange(
                    baseUrl + "/embed",
                    HttpMethod.POST,
                    entity,
                    EmbedResponse.class
            );

            if (resp == null || !resp.getStatusCode().is2xxSuccessful()) {
                throw new NlpServiceException(
                        "NLP embed failed: HTTP status " +
                                (resp != null ? resp.getStatusCode() : "null response")
                );
            }

            EmbedResponse body = resp.getBody();
            if (body == null) {
                throw new NlpServiceException("NLP embed failed: empty response body");
            }

            return body;

        } catch (RestClientException e) {
            log.error("NLP embed call failed", e);
            throw new NlpServiceException("NLP embed failed", e);
        }
    }
}