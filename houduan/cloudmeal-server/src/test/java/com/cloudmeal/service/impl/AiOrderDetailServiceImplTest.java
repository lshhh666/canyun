package com.cloudmeal.service.impl;

import com.cloudmeal.context.BaseContext;
import com.cloudmeal.entity.AiChatSession;
import com.cloudmeal.entity.OrderDetail;
import com.cloudmeal.entity.Orders;
import com.cloudmeal.enums.AiOrderDetailOutcome;
import com.cloudmeal.mapper.AiChatSessionMapper;
import com.cloudmeal.mapper.OrderMapper;
import com.cloudmeal.mapper.OrderdetailMapper;
import com.cloudmeal.service.model.AiOrderDetailResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOrderDetailServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 25L;
    private static final Long ORDER_ID = 88L;

    @Mock private AiChatSessionMapper sessionMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderdetailMapper orderdetailMapper;

    private AiOrderDetailServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
        service = new AiOrderDetailServiceImpl(sessionMapper, orderMapper, orderdetailMapper);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldAskForSelectionWhenSessionHasNoSelectedOrder() {
        when(sessionMapper.selectOwned(SESSION_ID, USER_ID))
                .thenReturn(AiChatSession.builder().id(SESSION_ID).userId(USER_ID).build());

        AiOrderDetailResult result = service.querySelectedOrderDetail(SESSION_ID);

        assertThat(result.getOutcome()).isEqualTo(AiOrderDetailOutcome.NO_SELECTED_ORDER);
        verify(orderMapper, never()).getAiDetailByIdAndUserId(ORDER_ID, USER_ID);
    }

    @Test
    void shouldReturnSafeDetailsForOwnedOrderRegardlessOfOrderStatus() {
        when(sessionMapper.selectOwned(SESSION_ID, USER_ID)).thenReturn(AiChatSession.builder()
                .id(SESSION_ID).userId(USER_ID).selectedOrderId(ORDER_ID).build());
        Orders completedOrder = Orders.builder()
                .id(ORDER_ID).number("202609081300002038")
                .status(Orders.COMPLETED).payStatus(Orders.PAID)
                .goodsAmount(new BigDecimal("75.00")).packAmount(3)
                .deliveryFee(new BigDecimal("6.00"))
                .originalAmount(new BigDecimal("84.00"))
                .discountAmount(new BigDecimal("6.00"))
                .amount(new BigDecimal("78.00"))
                .remark("不要辣").phone("13800000000").address("不应返回的地址")
                .build();
        when(orderMapper.getAiDetailByIdAndUserId(ORDER_ID, USER_ID))
                .thenReturn(completedOrder);
        when(orderdetailMapper.listAiDetailByOrderId(ORDER_ID)).thenReturn(Arrays.asList(
                OrderDetail.builder().name("米饭").number(2)
                        .amount(new BigDecimal("3.00")).build(),
                OrderDetail.builder().name("黄焖鸡").dishFlavor("微辣").number(1)
                        .amount(new BigDecimal("69.00")).build()));

        AiOrderDetailResult result = service.querySelectedOrderDetail(SESSION_ID);

        assertThat(result.getOutcome()).isEqualTo(AiOrderDetailOutcome.FOUND);
        assertThat(result.getOrderNumberSuffix()).isEqualTo("2038");
        assertThat(result.getFinalAmount()).isEqualByComparingTo("78.00");
        assertThat(result.getItems()).extracting("name").containsExactly("米饭", "黄焖鸡");
        assertThat(result.toString()).doesNotContain("13800000000", "不应返回的地址");
        verify(orderMapper).getAiDetailByIdAndUserId(ORDER_ID, USER_ID);
    }
}
