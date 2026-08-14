package com.sky.service;

import com.alibaba.fastjson.JSONObject;
import com.sky.entity.AddressBook;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.properties.BaiduMapProperties;
import com.sky.properties.ShopProperties;
import com.sky.service.impl.OrderPricingServiceImpl;
import com.sky.vo.OrderPreviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class OrderPricingServiceImplTest {
    private ShoppingCartMapper cartMapper;
    private AddressBookMapper addressBookMapper;
    private BaiduMapService baiduMapService;
    private OrderPricingServiceImpl service;

    @BeforeEach
    void setUp() {
        cartMapper = Mockito.mock(ShoppingCartMapper.class);
        addressBookMapper = Mockito.mock(AddressBookMapper.class);
        baiduMapService = Mockito.mock(BaiduMapService.class);
        ShopProperties shop = new ShopProperties();
        shop.setDeliveryFee(new BigDecimal("6.00"));
        shop.setPackFeePerItem(new BigDecimal("1.00"));
        shop.setEstimatedDeliveryMinutes(30);
        shop.setMaxDeliveryDistanceMeters(5000);
        BaiduMapProperties map = new BaiduMapProperties();
        map.setShopAddress("shop");

        service = new OrderPricingServiceImpl();
        ReflectionTestUtils.setField(service, "shoppingCartMapper", cartMapper);
        ReflectionTestUtils.setField(service, "addressBookMapper", addressBookMapper);
        ReflectionTestUtils.setField(service, "baiduMapService", baiduMapService);
        ReflectionTestUtils.setField(service, "baiduMapProperties", map);
        ReflectionTestUtils.setField(service, "shopProperties", shop);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(
                Instant.parse("2026-08-12T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void calculatesQuoteUsingConfiguredFees() {
        when(cartMapper.listShoppingCartByUserId(7L)).thenReturn(Arrays.asList(
                cart("20.00", 2), cart("9.00", 1)));
        when(addressBookMapper.getById(12L)).thenReturn(address(12L, 7L));
        stubRouteDistance(2400);

        OrderPreviewVO result = service.preview(7L, 12L);

        assertEquals(new BigDecimal("49.00"), result.getGoodsAmount());
        assertEquals(new BigDecimal("3.00"), result.getPackAmount());
        assertEquals(new BigDecimal("6.00"), result.getDeliveryFee());
        assertEquals(new BigDecimal("58.00"), result.getTotalAmount());
        assertEquals(LocalDateTime.of(2026, 8, 12, 12, 30), result.getEstimatedDeliveryTime());
    }

    @Test
    void rejectsEmptyCart() {
        when(cartMapper.listShoppingCartByUserId(7L)).thenReturn(Collections.emptyList());
        assertThrows(ShoppingCartBusinessException.class, () -> service.preview(7L, 12L));
    }

    @Test
    void rejectsMissingOrForeignAddress() {
        when(cartMapper.listShoppingCartByUserId(7L)).thenReturn(Collections.singletonList(cart("1.00", 1)));
        when(addressBookMapper.getById(12L)).thenReturn(null);
        assertThrows(AddressBookBusinessException.class, () -> service.preview(7L, 12L));

        when(addressBookMapper.getById(12L)).thenReturn(address(12L, 8L));
        assertThrows(AddressBookBusinessException.class, () -> service.preview(7L, 12L));
    }

    @Test
    void rejectsDistanceOutsideConfiguredRange() {
        when(cartMapper.listShoppingCartByUserId(7L)).thenReturn(Collections.singletonList(cart("1.00", 1)));
        when(addressBookMapper.getById(12L)).thenReturn(address(12L, 7L));
        stubRouteDistance(5001);
        assertThrows(OrderBusinessException.class, () -> service.preview(7L, 12L));
    }

    @Test
    void rejectsMalformedMapResponses() {
        when(cartMapper.listShoppingCartByUserId(7L)).thenReturn(Collections.singletonList(cart("1.00", 1)));
        when(addressBookMapper.getById(12L)).thenReturn(address(12L, 7L));
        when(baiduMapService.geocoder(anyString())).thenReturn(new JSONObject());
        assertThrows(OrderBusinessException.class, () -> service.preview(7L, 12L));
    }

    private void stubRouteDistance(int distance) {
        when(baiduMapService.geocoder(anyString())).thenReturn(
                JSONObject.parseObject("{\"result\":{\"location\":{\"lat\":39.9,\"lng\":116.4}}}"));
        when(baiduMapService.direction(anyString(), anyString())).thenReturn(
                JSONObject.parseObject("{\"result\":{\"routes\":[{\"distance\":" + distance + "}]}}"));
    }

    private ShoppingCart cart(String amount, int number) {
        return ShoppingCart.builder().amount(new BigDecimal(amount)).number(number).build();
    }

    private AddressBook address(Long id, Long userId) {
        return AddressBook.builder().id(id).userId(userId).provinceName("A").cityName("B")
                .districtName("C").detail("D").build();
    }
}
