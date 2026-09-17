package com.cloudmeal.service.impl;

import com.cloudmeal.entity.AiChatMessage;
import com.cloudmeal.enums.AiChatRole;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class AiOrderStatusIntentRouterTest {

    private final AiOrderStatusIntentRouter router = new AiOrderStatusIntentRouter();

    @Test
    void shouldRecognizeClearCurrentOrderTrackingQuestions() {
        assertThat(router.isExplicitOrderStatusQuestion("我的订单到哪了？")).isTrue();
        assertThat(router.isExplicitOrderStatusQuestion("商家接单了吗")).isTrue();
        assertThat(router.isExplicitOrderStatusQuestion("这笔订单什么时候送到")).isTrue();
        assertThat(router.isExplicitOrderStatusQuestion("查询订单")).isTrue();
        assertThat(router.isExplicitOrderStatusQuestion("查一下我的订单")).isTrue();
    }

    @Test
    void shouldNotConfuseOrderOperationsOrGeneralKnowledgeWithStatusQuery() {
        assertThat(router.isExplicitOrderStatusQuestion("怎么取消订单")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("订单状态有哪些")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("退款什么时候到账")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("我的订单退款还要多久")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("我的订单退款到哪了")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("当前订单退款还要多久")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("这笔订单状态下怎么取消")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("我的订单为什么不能付款")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("我的订单状态有几种")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("订单状态怎么流转")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("各订单状态代表什么")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("我的订单状态都是什么意思")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("我的订单状态各自代表什么")).isFalse();
        assertThat(router.isExplicitOrderStatusQuestion("我的订单状态是怎么流转的")).isFalse();
    }

    @Test
    void shouldRecognizeSuffixOnlyAfterDisplayedCandidateList() {
        AiChatMessage choices = AiChatMessage.builder()
                .role(AiChatRole.ASSISTANT)
                .content("您有多笔进行中订单：尾号4321；尾号8765。请回复上面显示的订单尾号。")
                .build();
        AiChatMessage reply = AiChatMessage.builder()
                .role(AiChatRole.USER).content("4321").build();

        assertThat(router.isDisplayedOrderSelection(
                Arrays.asList(choices, reply), "4321")).isTrue();

        AiChatMessage selectedOrder = AiChatMessage.builder()
                .role(AiChatRole.ASSISTANT)
                .content("订单尾号2038当前状态：待接单。")
                .build();
        AiChatMessage anotherReply = AiChatMessage.builder()
                .role(AiChatRole.USER).content("9999").build();
        assertThat(router.isDisplayedOrderSelection(
                Arrays.asList(selectedOrder, anotherReply), "9999")).isTrue();
    }

    @Test
    void shouldRejectBareNumberWithoutImmediatelyPrecedingCandidateList() {
        AiChatMessage choices = AiChatMessage.builder()
                .role(AiChatRole.ASSISTANT)
                .content("您有多笔进行中订单：尾号4321。请回复上面显示的订单尾号。")
                .build();
        AiChatMessage unrelated = AiChatMessage.builder()
                .role(AiChatRole.ASSISTANT).content("配送费固定为6元。")
                .build();
        AiChatMessage otherUserMessage = AiChatMessage.builder()
                .role(AiChatRole.USER).content("先问个别的问题").build();
        AiChatMessage currentReply = AiChatMessage.builder()
                .role(AiChatRole.USER).content("4321").build();

        assertThat(router.isDisplayedOrderSelection(
                Collections.singletonList(unrelated), "4321")).isFalse();
        assertThat(router.isDisplayedOrderSelection(
                Collections.emptyList(), "4321")).isFalse();
        assertThat(router.isDisplayedOrderSelection(
                Arrays.asList(choices, unrelated, currentReply), "4321")).isFalse();
        assertThat(router.isDisplayedOrderSelection(
                Arrays.asList(choices, otherUserMessage, currentReply), "4321")).isFalse();
    }
}
