package com.sky.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOrderDetailIntentRouterTest {

    private final AiOrderDetailIntentRouter router = new AiOrderDetailIntentRouter();

    @Test
    void shouldRecognizeQuestionsAboutSelectedOrderDetails() {
        assertThat(router.isSelectedOrderDetailQuestion("这单买了什么，一共多少钱？")).isTrue();
        assertThat(router.isSelectedOrderDetailQuestion("刚才那单配送费多少")).isTrue();
        assertThat(router.isSelectedOrderDetailQuestion("这个订单有备注吗")).isTrue();
    }

    @Test
    void shouldNotGuessOrderWhenQuestionDoesNotReferenceSelectedOrder() {
        assertThat(router.isSelectedOrderDetailQuestion("配送费怎么算")).isFalse();
        assertThat(router.isSelectedOrderDetailQuestion("推荐不辣的菜")).isFalse();
        assertThat(router.isSelectedOrderDetailQuestion("这笔订单什么时候送到")).isFalse();
    }
}
