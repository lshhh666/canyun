package com.sky.client.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sky.constant.MessageConstant;
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
        properties.setModel("glm-4.7-flash");
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
                .andExpect(content().json("{\"model\":\"glm-4.7-flash\",\"messages\":[{\"role\":\"system\",\"content\":\"你是餐云客服\"},{\"role\":\"user\",\"content\":\"你好\"}],\"stream\":false,\"max_tokens\":1024}"))
                .andRespond(withSuccess("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"您好，请问需要什么帮助？\"},\"finish_reason\":\"stop\"}]}", MediaType.APPLICATION_JSON));

        String answer = client.chat(SYSTEM_PROMPT, "你好");

        assertThat(answer).isEqualTo("您好，请问需要什么帮助？");
        server.verify();
    }

    @Test
    void shouldRejectResponseWithoutChoices() {
        server.expect(once(), requestTo(CHAT_URL))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.chat(SYSTEM_PROMPT, "你好"))
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
            assertThatThrownBy(() -> client.chat(SYSTEM_PROMPT, "你好"))
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

        assertThatThrownBy(() -> client.chat(SYSTEM_PROMPT, "请介绍一下你自己"))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void shouldRejectModelOutsideFreeAllowlist() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl("https://example.com/v1");
        properties.setModel("glm-4.7-flashx");
        properties.setApiKey(TEST_API_KEY);
        ZhipuAiChatClient paidModelClient = new ZhipuAiChatClient(new RestTemplate(), properties);

        assertThatThrownBy(() -> paidModelClient.chat(SYSTEM_PROMPT, "你好"))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }
}
