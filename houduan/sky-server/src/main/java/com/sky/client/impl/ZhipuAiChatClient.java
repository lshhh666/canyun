package com.sky.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.client.AiChatClient;
import com.sky.client.model.ChatCompletionRequest;
import com.sky.client.model.ChatCompletionResponse;
import com.sky.client.model.ChatMessage;
import com.sky.client.model.ChatThinking;
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
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class ZhipuAiChatClient implements AiChatClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String SYSTEM_ROLE = "system";
    private static final String USER_ROLE = "user";
    private static final String NORMAL_FINISH_REASON = "stop";
    private static final String DISABLED_THINKING_TYPE = "disabled";
    private static final String FREE_MODEL = "glm-4.7-flash";
    private static final String UNKNOWN_PROVIDER_ERROR_CODE = "unknown";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;

    public ZhipuAiChatClient(@Qualifier("aiRestTemplate") RestTemplate restTemplate,
                             AiProperties aiProperties) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
    }

    @Override
    public String chat(String systemPrompt, String message) {
        validateConfiguration();

        ChatMessage systemMessage = ChatMessage.builder()
                .role(SYSTEM_ROLE)
                .content(systemPrompt)
                .build();

        ChatMessage userMessage = ChatMessage.builder()
                .role(USER_ROLE)
                .content(message)
                .build();
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(aiProperties.getModel())
                .messages(Arrays.asList(systemMessage, userMessage))
                .stream(false)
                .thinking(ChatThinking.builder()
                        .type(DISABLED_THINKING_TYPE)
                        .build())
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
        } catch (HttpStatusCodeException ex) {
            log.warn("调用AI模型服务失败，HTTP状态：{}，业务错误码：{}",
                    ex.getRawStatusCode(), extractProviderErrorCode(ex.getResponseBodyAsString()));
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE, ex);
        } catch (RestClientException ex) {
            Throwable rootCause = ex.getMostSpecificCause();
            log.warn("调用AI模型服务失败，异常类型：{}，根因类型：{}",
                    ex.getClass().getSimpleName(), rootCause.getClass().getSimpleName());
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE, ex);
        }
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
        if (!NORMAL_FINISH_REASON.equals(firstChoice.getFinishReason())) {
            log.warn("AI模型回答未正常结束，结束原因：{}", firstChoice.getFinishReason());
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
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
