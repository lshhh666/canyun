package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.Orders;
import com.sky.enums.AiOrderQueryOutcome;
import com.sky.enums.AiOrderStatus;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.OrderMapper;
import com.sky.service.model.AiOrderStatusResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOrderStatusServiceImplTest {

    private static final Long USER_ID = 18L;

    @Mock
    private OrderMapper orderMapper;

    private AiOrderStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiOrderStatusServiceImpl(orderMapper);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldRejectRequestWithoutAuthenticatedUser() {
        assertThatThrownBy(() -> service.queryMyActiveOrder(null))
                .isInstanceOf(UserNotLoginException.class);

        verifyNoInteractions(orderMapper);
    }

    @Test
    void shouldReturnNoActiveOrder() {
        BaseContext.setCurrentId(USER_ID);
        when(orderMapper.listActiveByUserId(USER_ID))
                .thenReturn(Collections.emptyList());

        AiOrderStatusResult result = service.queryMyActiveOrder(null);

        assertThat(result.getOutcome()).isEqualTo(AiOrderQueryOutcome.NO_ACTIVE_ORDER);
        assertThat(result.getCandidates()).isEmpty();
        verify(orderMapper).listActiveByUserId(USER_ID);
    }

    @Test
    void shouldReturnOnlyActiveOrderWithoutExposingSensitiveFields() {
        BaseContext.setCurrentId(USER_ID);
        Orders order = order("202609081100004321", Orders.CONFIRMED, 11, 0);
        order.setPhone("13800000000");
        order.setAddress("不应进入AI结果的地址");
        when(orderMapper.listActiveByUserId(USER_ID))
                .thenReturn(Collections.singletonList(order));

        AiOrderStatusResult result = service.queryMyActiveOrder(null);

        assertThat(result.getOutcome()).isEqualTo(AiOrderQueryOutcome.FOUND);
        assertThat(result.getSelectedOrder().getOrderNumberSuffix()).isEqualTo("4321");
        assertThat(result.getSelectedOrder().getStatus()).isEqualTo(AiOrderStatus.CONFIRMED);
        assertThat(result.getSelectedOrder())
                .hasNoNullFieldsOrPropertiesExcept("estimatedDeliveryTime");
    }

    @Test
    void shouldReturnNotFoundWhenSingleActiveOrderDoesNotMatchRequestedSuffix() {
        BaseContext.setCurrentId(USER_ID);
        when(orderMapper.listActiveByUserId(USER_ID))
                .thenReturn(Collections.singletonList(
                        order("202609081100004321", Orders.CONFIRMED, 11, 0)));

        AiOrderStatusResult result = service.queryMyActiveOrder("9999");

        assertThat(result.getOutcome()).isEqualTo(AiOrderQueryOutcome.ORDER_NOT_FOUND);
        assertThat(result.getSelectedOrder()).isNull();
        assertThat(result.getCandidates()).extracting("orderNumberSuffix")
                .containsExactly("4321");
    }

    @Test
    void shouldRequireSelectionAndThenMatchSuffixInsideCurrentUsersOrders() {
        BaseContext.setCurrentId(USER_ID);
        Orders newest = order("202609081205004321", Orders.PENDING_PAYMENT, 12, 5);
        Orders older = order("202609081200008765", Orders.CONFIRMED, 12, 0);
        when(orderMapper.listActiveByUserId(USER_ID))
                .thenReturn(Arrays.asList(newest, older));

        AiOrderStatusResult first = service.queryMyActiveOrder(null);
        AiOrderStatusResult selected = service.queryMyActiveOrder("8765");

        assertThat(first.getOutcome()).isEqualTo(AiOrderQueryOutcome.SELECTION_REQUIRED);
        assertThat(first.getCandidates()).extracting("orderNumberSuffix")
                .containsExactly("4321", "8765");
        assertThat(selected.getOutcome()).isEqualTo(AiOrderQueryOutcome.FOUND);
        assertThat(selected.getSelectedOrder().getOrderNumberSuffix()).isEqualTo("8765");
        verify(orderMapper, org.mockito.Mockito.times(2)).listActiveByUserId(USER_ID);
    }

    @Test
    void shouldReturnNotFoundWhenSuffixDoesNotMatchDisplayedOrders() {
        BaseContext.setCurrentId(USER_ID);
        when(orderMapper.listActiveByUserId(USER_ID)).thenReturn(Arrays.asList(
                order("202609081205004321", Orders.PENDING_PAYMENT, 12, 5),
                order("202609081200008765", Orders.CONFIRMED, 12, 0)));

        AiOrderStatusResult result = service.queryMyActiveOrder("9999");

        assertThat(result.getOutcome()).isEqualTo(AiOrderQueryOutcome.ORDER_NOT_FOUND);
        assertThat(result.getSelectedOrder()).isNull();
    }

    @Test
    void shouldDisplayLongerUniqueSuffixWhenLastFourDigitsCollide() {
        BaseContext.setCurrentId(USER_ID);
        when(orderMapper.listActiveByUserId(USER_ID)).thenReturn(Arrays.asList(
                order("202609081205014321", Orders.PENDING_PAYMENT, 12, 5),
                order("202609081200028765", Orders.CONFIRMED, 12, 0),
                order("202609081155024321", Orders.DELIVERY_IN_PROGRESS, 11, 55)));

        AiOrderStatusResult choices = service.queryMyActiveOrder(null);
        AiOrderStatusResult selected = service.queryMyActiveOrder("14321");

        assertThat(choices.getCandidates()).extracting("orderNumberSuffix")
                .containsExactly("14321", "28765", "24321");
        assertThat(selected.getOutcome()).isEqualTo(AiOrderQueryOutcome.FOUND);
        assertThat(selected.getSelectedOrder().getOrderNumberSuffix()).isEqualTo("14321");
    }

    @Test
    void shouldExposeOnlyThreeSelectableOrdersAndRejectHiddenOrderSuffix() {
        BaseContext.setCurrentId(USER_ID);
        when(orderMapper.listActiveByUserId(USER_ID)).thenReturn(Arrays.asList(
                order("202609081205001111", Orders.PENDING_PAYMENT, 12, 5),
                order("202609081200002222", Orders.CONFIRMED, 12, 0),
                order("202609081155003333", Orders.DELIVERY_IN_PROGRESS, 11, 55),
                order("202609081150004444", Orders.CONFIRMED, 11, 50)));

        AiOrderStatusResult choices = service.queryMyActiveOrder(null);
        AiOrderStatusResult hiddenSelection = service.queryMyActiveOrder("4444");

        assertThat(choices.getOutcome()).isEqualTo(AiOrderQueryOutcome.SELECTION_REQUIRED);
        assertThat(choices.getCandidates()).extracting("orderNumberSuffix")
                .containsExactly("1111", "2222", "3333");
        assertThat(choices.isHasMore()).isTrue();
        assertThat(hiddenSelection.getOutcome()).isEqualTo(AiOrderQueryOutcome.ORDER_NOT_FOUND);
        assertThat(hiddenSelection.getSelectedOrder()).isNull();
    }

    private Orders order(String number, Integer status, int hour, int minute) {
        return Orders.builder()
                .id(Long.valueOf(number.substring(number.length() - 6)))
                .number(number)
                .status(status)
                .orderTime(LocalDateTime.of(2026, 9, 8, hour, minute))
                .build();
    }
}
