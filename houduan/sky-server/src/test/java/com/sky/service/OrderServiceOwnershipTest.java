package com.sky.service;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderdetailMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.impl.OrderServiceImpl;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户订单接口的归属校验测试，防止通过猜测订单 ID 越权读取或操作他人订单。
 */
class OrderServiceOwnershipTest {

    private static final Long CURRENT_USER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;
    private static final Long ORDER_ID = 99L;

    private OrderMapper orderMapper;
    private OrderdetailMapper orderdetailMapper;
    private ShoppingCartMapper shoppingCartMapper;
    private WebSocketServer webSocketServer;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(CURRENT_USER_ID);
        orderMapper = Mockito.mock(OrderMapper.class);
        orderdetailMapper = Mockito.mock(OrderdetailMapper.class);
        shoppingCartMapper = Mockito.mock(ShoppingCartMapper.class);
        webSocketServer = Mockito.mock(WebSocketServer.class);

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderdetailMapper", orderdetailMapper);
        ReflectionTestUtils.setField(service, "shoppingCartMapper", shoppingCartMapper);
        ReflectionTestUtils.setField(service, "webSocketServer", webSocketServer);
    }

    @AfterEach
    void clearContext() {
        BaseContext.removeCurrentId();
    }

    @Test
    void orderDetailRejectsForeignOrderBeforeLoadingDetails() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(orderOwnedBy(OTHER_USER_ID));

        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class, () -> service.orderDetail(ORDER_ID));

        assertEquals(MessageConstant.ORDER_NOT_FOUND, exception.getMessage());
        verify(orderdetailMapper, never()).getByOrderId(any());
    }

    @Test
    void orderDetailReturnsCurrentUsersOrder() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(orderOwnedBy(CURRENT_USER_ID));
        when(orderdetailMapper.getByOrderId(ORDER_ID)).thenReturn(Collections.singletonList(orderDetail()));

        OrderVO result = service.orderDetail(ORDER_ID);

        assertEquals(CURRENT_USER_ID, result.getUserId());
        assertEquals("鱼香肉丝x1", result.getOrderDishes());
        assertEquals(1, result.getOrderDetailList().size());
    }

    @Test
    void repetitionRejectsForeignOrderBeforeLoadingDetailsOrWritingCart() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(orderOwnedBy(OTHER_USER_ID));

        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class, () -> service.repetition(ORDER_ID));

        assertEquals(MessageConstant.ORDER_NOT_FOUND, exception.getMessage());
        verify(orderdetailMapper, never()).getByOrderId(any());
        verify(shoppingCartMapper, never()).addShoppingCart(any());
    }

    @Test
    void repetitionCopiesCurrentUsersOrderIntoCurrentUsersCart() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(orderOwnedBy(CURRENT_USER_ID));
        when(orderdetailMapper.getByOrderId(ORDER_ID)).thenReturn(Collections.singletonList(orderDetail()));

        service.repetition(ORDER_ID);

        ArgumentCaptor<ShoppingCart> cartCaptor = ArgumentCaptor.forClass(ShoppingCart.class);
        verify(shoppingCartMapper).addShoppingCart(cartCaptor.capture());
        ShoppingCart cart = cartCaptor.getValue();
        assertEquals(CURRENT_USER_ID, cart.getUserId());
        assertEquals("鱼香肉丝", cart.getName());
        assertNull(cart.getId());
    }

    @Test
    void reminderRejectsForeignOrderWithoutSendingWebSocketMessage() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(orderOwnedBy(OTHER_USER_ID));

        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class, () -> service.reminder(ORDER_ID));

        assertEquals(MessageConstant.ORDER_NOT_FOUND, exception.getMessage());
        verify(webSocketServer, never()).sendToAllClient(any());
    }

    @Test
    void reminderAllowsCurrentUserWhenOrderIsAwaitingConfirmation() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(orderOwnedBy(CURRENT_USER_ID));

        service.reminder(ORDER_ID);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(webSocketServer).sendToAllClient(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("ORDER-99"));
    }

    private Orders orderOwnedBy(Long userId) {
        return Orders.builder()
                .id(ORDER_ID)
                .userId(userId)
                .number("ORDER-99")
                .status(Orders.TO_BE_CONFIRMED)
                .orderTime(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    private OrderDetail orderDetail() {
        return OrderDetail.builder()
                .id(1L)
                .orderId(ORDER_ID)
                .dishId(10L)
                .name("鱼香肉丝")
                .number(1)
                .amount(new BigDecimal("20.00"))
                .build();
    }
}
