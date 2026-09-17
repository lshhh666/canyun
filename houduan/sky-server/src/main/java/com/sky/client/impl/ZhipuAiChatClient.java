package com.sky.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.client.AiChatClient;
import com.sky.client.model.ChatCompletionRequest;
import com.sky.client.model.ChatCompletionResponse;
import com.sky.client.model.ChatMessage;
import com.sky.client.model.ChatResponseFormat;
import com.sky.client.model.ChatThinking;
import com.sky.client.model.ChatTool;
import com.sky.client.model.ChatToolCall;
import com.sky.client.model.AiToolCallDecision;
import com.sky.client.model.Choice;
import com.sky.constant.MessageConstant;
import com.sky.entity.AiChatMessage;
import com.sky.enums.AiChatRole;
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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ZhipuAiChatClient implements AiChatClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String SYSTEM_ROLE = "system";
    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final String NORMAL_FINISH_REASON = "stop";
    private static final String TOOL_CALL_FINISH_REASON = "tool_calls";
    private static final String DISABLED_THINKING_TYPE = "disabled";
    private static final String JSON_OBJECT_RESPONSE_TYPE = "json_object";
    private static final String AUTO_TOOL_CHOICE = "auto";
    private static final double STRUCTURED_OUTPUT_TEMPERATURE = 0.1D;
    private static final String SUPPORTED_MODEL = "glm-4.5-air";
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
    public String chat(String systemPrompt, List<AiChatMessage> messages) {
        return executeChat(systemPrompt, messages, null, null);
    }

    @Override
    public String chatAsJson(String systemPrompt, List<AiChatMessage> messages) {
        return executeChat(systemPrompt, messages,
                ChatResponseFormat.builder().type(JSON_OBJECT_RESPONSE_TYPE).build(),
                STRUCTURED_OUTPUT_TEMPERATURE);
    }

    @Override
    public AiToolCallDecision requestToolCall(String systemPrompt,
                                              List<AiChatMessage> messages,
                                              List<ChatTool> tools) {
        validateTools(tools);
        ChatCompletionRequest request = baseRequest(
                buildRequestMessages(systemPrompt, messages))
                .tools(tools)
                .toolChoice(AUTO_TOOL_CHOICE)
                .build();
        return extractToolDecision(exchange(request));
    }

    private String executeChat(String systemPrompt,
                               List<AiChatMessage> messages,
                               ChatResponseFormat responseFormat,
                               Double temperature) {
        validateConfiguration();
        List<ChatMessage> requestMessages = buildRequestMessages(systemPrompt, messages);

        ChatCompletionRequest request = baseRequest(requestMessages)
                .responseFormat(responseFormat)
                .temperature(temperature)
                .build();

        return extractAnswer(exchange(request));
    }

    private ChatCompletionRequest.ChatCompletionRequestBuilder baseRequest(
            List<ChatMessage> requestMessages) {
        validateConfiguration();
        return ChatCompletionRequest.builder()
                .model(aiProperties.getModel())
                .messages(requestMessages)
                .stream(false)
                .thinking(ChatThinking.builder()
                        .type(DISABLED_THINKING_TYPE)
                        .build())
                .maxTokens(aiProperties.getMaxTokens());
    }

    private List<ChatMessage> buildRequestMessages(String systemPrompt,
                                                   List<AiChatMessage> messages) {
        if (!StringUtils.hasText(systemPrompt) || messages == null || messages.isEmpty()) {
            throw invalidContextException();
        }
        List<ChatMessage> requestMessages = new ArrayList<>();
        requestMessages.add(ChatMessage.builder()
                .role(SYSTEM_ROLE)
                .content(systemPrompt)
                .build());
        for (AiChatMessage message : messages) {
            requestMessages.add(toProviderMessage(message));
        }
        return requestMessages;
    }

    private ChatCompletionResponse exchange(ChatCompletionRequest request) {
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
            return response.getBody();
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

    private AiToolCallDecision extractToolDecision(ChatCompletionResponse response) {
        Choice firstChoice = firstChoice(response);
        if (NORMAL_FINISH_REASON.equals(firstChoice.getFinishReason())) {
            if (hasToolCalls(firstChoice.getMessage())) {
                throw invalidResponseException();
            }
            return AiToolCallDecision.noTool();
        }
        if (!TOOL_CALL_FINISH_REASON.equals(firstChoice.getFinishReason())) {
            throw invalidResponseException();
        }
        List<ChatToolCall> toolCalls = firstChoice.getMessage() == null
                ? null : firstChoice.getMessage().getToolCalls();
        if (toolCalls == null || toolCalls.size() != 1 || toolCalls.get(0) == null) {
            throw invalidResponseException();
        }
        return AiToolCallDecision.requested(toolCalls.get(0));
    }

    private boolean hasToolCalls(ChatMessage message) {
        return message != null && message.getToolCalls() != null
                && !message.getToolCalls().isEmpty();
    }

    private void validateTools(List<ChatTool> tools) {
        if (tools == null || tools.isEmpty()) {
            throw invalidContextException();
        }
    }

    private ChatMessage toProviderMessage(AiChatMessage message) {
        if (message == null || message.getRole() == null
                || !StringUtils.hasText(message.getContent())) {
            throw invalidContextException();
        }
        return ChatMessage.builder()
                .role(toProviderRole(message.getRole()))
                .content(message.getContent())
                .build();
    }

    private String toProviderRole(AiChatRole role) {
        if (role == AiChatRole.USER) {
            return USER_ROLE;
        }
        if (role == AiChatRole.ASSISTANT) {
            return ASSISTANT_ROLE;
        }
        throw invalidContextException();
    }

    private AiServiceException invalidContextException() {
        log.error("AI客服上下文消息无效");
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

    private String extractAnswer(ChatCompletionResponse response) {
        Choice firstChoice = firstChoice(response);
        if (firstChoice == null || firstChoice.getMessage() == null
                || !StringUtils.hasText(firstChoice.getMessage().getContent())) {
            throw invalidResponseException();
        }
        if (!NORMAL_FINISH_REASON.equals(firstChoice.getFinishReason())) {
            log.warn("AI模型回答未正常结束，结束原因：{}", firstChoice.getFinishReason());
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (hasToolCalls(firstChoice.getMessage())) {
            throw invalidResponseException();
        }
        return firstChoice.getMessage().getContent();
    }

    private Choice firstChoice(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null
                || response.getChoices().isEmpty() || response.getChoices().get(0) == null) {
            throw invalidResponseException();
        }
        return response.getChoices().get(0);
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
        if (!SUPPORTED_MODEL.equals(aiProperties.getModel())) {
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
