package com.sky.client.impl;

import com.sky.client.AiChatClient;
import com.sky.client.model.ChatCompletionRequest;
import com.sky.client.model.ChatCompletionResponse;
import com.sky.client.model.ChatMessage;
import com.sky.client.model.Choice;
import com.sky.constant.MessageConstant;
import com.sky.exception.AiServiceException;
import com.sky.properties.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ZhipuAiChatClient implements AiChatClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String USER_ROLE = "user";
    private static final String FREE_MODEL = "glm-4.7-flash";

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;

    public ZhipuAiChatClient(@Qualifier("aiRestTemplate") RestTemplate restTemplate,
                             AiProperties aiProperties) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
    }

    @Override
    public String chat(String message) {
        validateConfiguration();

        ChatMessage userMessage = ChatMessage.builder()
                .role(USER_ROLE)
                .content(message)
                .build();
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(aiProperties.getModel())
                .messages(Collections.singletonList(userMessage))
                .stream(false)
                .maxTokens(aiProperties.getMaxTokens())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getApiKey());

        try {
            ResponseEntity<ChatCompletionResponse> response = restTemplate.exchange(
                    buildChatCompletionsUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    ChatCompletionResponse.class
            );
            return extractAnswer(response.getBody());
        } catch (AiServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("调用AI模型服务失败，异常类型：{}", ex.getClass().getSimpleName());
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE, ex);
        }
    }

    private String extractAnswer(ChatCompletionResponse response) {
        if (response == null) {
            throw invalidResponseException();
        }
        List<Choice> choices = response.getChoices();
        if (choices == null || choices.isEmpty()) {
            throw invalidResponseException();
        }
        Choice firstChoice = choices.get(0);
        if (firstChoice == null || firstChoice.getMessage() == null
                || !StringUtils.hasText(firstChoice.getMessage().getContent())) {
            throw invalidResponseException();
        }
        return firstChoice.getMessage().getContent();
    }

    private AiServiceException invalidResponseException() {
        log.warn("AI模型服务返回了无有效回答的响应");
        return new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(aiProperties.getBaseUrl())
                || !StringUtils.hasText(aiProperties.getModel())
                || !StringUtils.hasText(aiProperties.getApiKey())) {
            log.error("AI模型服务配置不完整");
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (!FREE_MODEL.equals(aiProperties.getModel())) {
            log.error("AI模型配置不在允许列表中");
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
    }

    private String buildChatCompletionsUrl() {
        String baseUrl = aiProperties.getBaseUrl();
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + CHAT_COMPLETIONS_PATH
                : baseUrl + CHAT_COMPLETIONS_PATH;
    }
}
