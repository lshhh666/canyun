package com.sky.service;

import com.sky.context.BaseContext;
import com.sky.constant.MessageConstant;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.UserCoupon;
import com.sky.enums.UserCouponStatus;
import com.sky.exception.CouponBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderdetailMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserCouponMapper;
import com.sky.service.impl.OrderServiceImpl;
import com.sky.vo.OrderPreviewVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderPaymentVO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplSubmitTest {
    private OrderMapper orderMapper;
    private OrderdetailMapper detailMapper;
    private ShoppingCartMapper cartMapper;
    private AddressBookMapper addressMapper;
    private OrderPricingService pricingService;
    private WebSocketServer webSocketServer;
    private UserCouponMapper userCouponMapper;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(7L);
        orderMapper = Mockito.mock(OrderMapper.class);
        detailMapper = Mockito.mock(OrderdetailMapper.class);
        cartMapper = Mockito.mock(ShoppingCartMapper.class);
        addressMapper = Mockito.mock(AddressBookMapper.class);
        pricingService = Mockito.mock(OrderPricingService.class);
        webSocketServer = Mockito.mock(WebSocketServer.class);
        userCouponMapper = Mockito.mock(UserCouponMapper.class);
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderdetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "shoppingCartMapper", cartMapper);
        ReflectionTestUtils.setField(service, "addressBookMapper", addressMapper);
        ReflectionTestUtils.setField(service, "orderPricingService", pricingService);
        ReflectionTestUtils.setField(service, "webSocketServer", webSocketServer);
        ReflectionTestUtils.setField(service, "userCouponMapper", userCouponMapper);
    }

    @AfterEach
    void clearContext() { BaseContext.removeCurrentId(); }

    @Test
    void storesQuoteInsteadOfForgedClientMoney() {
        LocalDateTime eta = LocalDateTime.of(2026, 8, 12, 12, 30);
        when(pricingService.preview(7L, 12L)).thenReturn(OrderPreviewVO.builder()
                .goodsAmount(new BigDecimal("49.00")).packAmount(new BigDecimal("3.00"))
                .deliveryFee(new BigDecimal("6.00")).totalAmount(new BigDecimal("58.00"))
                .estimatedDeliveryTime(eta).build());
        ShoppingCart cart = ShoppingCart.builder().id(3L).name("dish").amount(new BigDecimal("49.00"))
                .number(1).userId(7L).build();
        when(cartMapper.listShoppingCartByUserId(7L)).thenReturn(Collections.singletonList(cart));
        when(addressMapper.getById(12L)).thenReturn(AddressBook.builder().id(12L).userId(7L)
                .provinceName("A").cityName("B").districtName("C").detail("D")
                .consignee("User").phone("13800000000").build());
        doAnswer(invocation -> { invocation.<Orders>getArgument(0).setId(99L); return null; })
                .when(orderMapper).add(any(Orders.class));
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(12L);
        dto.setPayMethod(1);
        dto.setAmount(new BigDecimal("0.01"));
        // Current miniapp omits server-owned quote fields such as packAmount.
        dto.setPackAmount(null);
        dto.setTablewareNumber(0);
        dto.setTablewareStatus(0);
        dto.setDeliveryStatus(1);
        dto.setEstimatedDeliveryTime("2030-01-01 00:00:00");

        OrderSubmitVO result = service.orderSubmit(dto);

        ArgumentCaptor<Orders> order = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).add(order.capture());
        assertEquals(new BigDecimal("58.00"), order.getValue().getAmount());
        assertEquals(new BigDecimal("58.00"), order.getValue().getOriginalAmount());
        assertEquals(BigDecimal.ZERO, order.getValue().getDiscountAmount());
        assertEquals(null, order.getValue().getUserCouponId());
        assertEquals(3, order.getValue().getPackAmount());
        assertEquals(eta, order.getValue().getEstimatedDeliveryTime());
        assertEquals(new BigDecimal("58.00"), result.getOrderAmount());
        assertEquals(99L, result.getId());
        InOrder inOrder = Mockito.inOrder(orderMapper, detailMapper, cartMapper);
        inOrder.verify(orderMapper).add(any(Orders.class));
        inOrder.verify(detailMapper).add(any(OrderDetail.class));
        inOrder.verify(cartMapper).deleteShoppingCart(7L);
    }

    @Test
    void validCouponUsesServerGoodsAmountAndStoresPriceSnapshots() {
        LocalDateTime now = LocalDateTime.now();
        when(pricingService.preview(7L, 12L)).thenReturn(OrderPreviewVO.builder()
                .goodsAmount(new BigDecimal("60.00"))
                .packAmount(new BigDecimal("2.00"))
                .deliveryFee(new BigDecimal("6.00"))
                .totalAmount(new BigDecimal("68.00"))
                .estimatedDeliveryTime(now.plusMinutes(30))
                .build());
        when(cartMapper.listShoppingCartByUserId(7L)).thenReturn(Collections.singletonList(
                ShoppingCart.builder().id(3L).name("dish").amount(new BigDecimal("60.00"))
                        .number(1).userId(7L).build()));
        when(addressMapper.getById(12L)).thenReturn(AddressBook.builder()
                .id(12L).userId(7L).provinceName("A").cityName("B")
                .districtName("C").detail("D").consignee("User")
                .phone("13800000000").build());
        when(userCouponMapper.selectById(101L)).thenReturn(new UserCoupon()
                .setId(101L)
                .setUserId(7L)
                .setThresholdAmount(new BigDecimal("50.00"))
                .setDiscountAmount(new BigDecimal("10.00"))
                .setStatus(UserCouponStatus.AVAILABLE)
                .setValidStartTime(now.minusMinutes(1))
                .setValidEndTime(now.plusDays(1)));
        doAnswer(invocation -> {
            invocation.<Orders>getArgument(0).setId(99L);
            return null;
        }).when(orderMapper).add(any(Orders.class));
        when(userCouponMapper.lockForOrder(eq(101L), eq(7L), eq(99L),
                eq(new BigDecimal("60.00")), any(LocalDateTime.class))).thenReturn(1);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(12L);
        dto.setPayMethod(1);
        dto.setAmount(new BigDecimal("0.01"));
        dto.setUserCouponId(101L);

        OrderSubmitVO result = service.orderSubmit(dto);

        ArgumentCaptor<Orders> order = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).add(order.capture());
        assertEquals(new BigDecimal("68.00"), order.getValue().getOriginalAmount());
        assertEquals(new BigDecimal("10.00"), order.getValue().getDiscountAmount());
        assertEquals(new BigDecimal("58.00"), order.getValue().getAmount());
        assertEquals(101L, order.getValue().getUserCouponId());
        assertEquals(new BigDecimal("58.00"), result.getOrderAmount());
        verify(userCouponMapper).lockForOrder(eq(101L), eq(7L), eq(99L),
                eq(new BigDecimal("60.00")), any(LocalDateTime.class));
    }

    @Test
    void lockFailureStopsOrderDetailsAndCartCleanup() {
        LocalDateTime now = LocalDateTime.now();
        when(pricingService.preview(7L, 12L)).thenReturn(OrderPreviewVO.builder()
                .goodsAmount(new BigDecimal("60.00"))
                .packAmount(new BigDecimal("2.00"))
                .deliveryFee(new BigDecimal("6.00"))
                .totalAmount(new BigDecimal("68.00"))
                .estimatedDeliveryTime(now.plusMinutes(30))
                .build());
        when(cartMapper.listShoppingCartByUserId(7L)).thenReturn(Collections.singletonList(
                ShoppingCart.builder().id(3L).name("dish").amount(new BigDecimal("60.00"))
                        .number(1).userId(7L).build()));
        when(addressMapper.getById(12L)).thenReturn(AddressBook.builder()
                .id(12L).userId(7L).provinceName("A").cityName("B")
                .districtName("C").detail("D").consignee("User")
                .phone("13800000000").build());
        when(userCouponMapper.selectById(101L)).thenReturn(new UserCoupon()
                .setId(101L)
                .setUserId(7L)
                .setThresholdAmount(new BigDecimal("50.00"))
                .setDiscountAmount(new BigDecimal("10.00"))
                .setStatus(UserCouponStatus.AVAILABLE)
                .setValidStartTime(now.minusMinutes(1))
                .setValidEndTime(now.plusDays(1)));
        doAnswer(invocation -> {
            invocation.<Orders>getArgument(0).setId(99L);
            return null;
        }).when(orderMapper).add(any(Orders.class));
        when(userCouponMapper.lockForOrder(eq(101L), eq(7L), eq(99L),
                eq(new BigDecimal("60.00")), any(LocalDateTime.class))).thenReturn(0);

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(12L);
        dto.setPayMethod(1);
        dto.setUserCouponId(101L);

        assertThrows(CouponBusinessException.class, () -> service.orderSubmit(dto));

        verify(detailMapper, never()).add(any(OrderDetail.class));
        verify(cartMapper, never()).deleteShoppingCart(any(Long.class));
    }

    @Test
    void pricingFailureDoesNotWriteOrClearAnything() {
        when(pricingService.preview(7L, 12L)).thenThrow(new OrderBusinessException("quote failed"));
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(12L);

        assertThrows(OrderBusinessException.class, () -> service.orderSubmit(dto));

        verify(orderMapper, never()).add(any(Orders.class));
        verify(detailMapper, never()).add(any(OrderDetail.class));
        verify(cartMapper, never()).deleteShoppingCart(any(Long.class));
    }

    @Test
    void repeatedDemoPaymentReturnsSuccessWithoutUpdatingAgain() {
        LocalDateTime eta = LocalDateTime.of(2026, 8, 14, 20, 15);
        Orders paidOrder = Orders.builder().id(15L).number("202608141943273346")
                .userId(7L)
                .status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID)
                .userCouponId(101L)
                .estimatedDeliveryTime(eta).build();
        when(orderMapper.getByNumber("202608141943273346")).thenReturn(paidOrder);
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("202608141943273346");
        dto.setPayMethod(1);

        OrderPaymentVO result = service.orderpayment(dto);

        assertEquals(eta.toString(), result.getEstimatedDeliveryTime());
        verify(orderMapper, never()).markPaidIfPending(any(Long.class), any(Long.class),
                any(Integer.class), any(LocalDateTime.class));
        verify(orderMapper, never()).update(any(Orders.class));
        verify(userCouponMapper, never()).markUsedByOrder(any(Long.class), any(Long.class),
                any(LocalDateTime.class));
        verify(webSocketServer, never()).sendToAllClient(any(String.class));
    }

    @Test
    void firstDemoPaymentAtomicallyMarksOrderThenCouponUsed() {
        Orders pendingOrder = Orders.builder()
                .id(15L)
                .userId(7L)
                .number("202608200001")
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .userCouponId(101L)
                .build();
        when(orderMapper.getByNumber("202608200001")).thenReturn(pendingOrder);
        when(orderMapper.markPaidIfPending(eq(15L), eq(7L), eq(1),
                any(LocalDateTime.class))).thenReturn(1);
        when(userCouponMapper.markUsedByOrder(eq(101L), eq(15L),
                any(LocalDateTime.class))).thenReturn(1);
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("202608200001");
        dto.setPayMethod(1);

        service.orderpayment(dto);

        ArgumentCaptor<LocalDateTime> paidAt = ArgumentCaptor.forClass(LocalDateTime.class);
        InOrder inOrder = Mockito.inOrder(orderMapper, userCouponMapper);
        inOrder.verify(orderMapper).markPaidIfPending(
                eq(15L), eq(7L), eq(1), paidAt.capture());
        inOrder.verify(userCouponMapper).markUsedByOrder(
                eq(101L), eq(15L), eq(paidAt.getValue()));
        verify(orderMapper, never()).update(any(Orders.class));
        verify(webSocketServer).sendToAllClient(any(String.class));
    }

    @Test
    void couponWriteOffFailureDoesNotUpdateOrderOrSendNotification() {
        Orders pendingOrder = Orders.builder()
                .id(15L)
                .userId(7L)
                .number("202608200002")
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .userCouponId(101L)
                .build();
        when(orderMapper.getByNumber("202608200002")).thenReturn(pendingOrder);
        when(orderMapper.markPaidIfPending(eq(15L), eq(7L), eq(1),
                any(LocalDateTime.class))).thenReturn(1);
        when(userCouponMapper.markUsedByOrder(eq(101L), eq(15L),
                any(LocalDateTime.class))).thenReturn(0);
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("202608200002");
        dto.setPayMethod(1);

        assertThrows(CouponBusinessException.class, () -> service.orderpayment(dto));

        verify(orderMapper).markPaidIfPending(eq(15L), eq(7L), eq(1),
                any(LocalDateTime.class));
        verify(orderMapper, never()).update(any(Orders.class));
        verify(webSocketServer, never()).sendToAllClient(any(String.class));
    }

    @Test
    void paymentWithoutCouponDoesNotAttemptCouponWriteOff() {
        Orders pendingOrder = Orders.builder()
                .id(16L)
                .userId(7L)
                .number("202608200003")
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .build();
        when(orderMapper.getByNumber("202608200003")).thenReturn(pendingOrder);
        when(orderMapper.markPaidIfPending(eq(16L), eq(7L), eq(1),
                any(LocalDateTime.class))).thenReturn(1);
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("202608200003");
        dto.setPayMethod(1);

        service.orderpayment(dto);

        verify(orderMapper).markPaidIfPending(eq(16L), eq(7L), eq(1),
                any(LocalDateTime.class));
        verify(userCouponMapper, never()).markUsedByOrder(
                any(Long.class), any(Long.class), any(LocalDateTime.class));
        verify(webSocketServer).sendToAllClient(any(String.class));
    }

    @Test
    void paymentLosingAtomicRaceDoesNotWriteOffCoupon() {
        Orders stalePendingOrder = Orders.builder()
                .id(17L)
                .userId(7L)
                .number("202608200004")
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .userCouponId(101L)
                .build();
        when(orderMapper.getByNumber("202608200004")).thenReturn(stalePendingOrder);
        when(orderMapper.markPaidIfPending(eq(17L), eq(7L), eq(1),
                any(LocalDateTime.class))).thenReturn(0);
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber("202608200004");
        dto.setPayMethod(1);

        assertThrows(OrderBusinessException.class, () -> service.orderpayment(dto));

        verify(userCouponMapper, never()).markUsedByOrder(
                any(Long.class), any(Long.class), any(LocalDateTime.class));
        verify(webSocketServer, never()).sendToAllClient(any(String.class));
    }

    @Test
    void userCannotCancelAnotherUsersOrder() {
        Orders anotherUsersOrder = Orders.builder()
                .id(88L)
                .userId(8L)
                .status(Orders.PENDING_PAYMENT)
                .build();
        when(orderMapper.getById(88L)).thenReturn(anotherUsersOrder);

        assertThrows(OrderBusinessException.class, () -> service.cancelByOrderId(88L));

        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    void cancellingUnpaidOrderReleasesLockedCoupon() {
        Orders pendingOrder = Orders.builder()
                .id(88L)
                .userId(7L)
                .status(Orders.PENDING_PAYMENT)
                .userCouponId(101L)
                .build();
        when(orderMapper.getById(88L)).thenReturn(pendingOrder);
        when(orderMapper.cancelIfPending(eq(88L), any(LocalDateTime.class), eq(null)))
                .thenReturn(1);
        when(userCouponMapper.releaseByOrder(eq(101L), eq(88L),
                any(LocalDateTime.class))).thenReturn(1);

        service.cancelByOrderId(88L);

        ArgumentCaptor<LocalDateTime> cancelTime = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderMapper).cancelIfPending(eq(88L), cancelTime.capture(), eq(null));
        verify(userCouponMapper).releaseByOrder(eq(101L), eq(88L),
                eq(cancelTime.getValue()));
        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    void couponReleaseFailureRejectsCancellation() {
        Orders pendingOrder = Orders.builder()
                .id(88L)
                .userId(7L)
                .status(Orders.PENDING_PAYMENT)
                .userCouponId(101L)
                .build();
        when(orderMapper.getById(88L)).thenReturn(pendingOrder);
        when(orderMapper.cancelIfPending(eq(88L), any(LocalDateTime.class), eq(null)))
                .thenReturn(1);
        when(userCouponMapper.releaseByOrder(eq(101L), eq(88L),
                any(LocalDateTime.class))).thenReturn(0);

        assertThrows(CouponBusinessException.class, () -> service.cancelByOrderId(88L));

        verify(userCouponMapper).releaseByOrder(eq(101L), eq(88L),
                any(LocalDateTime.class));
    }

    @Test
    void cancellingPaidOrderIsRejectedUntilRefundFlowExists() {
        Orders paidOrder = Orders.builder()
                .id(88L)
                .userId(7L)
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .userCouponId(101L)
                .build();
        when(orderMapper.getById(88L)).thenReturn(paidOrder);

        assertThrows(OrderBusinessException.class, () -> service.cancelByOrderId(88L));

        verify(orderMapper, never()).cancelIfPending(any(Long.class), any(LocalDateTime.class),
                any());
        verify(orderMapper, never()).update(any(Orders.class));
        verify(userCouponMapper, never()).releaseByOrder(any(Long.class), any(Long.class),
                any(LocalDateTime.class));
    }

    @Test
    void userCancellationLosingAtomicRaceDoesNotReleaseCoupon() {
        Orders stalePendingOrder = Orders.builder()
                .id(88L)
                .userId(7L)
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .userCouponId(101L)
                .build();
        when(orderMapper.getById(88L)).thenReturn(stalePendingOrder);
        when(orderMapper.cancelIfPending(eq(88L), any(LocalDateTime.class), eq(null)))
                .thenReturn(0);

        assertThrows(OrderBusinessException.class, () -> service.cancelByOrderId(88L));

        verify(userCouponMapper, never()).releaseByOrder(any(Long.class), any(Long.class),
                any(LocalDateTime.class));
        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    void timeoutCancellationReusesCouponRelease() {
        Orders pendingOrder = Orders.builder()
                .id(88L)
                .userId(7L)
                .status(Orders.PENDING_PAYMENT)
                .userCouponId(101L)
                .build();
        when(orderMapper.getById(88L)).thenReturn(pendingOrder);
        when(orderMapper.cancelIfPending(eq(88L), any(LocalDateTime.class),
                eq(MessageConstant.ORDER_TIME_OUT))).thenReturn(1);
        when(userCouponMapper.releaseByOrder(eq(101L), eq(88L),
                any(LocalDateTime.class))).thenReturn(1);

        service.cancelTimeoutOrder(88L);

        ArgumentCaptor<LocalDateTime> cancelTime = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderMapper).cancelIfPending(eq(88L), cancelTime.capture(),
                eq(MessageConstant.ORDER_TIME_OUT));
        verify(userCouponMapper).releaseByOrder(eq(101L), eq(88L),
                eq(cancelTime.getValue()));
        verify(orderMapper, never()).update(any(Orders.class));
    }

    @Test
    void timeoutCancellationSkipsOrderPaidAfterTaskQuery() {
        Orders stalePendingOrder = Orders.builder()
                .id(88L)
                .userId(7L)
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .userCouponId(101L)
                .build();
        when(orderMapper.getById(88L)).thenReturn(stalePendingOrder);
        // The payment commits after this stale read, so the conditional update
        // matches no row even though the Java object still says PENDING_PAYMENT.
        when(orderMapper.cancelIfPending(eq(88L), any(LocalDateTime.class),
                eq(MessageConstant.ORDER_TIME_OUT))).thenReturn(0);

        service.cancelTimeoutOrder(88L);

        verify(orderMapper).cancelIfPending(eq(88L), any(LocalDateTime.class),
                eq(MessageConstant.ORDER_TIME_OUT));
        verify(orderMapper, never()).update(any(Orders.class));
        verify(userCouponMapper, never()).releaseByOrder(any(Long.class), any(Long.class),
                any(LocalDateTime.class));
    }
}
