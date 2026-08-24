package com.sky.controller.admin;

import com.sky.dto.CouponDTO;
import com.sky.json.JacksonObjectMapper;
import com.sky.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端页面与后端之间的 JSON 时间格式契约测试。
 */
class CouponControllerTest {

    private CouponService couponService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        couponService = mock(CouponService.class);
        CouponController controller = new CouponController();
        ReflectionTestUtils.setField(controller, "couponService", couponService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonObjectMapper()))
                .build();
    }

    @Test
    void createShouldAcceptFrontendMinutePrecisionDateTimes() throws Exception {
        String payload = "{"
                + "\"name\":\"管理端接口测试券\","
                + "\"thresholdAmount\":\"30.00\","
                + "\"discountAmount\":\"5.00\","
                + "\"totalStock\":100,"
                + "\"receiveStartTime\":\"2026-08-24 10:00\","
                + "\"receiveEndTime\":\"2026-08-25 10:00\","
                + "\"validStartTime\":\"2026-08-24 10:00\","
                + "\"validEndTime\":\"2026-08-31 10:00\"}";

        mockMvc.perform(post("/admin/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        ArgumentCaptor<CouponDTO> captor = ArgumentCaptor.forClass(CouponDTO.class);
        verify(couponService).createCoupon(captor.capture());
        assertEquals(LocalDateTime.of(2026, 8, 24, 10, 0),
                captor.getValue().getReceiveStartTime());
    }
}
