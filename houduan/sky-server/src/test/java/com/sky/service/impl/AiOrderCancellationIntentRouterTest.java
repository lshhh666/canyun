package com.sky.service.impl;

import com.sky.entity.AiChatMessage;
import com.sky.enums.AiChatRole;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AiOrderCancellationIntentRouterTest {

    private final AiOrderCancellationIntentRouter router =
            new AiOrderCancellationIntentRouter();

    @Test
    void shouldRecognizeExplicitCancellationButNotKnowledgeQuestions() {
        assertThat(router.isCancellationRequest("帮我取消这单")).isTrue();
        assertThat(router.isCancellationRequest("帮我取消")).isTrue();
        assertThat(router.isCancellationRequest("取消它")).isTrue();
        assertThat(router.isCancellationRequest("把刚才那单退掉")).isTrue();
        assertThat(router.isCancellationRequest("我想取消订单")).isTrue();
        assertThat(router.isCancellationRequest("我要取消订单")).isTrue();
        assertThat(router.isCancellationRequest("我不想取消订单")).isFalse();
        assertThat(router.isCancellationRequest("不要取消订单")).isFalse();
        assertThat(router.isCancellationRequest("怎么取消订单？")).isFalse();
        assertThat(router.isCancellationRequest("这笔订单可以取消吗？")).isFalse();
        assertThat(router.isCancellationRequest("取消后退款到哪里？")).isFalse();
        assertThat(router.isCancellationRequest("好的")).isFalse();
    }

    @Test
    void shouldRecognizeSuffixOnlyAfterCancellationChoices() {
        assertThat(router.isDisplayedCancellationSelection(Arrays.asList(
                AiChatMessage.builder().role(AiChatRole.ASSISTANT)
                        .content("您有多笔进行中订单。请选择要取消的订单尾号。").build(),
                AiChatMessage.builder().role(AiChatRole.USER).content("5412").build()
        ), "5412")).isTrue();

        assertThat(router.isDisplayedCancellationSelection(Arrays.asList(
                AiChatMessage.builder().role(AiChatRole.ASSISTANT)
                        .content("您有多笔进行中订单。请回复上面显示的订单尾号。").build(),
                AiChatMessage.builder().role(AiChatRole.USER).content("5412").build()
        ), "5412")).isFalse();
    }

    @Test
    void shouldRecognizeEligibilityQuestionAndExtractSuffix() {
        assertThat(router.isCancellationEligibilityQuestion("3651能取消吗")).isTrue();
        assertThat(router.extractOrderNumberSuffix("3651能取消吗")).isEqualTo("3651");
        assertThat(router.isCancellationEligibilityQuestion("这单可不可以取消？")).isTrue();
        assertThat(router.extractOrderNumberSuffix("这单可不可以取消？")).isNull();
        assertThat(router.isCancellationEligibilityQuestion("能取消吗？")).isTrue();
        assertThat(router.isCancellationEligibilityQuestion("什么订单可以取消？")).isFalse();
        assertThat(router.isCancellationEligibilityQuestion("哪些订单能取消？")).isFalse();
        assertThat(router.isCancellationEligibilityQuestion("待接单的订单能取消吗？")).isFalse();
        assertThat(router.isCancellationEligibilityQuestion("待付款订单可以取消吗？")).isFalse();
        assertThat(router.isCancellationEligibilityQuestion("待接单能取消吗？")).isFalse();
        assertThat(router.isCancellationEligibilityQuestion("这笔订单待接单能取消吗？")).isTrue();
        assertThat(router.isCancellationEligibilityQuestion("帮我取消")).isFalse();
    }
}
