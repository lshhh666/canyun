package com.sky.client.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sky.constant.MessageConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.client.model.AiToolCallDecision;
import com.sky.client.model.ChatFunctionDefinition;
import com.sky.client.model.ChatTool;
import com.sky.entity.AiChatMessage;
import com.sky.enums.AiChatRole;
import com.sky.exception.AiServiceException;
import com.sky.properties.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ZhipuAiChatClientTest {

    private static final String CHAT_URL = "https://example.com/v1/chat/completions";
    private static final String TEST_API_KEY = "test-secret-key";
    private static final String SYSTEM_PROMPT = "你是餐云客服";

    private MockRestServiceServer server;
    private ZhipuAiChatClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        AiProperties properties = new AiProperties();
        properties.setBaseUrl("https://example.com/v1/");
        properties.setModel("glm-4.5-air");
        properties.setApiKey(TEST_API_KEY);
        properties.setMaxTokens(1024);
        client = new ZhipuAiChatClient(restTemplate, properties);
    }

    @Test
    void shouldSendChatRequestAndExtractFirstAnswer() {
        server.expect(once(), requestTo(CHAT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"model\":\"glm-4.5-air\",\"messages\":[{\"role\":\"system\",\"content\":\"你是餐云客服\"},{\"role\":\"user\",\"content\":\"我不吃辣\"},{\"role\":\"assistant\",\"content\":\"已经记下了\"},{\"role\":\"user\",\"content\":\"那主食呢\"}],\"stream\":false,\"thinking\":{\"type\":\"disabled\"},\"max_tokens\":1024}"))
                .andRespond(withSuccess("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"您好，请问需要什么帮助？\"},\"finish_reason\":\"stop\"}]}", MediaType.APPLICATION_JSON));

        String answer = client.chat(SYSTEM_PROMPT, Arrays.asList(
                message(AiChatRole.USER, "我不吃辣"),
                message(AiChatRole.ASSISTANT, "已经记下了"),
                message(AiChatRole.USER, "那主食呢")));

        assertThat(answer).isEqualTo("您好，请问需要什么帮助？");
        server.verify();
    }

    @Test
    void shouldRequestJsonObjectResponseForStructuredChat() {
        server.expect(once(), requestTo(CHAT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"model\":\"glm-4.5-air\",\"messages\":[{\"role\":\"system\",\"content\":\"你是餐云客服\"},{\"role\":\"user\",\"content\":\"营业时间\"}],\"stream\":false,\"thinking\":{\"type\":\"disabled\"},\"response_format\":{\"type\":\"json_object\"},\"temperature\":0.1,\"max_tokens\":1024}"))
                .andRespond(withSuccess("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"action\\\":\\\"SEARCH\\\",\\\"query\\\":\\\"营业时间\\\",\\\"clarification\\\":null}\"},\"finish_reason\":\"stop\"}]}", MediaType.APPLICATION_JSON));

        String result = client.chatAsJson(SYSTEM_PROMPT, userContext("营业时间"));

        assertThat(result).contains("\"action\":\"SEARCH\"");
        server.verify();
    }

    @Test
    void shouldSendToolDefinitionAndParseSingleToolCall() {
        server.expect(once(), requestTo(CHAT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"model\":\"glm-4.5-air\",\"messages\":[{\"role\":\"system\",\"content\":\"你是餐云客服\"},{\"role\":\"user\",\"content\":\"为什么我的优惠券不能用\"}],\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"check_my_coupon_eligibility\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}}}],\"tool_choice\":\"auto\"}"))
                .andRespond(withSuccess("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"check_my_coupon_eligibility\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"tool_calls\"}]}", MediaType.APPLICATION_JSON));

        AiToolCallDecision decision = client.requestToolCall(
                SYSTEM_PROMPT,
                userContext("为什么我的优惠券不能用"),
                Collections.singletonList(couponTool()));

        assertThat(decision.isToolRequested()).isTrue();
        assertThat(decision.getToolCall().getId()).isEqualTo("call_1");
        assertThat(decision.getToolCall().getFunction().getName())
                .isEqualTo("check_my_coupon_eligibility");
        assertThat(decision.getToolCall().getFunction().getArguments()).isEqualTo("{}");
        server.verify();
    }

    @Test
    void shouldReturnNoToolDecisionWhenModelStopsNormally() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withSuccess("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"NO_TOOL\"},\"finish_reason\":\"stop\"}]}", MediaType.APPLICATION_JSON));

        AiToolCallDecision decision = client.requestToolCall(
                SYSTEM_PROMPT,
                userContext("优惠券门槛怎么算"),
                Collections.singletonList(couponTool()));

        assertThat(decision.isToolRequested()).isFalse();
        assertThat(decision.getToolCall()).isNull();
        server.verify();
    }

    @Test
    void shouldRejectNormalStopDecisionThatStillContainsToolCalls() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withSuccess("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"NO_TOOL\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"check_my_coupon_eligibility\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"stop\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.requestToolCall(
                SYSTEM_PROMPT,
                userContext("为什么我的优惠券不能用"),
                Collections.singletonList(couponTool())))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void shouldRejectMultipleToolCallsBecauseThisMvpAllowsOnlyOne() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withSuccess("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"check_my_coupon_eligibility\",\"arguments\":\"{}\"}},{\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"check_my_coupon_eligibility\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"tool_calls\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.requestToolCall(
                SYSTEM_PROMPT,
                userContext("为什么我的优惠券不能用"),
                Collections.singletonList(couponTool())))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void shouldRejectResponseWithoutChoices() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat(SYSTEM_PROMPT, userContext("你好")))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void shouldTranslateProviderErrorWithoutLeakingApiKey() {
        Logger logger = (Logger) LoggerFactory.getLogger(ZhipuAiChatClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"1305\",\"message\":\"模型繁忙，provider-secret-detail\"}}"));

        try {
            assertThatThrownBy(() -> client.chat(SYSTEM_PROMPT, userContext("你好")))
                    .isInstanceOf(AiServiceException.class)
                    .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE)
                    .hasMessageNotContaining(TEST_API_KEY);
            server.verify();

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("HTTP状态：429") && message.contains("业务错误码：1305"))
                    .noneMatch(message -> message.contains(TEST_API_KEY)
                            || message.contains("provider-secret-detail"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldRejectTruncatedAnswer() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withSuccess("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"这是一个没有说完的回答或\"},\"finish_reason\":\"length\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat(SYSTEM_PROMPT, userContext("请介绍一下你自己")))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void shouldRejectModelOutsideSupportedAllowlist() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("https://example.com/v1");
        properties.setModel("glm-4.7-flashx");
        properties.setApiKey(TEST_API_KEY);
        ZhipuAiChatClient paidModelClient = new ZhipuAiChatClient(new RestTemplate(), properties);

        assertThatThrownBy(() -> paidModelClient.chat(SYSTEM_PROMPT, userContext("你好")))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }

    private List<AiChatMessage> userContext(String content) {
        return Collections.singletonList(message(AiChatRole.USER, content));
    }

    private AiChatMessage message(AiChatRole role, String content) {
        return AiChatMessage.builder()
                .role(role)
                .content(content)
                .build();
    }

    private ChatTool couponTool() {
        com.fasterxml.jackson.databind.node.ObjectNode parameters =
                new ObjectMapper().createObjectNode();
        parameters.put("type", "object");
        parameters.set("properties", new ObjectMapper().createObjectNode());
        parameters.put("additionalProperties", false);
        return ChatTool.builder()
                .type("function")
                .function(ChatFunctionDefinition.builder()
                        .name("check_my_coupon_eligibility")
                        .parameters(parameters)
                        .build())
                .build();
    }
}
