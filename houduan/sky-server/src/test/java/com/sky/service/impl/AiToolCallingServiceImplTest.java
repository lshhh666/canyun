package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.enums.AiCouponNextAction;
import com.sky.enums.AiOrderDetailOutcome;
import com.sky.enums.AiOrderQueryOutcome;
import com.sky.enums.AiOrderStatus;
import com.sky.exception.AiServiceException;
import com.sky.exception.OrderBusinessException;
import com.sky.service.AiCouponEligibilityService;
import com.sky.service.AiOrderDetailService;
import com.sky.service.AiOrderStatusService;
import com.sky.service.model.AiCouponEligibilityItem;
import com.sky.service.model.AiCouponEligibilityResult;
import com.sky.service.model.AiOrderDetailItem;
import com.sky.service.model.AiOrderDetailResult;
import com.sky.service.model.AiOrderStatusItem;
import com.sky.service.model.AiOrderStatusResult;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiToolCallingServiceImplTest {

    @Mock
    private AiCouponEligibilityService couponEligibilityService;

    @Mock
    private AiOrderStatusService orderStatusService;

    @Mock
    private AiOrderDetailService orderDetailService;

    private AiToolCallingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiToolCallingServiceImpl(
                couponEligibilityService, orderStatusService, orderDetailService);
    }

    @Test
    void shouldReturnDeterministicBackendAnswerWithoutSecondModelDecision() {
        when(couponEligibilityService.checkMyCouponEligibility())
                .thenReturn(eligibilityResult());

        assertThat(service.answerCouponEligibility()).isEqualTo(
                "当前菜品金额为91元，使用满100减10还差9元，请继续添加商品。");
    }

    @Test
    void shouldHideBusinessToolFailure() {
        OrderBusinessException databaseFailure =
                new OrderBusinessException("user_coupon表查询失败: select * from user_coupon");
        when(couponEligibilityService.checkMyCouponEligibility())
                .thenThrow(databaseFailure);

        assertThatThrownBy(() -> service.answerCouponEligibility())
                .isInstanceOf(AiServiceException.class)
                .hasMessage(MessageConstant.AI_SERVICE_UNAVAILABLE)
                .hasMessageNotContaining("user_coupon")
                .hasMessageNotContaining("select");
    }

    @Test
    void shouldRecommendNewCouponInsteadOfAddingItemsWhenCartIsEmptyAndCouponsAreUsedOrExpired() {
        when(couponEligibilityService.checkMyCouponEligibility())
                .thenReturn(unusableCouponsResult());

        String answer = service.answerCouponEligibility();

        assertThat(answer)
                .contains("请前往领券中心领取新券")
                .doesNotContain("添加商品");
    }

    @Test
    void shouldReturnSelectedOrderStatusAndEstimatedDeliveryTime() {
        AiOrderStatusItem selected = orderItem(
                "4321", AiOrderStatus.DELIVERY_IN_PROGRESS,
                LocalDateTime.of(2026, 9, 8, 11, 0),
                LocalDateTime.of(2026, 9, 8, 12, 30));
        when(orderStatusService.queryMyActiveOrder("4321"))
                .thenReturn(AiOrderStatusResult.builder()
                        .outcome(AiOrderQueryOutcome.FOUND)
                        .selectedOrder(selected)
                        .candidates(Collections.singletonList(selected))
                        .build());

        String answer = service.answerOrderStatus("订单尾号4321");

        assertThat(answer).isEqualTo(
                "订单尾号4321当前状态：配送中。预计09-08 12:30送达。");
        verify(orderStatusService).queryMyActiveOrder("4321");
    }

    @Test
    void shouldAskUserToChooseWhenThereAreMultipleActiveOrders() {
        AiOrderStatusItem first = orderItem(
                "4321", AiOrderStatus.PENDING_PAYMENT,
                LocalDateTime.of(2026, 9, 8, 12, 5), null);
        AiOrderStatusItem second = orderItem(
                "8765", AiOrderStatus.CONFIRMED,
                LocalDateTime.of(2026, 9, 8, 12, 0), null);
        when(orderStatusService.queryMyActiveOrder(null))
                .thenReturn(AiOrderStatusResult.builder()
                        .outcome(AiOrderQueryOutcome.SELECTION_REQUIRED)
                        .candidates(Arrays.asList(first, second))
                        .build());

        String answer = service.answerOrderStatus("我的订单到哪了");

        assertThat(answer)
                .contains("尾号4321（待付款，09-08 12:05）")
                .contains("尾号8765（已接单，09-08 12:00）")
                .endsWith("请回复上面显示的订单尾号。");
        verify(orderStatusService).queryMyActiveOrder(null);
    }

    @Test
    void shouldExplainThatOnlyThreeRecentOrdersAreSelectableWhenThereAreMore() {
        AiOrderStatusItem first = orderItem(
                "1111", AiOrderStatus.PENDING_PAYMENT,
                LocalDateTime.of(2026, 9, 8, 12, 5), null);
        AiOrderStatusItem second = orderItem(
                "2222", AiOrderStatus.CONFIRMED,
                LocalDateTime.of(2026, 9, 8, 12, 0), null);
        AiOrderStatusItem third = orderItem(
                "3333", AiOrderStatus.DELIVERY_IN_PROGRESS,
                LocalDateTime.of(2026, 9, 8, 11, 55), null);
        when(orderStatusService.queryMyActiveOrder(null))
                .thenReturn(AiOrderStatusResult.builder()
                        .outcome(AiOrderQueryOutcome.SELECTION_REQUIRED)
                        .candidates(Arrays.asList(first, second, third))
                        .hasMore(true)
                        .build());

        String answer = service.answerOrderStatus("我的订单到哪了");

        assertThat(answer)
                .contains("仅展示最近3笔")
                .contains("其他订单请前往订单页查看")
                .endsWith("请回复上面显示的订单尾号。");
    }

    @Test
    void shouldReturnNoActiveOrderWithoutCallingAnswerModel() {
        when(orderStatusService.queryMyActiveOrder(null))
                .thenReturn(AiOrderStatusResult.builder()
                        .outcome(AiOrderQueryOutcome.NO_ACTIVE_ORDER)
                        .candidates(Collections.emptyList())
                        .build());

        assertThat(service.answerOrderStatus("查一下订单"))
                .isEqualTo("您当前没有进行中的订单。");
    }

    @Test
    void shouldReturnBusinessMessageAndChoicesWhenOrderSuffixDoesNotExist() {
        AiOrderStatusItem first = orderItem(
                "2038", AiOrderStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 23, 16, 17), null);
        AiOrderStatusItem second = orderItem(
                "6942", AiOrderStatus.CONFIRMED,
                LocalDateTime.of(2026, 8, 14, 19, 50), null);
        when(orderStatusService.queryMyActiveOrder("9999"))
                .thenReturn(AiOrderStatusResult.builder()
                        .outcome(AiOrderQueryOutcome.ORDER_NOT_FOUND)
                        .candidates(Arrays.asList(first, second))
                        .build());

        String answer = service.answerOrderStatus("9999");

        assertThat(answer)
                .startsWith("没有找到该尾号对应的进行中订单。")
                .contains("尾号2038")
                .contains("尾号6942")
                .endsWith("请回复上面显示的订单尾号。");
    }

    @Test
    void shouldReturnConciseSelectedOrderItemsAndPriceBreakdown() {
        when(orderDetailService.querySelectedOrderDetail(25L))
                .thenReturn(orderDetailResult());

        String answer = service.answerSelectedOrderDetail(
                25L, "这单买了什么，一共多少钱");

        assertThat(answer)
                .contains("订单尾号2038包含：米饭×2、黄焖鸡（微辣）×1")
                .contains("最终金额78元")
                .contains("商品75元、打包3元、配送6元、优惠6元")
                .doesNotContain("手机号", "地址");
    }

    @Test
    void shouldAnswerOnlyRequestedFeeInsteadOfRepeatingWholeOrder() {
        when(orderDetailService.querySelectedOrderDetail(25L))
                .thenReturn(orderDetailResult());

        assertThat(service.answerSelectedOrderDetail(25L, "这单配送费多少钱"))
                .isEqualTo("订单尾号2038的配送费为6元。");
    }

    @Test
    void shouldAskUserToSelectOrderWhenSessionHasNoSelection() {
        when(orderDetailService.querySelectedOrderDetail(25L))
                .thenReturn(AiOrderDetailResult.builder()
                        .outcome(AiOrderDetailOutcome.NO_SELECTED_ORDER)
                        .items(Collections.emptyList())
                        .build());

        assertThat(service.answerSelectedOrderDetail(25L, "这单多少钱"))
                .isEqualTo("请先查询进行中的订单并选择订单尾号。");
    }

    private AiCouponEligibilityResult eligibilityResult() {
        AiCouponEligibilityItem item = AiCouponEligibilityItem.builder()
                .couponName("满100减10")
                .thresholdAmount(new BigDecimal("100.00"))
                .discountAmount(new BigDecimal("10.00"))
                .validEndTime("2026-09-09 21:00:00")
                .eligible(false)
                .amountNeeded(new BigDecimal("9.00"))
                .reason("菜品金额未达到使用门槛")
                .build();
        return AiCouponEligibilityResult.builder()
                .goodsAmount(new BigDecimal("91.00"))
                .cartEmpty(false)
                .nextAction(AiCouponNextAction.ADD_MORE)
                .amountNeeded(new BigDecimal("9.00"))
                .coupons(Collections.singletonList(item))
                .build();
    }

    private AiOrderDetailResult orderDetailResult() {
        return AiOrderDetailResult.builder()
                .outcome(AiOrderDetailOutcome.FOUND)
                .orderNumberSuffix("2038")
                .items(Arrays.asList(
                        AiOrderDetailItem.builder().name("米饭").quantity(2)
                                .unitAmount(new BigDecimal("3.00")).build(),
                        AiOrderDetailItem.builder().name("黄焖鸡").flavor("微辣").quantity(1)
                                .unitAmount(new BigDecimal("69.00")).build()))
                .goodsAmount(new BigDecimal("75.00"))
                .packAmount(new BigDecimal("3.00"))
                .deliveryFee(new BigDecimal("6.00"))
                .originalAmount(new BigDecimal("84.00"))
                .discountAmount(new BigDecimal("6.00"))
                .finalAmount(new BigDecimal("78.00"))
                .remark("不要辣")
                .build();
    }

    private AiCouponEligibilityResult unusableCouponsResult() {
        AiCouponEligibilityItem used = AiCouponEligibilityItem.builder()
                .couponName("满50减8")
                .eligible(false)
                .amountNeeded(BigDecimal.ZERO)
                .reason("优惠券已使用")
                .build();
        AiCouponEligibilityItem expired = AiCouponEligibilityItem.builder()
                .couponName("满10减3")
                .eligible(false)
                .amountNeeded(BigDecimal.ZERO)
                .reason("优惠券已过期")
                .build();
        return AiCouponEligibilityResult.builder()
                .goodsAmount(BigDecimal.ZERO)
                .cartEmpty(true)
                .nextAction(AiCouponNextAction.RECEIVE_NEW_COUPON)
                .amountNeeded(BigDecimal.ZERO)
                .coupons(Arrays.asList(expired, used))
                .build();
    }

    private AiOrderStatusItem orderItem(String suffix,
                                        AiOrderStatus status,
                                        LocalDateTime orderTime,
                                        LocalDateTime estimatedDeliveryTime) {
        return AiOrderStatusItem.builder()
                .orderId(Long.valueOf(suffix))
                .orderNumberSuffix(suffix)
                .status(status)
                .orderTime(orderTime)
                .estimatedDeliveryTime(estimatedDeliveryTime)
                .build();
    }
}
