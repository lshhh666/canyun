package com.cloudmeal.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloudmeal.client.AiChatClient;
import com.cloudmeal.client.model.AiEvidenceReference;
import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.entity.AiChatMessage;
import com.cloudmeal.enums.AiChatRole;
import com.cloudmeal.enums.AiEvidenceDecision;
import com.cloudmeal.exception.AiServiceException;
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
class ZhipuAiEvidenceSufficiencyClientTest {

    @Mock
    private AiChatClient aiChatClient;

    private ZhipuAiEvidenceSufficiencyClient client;
    private List<AiChatMessage> context;
    private List<AiEvidenceReference> references;

    @BeforeEach
    void setUp() {
        client = new ZhipuAiEvidenceSufficiencyClient(aiChatClient, new ObjectMapper());
        context = Collections.singletonList(AiChatMessage.builder()
                .role(AiChatRole.USER)
                .content("退款多久能到账")
                .build());
        references = Collections.singletonList(AiEvidenceReference.builder()
                .title("退款到账方式")
                .content("订单退款将原路退回至用户原支付账户。")
                .build());
    }

    @Test
    void shouldReturnAnswerableAndSendOnlyMinimalKnowledgeFields() {
        when(aiChatClient.chatAsJson(anyString(), eq(context)))
                .thenReturn("{\"decision\":\"ANSWERABLE\"}");

        AiEvidenceDecision decision = client.judge(context, references);

        assertThat(decision).isEqualTo(AiEvidenceDecision.ANSWERABLE);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiChatClient).chatAsJson(prompt.capture(), eq(context));
        assertThat(prompt.getValue())
                .contains("主题相关、措辞相似")
                .contains("退款原路退回")
                .contains("不能回答退款多久到账")
                .contains("COUPON_ELIGIBILITY_REQUIRED")
                .contains("ORDER_STATUS_REQUIRED")
                .contains("ORDER_DETAIL_REQUIRED")
                .contains("\"title\":\"退款到账方式\"")
                .contains("\"content\":\"订单退款将原路退回至用户原支付账户。\"")
                .doesNotContain("score", "embedding");
    }

    @Test
    void shouldReturnInsufficient() {
        when(aiChatClient.chatAsJson(anyString(), eq(context)))
                .thenReturn("```json\n{\"decision\":\"INSUFFICIENT\"}\n```");

        assertThat(client.judge(context, references))
                .isEqualTo(AiEvidenceDecision.INSUFFICIENT);
    }

    @Test
    void shouldReturnCouponEligibilityRequiredForCurrentUserCouponQuestion() {
        when(aiChatClient.chatAsJson(anyString(), eq(context)))
                .thenReturn("{\"decision\":\"COUPON_ELIGIBILITY_REQUIRED\"}");

        assertThat(client.judge(context, references))
                .isEqualTo(AiEvidenceDecision.COUPON_ELIGIBILITY_REQUIRED);
    }

    @Test
    void shouldAllowEmptyEvidenceAndRouteCurrentUserQuestionToTool() {
        List<AiChatMessage> currentUserQuestion = Collections.singletonList(
                AiChatMessage.builder()
                        .role(AiChatRole.USER)
                        .content("为什么我的优惠券不能用")
                        .build());
        when(aiChatClient.chatAsJson(anyString(), eq(currentUserQuestion)))
                .thenReturn("{\"decision\":\"COUPON_ELIGIBILITY_REQUIRED\"}");

        AiEvidenceDecision decision = client.judge(
                currentUserQuestion, Collections.emptyList());

        assertThat(decision).isEqualTo(AiEvidenceDecision.COUPON_ELIGIBILITY_REQUIRED);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(aiChatClient).chatAsJson(prompt.capture(), eq(currentUserQuestion));
        assertThat(prompt.getValue())
                .contains("参考知识为空时绝不能判定ANSWERABLE")
                .contains("查询当前订单状态", "必须判定INSUFFICIENT")
                .endsWith("[]");
    }

    @Test
    void shouldAllowEmptyEvidenceAndReturnInsufficientForUnknownRule() {
        when(aiChatClient.chatAsJson(anyString(), eq(context)))
                .thenReturn("{\"decision\":\"INSUFFICIENT\"}");

        assertThat(client.judge(context, Collections.emptyList()))
                .isEqualTo(AiEvidenceDecision.INSUFFICIENT);
    }

    @Test
    void shouldReturnOrderStatusRequiredForCurrentUsersOrderProgress() {
        List<AiChatMessage> orderQuestion = Collections.singletonList(
                AiChatMessage.builder()
                        .role(AiChatRole.USER)
                        .content("我的订单到哪了")
                        .build());
        when(aiChatClient.chatAsJson(anyString(), eq(orderQuestion)))
                .thenReturn("{\"decision\":\"ORDER_STATUS_REQUIRED\"}");

        assertThat(client.judge(orderQuestion, Collections.emptyList()))
                .isEqualTo(AiEvidenceDecision.ORDER_STATUS_REQUIRED);
    }

    @Test
    void shouldReturnOrderDetailRequiredForSelectedOrderQuestion() {
        List<AiChatMessage> orderQuestion = Collections.singletonList(
                AiChatMessage.builder()
                        .role(AiChatRole.USER)
                        .content("这单买了什么，一共多少钱")
                        .build());
        when(aiChatClient.chatAsJson(anyString(), eq(orderQuestion)))
                .thenReturn("{\"decision\":\"ORDER_DETAIL_REQUIRED\"}");

        assertThat(client.judge(orderQuestion, Collections.emptyList()))
                .isEqualTo(AiEvidenceDecision.ORDER_DETAIL_REQUIRED);
    }

    @Test
    void shouldRejectUnexpectedOrMultipleFields() {
        when(aiChatClient.chatAsJson(anyString(), eq(context)))
                .thenReturn("{\"decision\":\"ANSWERABLE\",\"answer\":\"3至5天\"}");

        assertThatThrownBy(() -> client.judge(context, references))
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldPropagateProviderFailureAsServiceFailure() {
        AiServiceException failure = new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        when(aiChatClient.chatAsJson(anyString(), eq(context))).thenThrow(failure);

        assertThatThrownBy(() -> client.judge(context, references)).isSameAs(failure);
    }
}
