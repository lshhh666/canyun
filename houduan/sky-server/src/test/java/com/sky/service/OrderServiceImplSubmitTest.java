package com.sky.service;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderdetailMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.impl.OrderServiceImpl;
import com.sky.vo.OrderPreviewVO;
import com.sky.vo.OrderSubmitVO;
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
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(7L);
        orderMapper = Mockito.mock(OrderMapper.class);
        detailMapper = Mockito.mock(OrderdetailMapper.class);
        cartMapper = Mockito.mock(ShoppingCartMapper.class);
        addressMapper = Mockito.mock(AddressBookMapper.class);
        pricingService = Mockito.mock(OrderPricingService.class);
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderdetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "shoppingCartMapper", cartMapper);
        ReflectionTestUtils.setField(service, "addressBookMapper", addressMapper);
        ReflectionTestUtils.setField(service, "orderPricingService", pricingService);
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
    void pricingFailureDoesNotWriteOrClearAnything() {
        when(pricingService.preview(7L, 12L)).thenThrow(new OrderBusinessException("quote failed"));
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(12L);

        assertThrows(OrderBusinessException.class, () -> service.orderSubmit(dto));

        verify(orderMapper, never()).add(any(Orders.class));
        verify(detailMapper, never()).add(any(OrderDetail.class));
        verify(cartMapper, never()).deleteShoppingCart(any(Long.class));
    }
}
