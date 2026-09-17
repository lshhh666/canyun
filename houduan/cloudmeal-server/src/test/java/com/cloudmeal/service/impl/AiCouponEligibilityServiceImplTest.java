package com.cloudmeal.service.impl;

import com.cloudmeal.context.BaseContext;
import com.cloudmeal.entity.ShoppingCart;
import com.cloudmeal.entity.UserCoupon;
import com.cloudmeal.enums.AiCouponNextAction;
import com.cloudmeal.enums.UserCouponStatus;
import com.cloudmeal.exception.OrderBusinessException;
import com.cloudmeal.exception.UserNotLoginException;
import com.cloudmeal.mapper.ShoppingCartMapper;
import com.cloudmeal.mapper.UserCouponMapper;
import com.cloudmeal.service.model.AiCouponEligibilityItem;
import com.cloudmeal.service.model.AiCouponEligibilityResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCouponEligibilityServiceImplTest {

    private static final Long USER_ID = 18L;

    @Mock
    private ShoppingCartMapper shoppingCartMapper;

    @Mock
    private UserCouponMapper userCouponMapper;

    private AiCouponEligibilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiCouponEligibilityServiceImpl(shoppingCartMapper, userCouponMapper);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldRejectRequestWithoutAuthenticatedUser() {
        assertThatThrownBy(() -> service.checkMyCouponEligibility())
                .isInstanceOf(UserNotLoginException.class);

        verifyNoInteractions(shoppingCartMapper, userCouponMapper);
    }

    @Test
    void shouldCalculateGoodsAmountAndCouponEligibilityOnServer() {
        BaseContext.setCurrentId(USER_ID);
        when(shoppingCartMapper.listShoppingCartByUserId(USER_ID)).thenReturn(Arrays.asList(
                cart("30.00", 2),
                cart("31.00", 1)));
        when(userCouponMapper.selectList(any())).thenReturn(Arrays.asList(
                coupon("满100减10", "100.00", "10.00", UserCouponStatus.AVAILABLE,
                        LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)),
                coupon("满50减5", "50.00", "5.00", UserCouponStatus.AVAILABLE,
                        LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)),
                coupon("已锁定券", "20.00", "3.00", UserCouponStatus.LOCKED,
                        LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))));

        AiCouponEligibilityResult result = service.checkMyCouponEligibility();

        assertThat(result.getGoodsAmount()).isEqualByComparingTo("91.00");
        assertThat(result.getCartEmpty()).isFalse();
        assertThat(result.getNextAction()).isEqualTo(AiCouponNextAction.USE_COUPON);
        assertThat(result.getAmountNeeded()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCoupons()).hasSize(3);

        AiCouponEligibilityItem thresholdNotMet = result.getCoupons().get(0);
        assertThat(thresholdNotMet.getEligible()).isFalse();
        assertThat(thresholdNotMet.getAmountNeeded()).isEqualByComparingTo("9.00");
        assertThat(thresholdNotMet.getReason()).isEqualTo("菜品金额未达到使用门槛");

        AiCouponEligibilityItem available = result.getCoupons().get(1);
        assertThat(available.getEligible()).isTrue();
        assertThat(available.getAmountNeeded()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(available.getReason()).isEqualTo("当前可使用");

        AiCouponEligibilityItem locked = result.getCoupons().get(2);
        assertThat(locked.getEligible()).isFalse();
        assertThat(locked.getAmountNeeded()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(locked.getReason()).isEqualTo("优惠券已被订单锁定");

        verify(shoppingCartMapper).listShoppingCartByUserId(USER_ID);
        verify(userCouponMapper).selectList(any());
    }

    @Test
    void shouldExplainEmptyCartAndExpiredCouponWithoutCallingModel() {
        BaseContext.setCurrentId(USER_ID);
        when(shoppingCartMapper.listShoppingCartByUserId(USER_ID))
                .thenReturn(Collections.emptyList());
        when(userCouponMapper.selectList(any())).thenReturn(Arrays.asList(
                coupon("可用券", "20.00", "3.00", UserCouponStatus.AVAILABLE,
                        LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)),
                coupon("过期券", "20.00", "3.00", UserCouponStatus.AVAILABLE,
                        LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1))));

        AiCouponEligibilityResult result = service.checkMyCouponEligibility();

        assertThat(result.getGoodsAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCartEmpty()).isTrue();
        assertThat(result.getNextAction()).isEqualTo(AiCouponNextAction.ADD_ITEMS);
        assertThat(result.getAmountNeeded()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getCoupons().get(0).getReason()).isEqualTo("购物车暂无商品");
        assertThat(result.getCoupons().get(1).getReason()).isEqualTo("优惠券已过期");
    }

    @Test
    void shouldRecommendReceivingNewCouponWhenCartIsEmptyButAllCouponsAreUnusable() {
        BaseContext.setCurrentId(USER_ID);
        when(shoppingCartMapper.listShoppingCartByUserId(USER_ID))
                .thenReturn(Collections.emptyList());
        when(userCouponMapper.selectList(any())).thenReturn(Arrays.asList(
                coupon("已使用券", "50.00", "8.00", UserCouponStatus.USED,
                        LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(1)),
                coupon("已过期券", "10.00", "3.00", UserCouponStatus.EXPIRED,
                        LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1))));

        AiCouponEligibilityResult result = service.checkMyCouponEligibility();

        assertThat(result.getCartEmpty()).isTrue();
        assertThat(result.getNextAction())
                .isEqualTo(AiCouponNextAction.RECEIVE_NEW_COUPON);
        assertThat(result.getAmountNeeded()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldReturnMinimumAmountNeededWhenValidCouponsNeedMoreGoods() {
        BaseContext.setCurrentId(USER_ID);
        when(shoppingCartMapper.listShoppingCartByUserId(USER_ID))
                .thenReturn(Collections.singletonList(cart("30.00", 1)));
        when(userCouponMapper.selectList(any())).thenReturn(Arrays.asList(
                coupon("满100减10", "100.00", "10.00", UserCouponStatus.AVAILABLE,
                        LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)),
                coupon("满50减8", "50.00", "8.00", UserCouponStatus.AVAILABLE,
                        LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))));

        AiCouponEligibilityResult result = service.checkMyCouponEligibility();

        assertThat(result.getNextAction()).isEqualTo(AiCouponNextAction.ADD_MORE);
        assertThat(result.getAmountNeeded()).isEqualByComparingTo("20.00");
    }

    @Test
    void shouldRejectInvalidCartSnapshot() {
        BaseContext.setCurrentId(USER_ID);
        when(shoppingCartMapper.listShoppingCartByUserId(USER_ID))
                .thenReturn(Collections.singletonList(cart("10.00", -1)));

        assertThatThrownBy(() -> service.checkMyCouponEligibility())
                .isInstanceOf(OrderBusinessException.class)
                .hasMessage("购物车金额异常");

        verify(userCouponMapper, never()).selectList(any());
    }

    private ShoppingCart cart(String amount, int number) {
        return ShoppingCart.builder()
                .amount(new BigDecimal(amount))
                .number(number)
                .build();
    }

    private UserCoupon coupon(String name,
                              String threshold,
                              String discount,
                              UserCouponStatus status,
                              LocalDateTime validStart,
                              LocalDateTime validEnd) {
        return new UserCoupon()
                .setCouponName(name)
                .setThresholdAmount(new BigDecimal(threshold))
                .setDiscountAmount(new BigDecimal(discount))
                .setStatus(status)
                .setValidStartTime(validStart)
                .setValidEndTime(validEnd);
    }
}
