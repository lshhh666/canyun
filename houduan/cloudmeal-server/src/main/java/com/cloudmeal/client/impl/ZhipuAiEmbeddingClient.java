package com.cloudmeal.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloudmeal.client.AiEmbeddingClient;
import com.cloudmeal.client.model.EmbeddingData;
import com.cloudmeal.client.model.EmbeddingRequest;
import com.cloudmeal.client.model.EmbeddingResponse;
import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.exception.AiServiceException;
import com.cloudmeal.properties.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class ZhipuAiEmbeddingClient implements AiEmbeddingClient {

    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final String EMBEDDING_MODEL = "embedding-3";
    private static final String UNKNOWN_PROVIDER_ERROR_CODE = "unknown";
    private static final Set<Integer> ALLOWED_DIMENSIONS =
            new HashSet<>(Arrays.asList(256, 512, 1024, 2048));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;

    public ZhipuAiEmbeddingClient(@Qualifier("aiRestTemplate") RestTemplate restTemplate,
                                  AiProperties aiProperties) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
    }

    @Override
    public List<Double> embed(String text) {
        validateRequest(text);

        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(aiProperties.getEmbeddingModel())
                .input(text.trim())
                .dimensions(aiProperties.getEmbeddingDimensions())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getApiKey());

        try {
            ResponseEntity<EmbeddingResponse> response = restTemplate.exchange(
                    buildEmbeddingsUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    EmbeddingResponse.class
            );
            return extractEmbedding(response.getBody());
        } catch (AiServiceException ex) {
            throw ex;
        } catch (HttpStatusCodeException ex) {
            log.warn("调用文本向量服务失败，HTTP状态：{}，业务错误码：{}",
                    ex.getRawStatusCode(), extractProviderErrorCode(ex.getResponseBodyAsString()));
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE, ex);
        } catch (RestClientException ex) {
            Throwable rootCause = ex.getMostSpecificCause();
            log.warn("调用文本向量服务失败，异常类型：{}，根因类型：{}",
                    ex.getClass().getSimpleName(), rootCause.getClass().getSimpleName());
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE, ex);
        }
    }

    private void validateRequest(String text) {
        if (!StringUtils.hasText(text)) {
            throw invalidEmbeddingException("向量化文本为空");
        }
        if (!StringUtils.hasText(aiProperties.getBaseUrl())
                || !StringUtils.hasText(aiProperties.getApiKey())
                || !EMBEDDING_MODEL.equals(aiProperties.getEmbeddingModel())
                || !ALLOWED_DIMENSIONS.contains(aiProperties.getEmbeddingDimensions())) {
            throw invalidEmbeddingException("文本向量服务配置不完整或不受支持");
        }
    }

    private List<Double> extractEmbedding(EmbeddingResponse response) {
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw invalidEmbeddingException("文本向量服务未返回有效结果");
        }
        EmbeddingData first = response.getData().get(0);
        if (first == null || first.getEmbedding() == null
                || first.getEmbedding().size() != aiProperties.getEmbeddingDimensions()
                || first.getEmbedding().contains(null)) {
            throw invalidEmbeddingException("文本向量服务返回的向量维度无效");
        }
        return first.getEmbedding();
    }

    private AiServiceException invalidEmbeddingException(String logMessage) {
        log.warn(logMessage);
        return new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }

    private String extractProviderErrorCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return UNKNOWN_PROVIDER_ERROR_CODE;
        }
        try {
            JsonNode errorCode = OBJECT_MAPPER.readTree(responseBody).path("error").path("code");
            return errorCode.isValueNode() && StringUtils.hasText(errorCode.asText())
                    ? errorCode.asText()
                    : UNKNOWN_PROVIDER_ERROR_CODE;
        } catch (Exception ex) {
            return UNKNOWN_PROVIDER_ERROR_CODE;
        }
    }

    private String buildEmbeddingsUrl() {
        String baseUrl = aiProperties.getBaseUrl();
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + EMBEDDINGS_PATH
                : baseUrl + EMBEDDINGS_PATH;
    }
}
