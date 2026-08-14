package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.OrderPreviewDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.json.JacksonObjectMapper;
import com.sky.service.OrderService;
import com.sky.vo.OrderPreviewVO;
import com.sky.vo.OrderSubmitVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderPreviewControllerTest {
    @AfterEach
    void clearContext() { BaseContext.removeCurrentId(); }

    @Test
    void previewReturnsAuthoritativeQuote() throws Exception {
        OrderService service = Mockito.mock(OrderService.class);
        when(service.preview(any(OrderPreviewDTO.class))).thenReturn(OrderPreviewVO.builder()
                .goodsAmount(new BigDecimal("49.00")).packAmount(new BigDecimal("3.00"))
                .deliveryFee(new BigDecimal("6.00")).totalAmount(new BigDecimal("58.00"))
                .estimatedDeliveryTime(LocalDateTime.of(2026, 8, 12, 12, 30)).build());
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/user/order/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressBookId\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goodsAmount").value(49.0))
                .andExpect(jsonPath("$.data.totalAmount").value(58.0))
                .andExpect(jsonPath("$.data.estimatedDeliveryTime").exists());
        verify(service).preview(any(OrderPreviewDTO.class));
    }

    @Test
    void submitAcceptsLegacyClientEtaWithoutDateParsing() throws Exception {
        OrderService service = Mockito.mock(OrderService.class);
        when(service.orderSubmit(any(OrdersSubmitDTO.class))).thenReturn(OrderSubmitVO.builder()
                .id(99L).orderAmount(new BigDecimal("58.00")).build());
        OrderController controller = new OrderController();
        ReflectionTestUtils.setField(controller, "orderService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonObjectMapper()))
                .build();

        mvc.perform(post("/user/order/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressBookId\":12,\"payMethod\":1,\"estimatedDeliveryTime\":\"2026-08-14 19:51\",\"deliveryStatus\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(99));
        org.mockito.ArgumentCaptor<OrdersSubmitDTO> captor = org.mockito.ArgumentCaptor.forClass(OrdersSubmitDTO.class);
        verify(service).orderSubmit(captor.capture());
        assertEquals("2026-08-14 19:51", String.valueOf(captor.getValue().getEstimatedDeliveryTime()));
    }
}
