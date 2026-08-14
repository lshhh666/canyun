package com.sky.controller.user;

import com.sky.service.ShopService;
import com.sky.vo.ShopInfoVO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShopControllerTest {
    @Test
    void infoReturnsConfiguredPublicFieldsAndStatus() throws Exception {
        ShopService service = Mockito.mock(ShopService.class);
        when(service.getShopInfo()).thenReturn(ShopInfoVO.builder().shopId(1L).shopName("CloudMeal")
                .shopAddress("Road 1").phone("10086").deliveryFee(new BigDecimal("6.00")).status(1).build());
        ShopController controller = new ShopController();
        ReflectionTestUtils.setField(controller, "shopService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/user/shop/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopId").value(1))
                .andExpect(jsonPath("$.data.shopName").value("CloudMeal"))
                .andExpect(jsonPath("$.data.deliveryFee").value(6.0))
                .andExpect(jsonPath("$.data.status").value(1));
        verify(service).getShopInfo();
    }
}
