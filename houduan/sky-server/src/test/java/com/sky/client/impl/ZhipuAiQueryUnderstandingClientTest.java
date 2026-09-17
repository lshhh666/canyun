package com.sky.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.client.AiChatClient;
import com.sky.client.model.AiQueryUnderstandingResult;
import com.sky.constant.MessageConstant;
import com.sky.entity.AiChatMessage;
import com.sky.enums.AiChatRole;
import com.sky.enums.AiQueryAction;
import com.sky.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZhipuAiQueryUnderstandingClientTest {

    @Mock
    private AiChatClient aiChatClient;

    private ZhipuAiQueryUnderstandingClient client;
    private List<AiChatMessage> context;

    @BeforeEach
    void setUp() {
        client = new ZhipuAiQueryUnderstandingClient(aiChatClient, new ObjectMapper());
        context = Collections.singletonList(AiChatMessage.builder()
                .role(AiChatRole.USER)
                .content("早上8点想吃饭了")
                .build());
    }

    @Test
    void shouldParseSearchResultAndRequireJsonMode() {
        when(aiChatClient.chatAsJson(anyString(), eq(context))).thenReturn(
                "{\"action\":\"SEARCH\",\"query\":\"门店早上8点是否营业\",\"clarification\":null}");

        AiQueryUnderstandingResult result = client.understand(context);

        assertThat(result.getAction()).isEqualTo(AiQueryAction.SEARCH);
        assertThat(result.getQuery()).isEqualTo("门店早上8点是否营业");
        assertThat(result.getClarification()).isNull();
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiChatClient).chatAsJson(prompt.capture(), eq(context));
        assertThat(prompt.getValue())
                .contains("只做分类和问题改写")
                .contains("SEARCH")
                .contains("CLARIFY")
                .contains("不得添加用户未表达的业务事实")
                .contains("只对应一个已配置门店")
                .contains("不得追问是哪家门店")
                .contains("用户本轮明确选择其中一个意图")
                .contains("订单尾号")
                .contains("首次查询不要求用户提供订单尾号")
                .contains("只表达时间和用餐意愿")
                .contains("早上8点想吃饭")
                .contains("领取、使用、有效期还是用途");
    }

    @Test
    void shouldParseClarificationInsideMarkdownFence() {
        when(aiChatClient.chatAsJson(anyString(), eq(context))).thenReturn(
                "```json\n{\"action\":\"CLARIFY\",\"query\":null,\"clarification\":\"您是想询问早上8点是否营业，还是想让我推荐早餐？\"}\n```");

        AiQueryUnderstandingResult result = client.understand(context);

        assertThat(result.getAction()).isEqualTo(AiQueryAction.CLARIFY);
        assertThat(result.getQuery()).isNull();
        assertThat(result.getClarification()).contains("是否营业");
    }

    @Test
    void shouldRejectMultipleJsonObjects() {
        when(aiChatClient.chatAsJson(anyString(), eq(context))).thenReturn(
                "{\"action\":\"SEARCH\",\"query\":\"营业时间\",\"clarification\":null}"
                        + "{\"action\":\"CLARIFY\",\"query\":null,\"clarification\":\"请说明问题\"}");

        assertThatThrownBy(() -> client.understand(context))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldRejectInvalidActionFieldCombination() {
        when(aiChatClient.chatAsJson(anyString(), eq(context))).thenReturn(
                "{\"action\":\"SEARCH\",\"query\":null,\"clarification\":\"请说明问题\"}");

        assertThatThrownBy(() -> client.understand(context))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldRejectUnexpectedFields() {
        when(aiChatClient.chatAsJson(anyString(), eq(context))).thenReturn(
                "{\"action\":\"SEARCH\",\"query\":\"营业时间\",\"clarification\":null,\"userId\":88}");

        assertThatThrownBy(() -> client.understand(context))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }
}
