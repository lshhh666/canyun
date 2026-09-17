package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.ShoppingCart;
import com.sky.entity.UserCoupon;
import com.sky.enums.AiCouponNextAction;
import com.sky.enums.UserCouponStatus;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserCouponMapper;
import com.sky.service.AiCouponEligibilityService;
import com.sky.service.model.AiCouponEligibilityItem;
import com.sky.service.model.AiCouponEligibilityResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 后端直接执行优惠券资格判断，模型只负责解释已经确定的结果。
 */
@Service
@RequiredArgsConstructor
public class AiCouponEligibilityServiceImpl implements AiCouponEligibilityService {

    private static final String CART_EMPTY_REASON = "购物车暂无商品";
    private static final String INVALID_COUPON_REASON = "优惠券信息异常";
    private static final String THRESHOLD_NOT_MET_REASON = "菜品金额未达到使用门槛";
    private static final DateTimeFormatter TOOL_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ShoppingCartMapper shoppingCartMapper;
    private final UserCouponMapper userCouponMapper;

    @Override
    public AiCouponEligibilityResult checkMyCouponEligibility() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }

        List<ShoppingCart> cart = shoppingCartMapper.listShoppingCartByUserId(userId);
        boolean cartEmpty = cart == null || cart.isEmpty();
        BigDecimal goodsAmount = calculateGoodsAmount(cart);

        List<UserCoupon> userCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByDesc(UserCoupon::getReceiveTime));
        if (userCoupons == null) {
            userCoupons = Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        List<AiCouponEligibilityItem> items = userCoupons.stream()
                .map(coupon -> evaluate(coupon, goodsAmount, cartEmpty, now))
                .collect(Collectors.toList());
        CouponActionSummary actionSummary = summarizeAction(items, cartEmpty);

        return AiCouponEligibilityResult.builder()
                .goodsAmount(goodsAmount)
                .cartEmpty(cartEmpty)
                .nextAction(actionSummary.nextAction)
                .amountNeeded(actionSummary.amountNeeded)
                .coupons(items)
                .build();
    }

    private CouponActionSummary summarizeAction(List<AiCouponEligibilityItem> items,
                                                boolean cartEmpty) {
        if (items.stream().anyMatch(item -> Boolean.TRUE.equals(item.getEligible()))) {
            return action(AiCouponNextAction.USE_COUPON, BigDecimal.ZERO);
        }

        List<AiCouponEligibilityItem> thresholdCandidates = items.stream()
                .filter(item -> CART_EMPTY_REASON.equals(item.getReason())
                        || THRESHOLD_NOT_MET_REASON.equals(item.getReason()))
                .collect(Collectors.toList());
        if (!thresholdCandidates.isEmpty()) {
            if (cartEmpty) {
                return action(AiCouponNextAction.ADD_ITEMS, BigDecimal.ZERO);
            }
            BigDecimal minimumNeeded = thresholdCandidates.stream()
                    .map(AiCouponEligibilityItem::getAmountNeeded)
                    .filter(amount -> amount != null && amount.signum() > 0)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            return action(AiCouponNextAction.ADD_MORE, minimumNeeded);
        }

        if (items.stream().anyMatch(item -> "优惠券尚未生效".equals(item.getReason()))) {
            return action(AiCouponNextAction.WAIT_FOR_VALIDITY, BigDecimal.ZERO);
        }
        if (items.stream().anyMatch(item -> "优惠券已被订单锁定".equals(item.getReason()))) {
            return action(AiCouponNextAction.CHECK_ORDER, BigDecimal.ZERO);
        }
        return action(AiCouponNextAction.RECEIVE_NEW_COUPON, BigDecimal.ZERO);
    }

    private CouponActionSummary action(AiCouponNextAction nextAction,
                                       BigDecimal amountNeeded) {
        return new CouponActionSummary(nextAction, amountNeeded);
    }

    private static class CouponActionSummary {
        private final AiCouponNextAction nextAction;
        private final BigDecimal amountNeeded;

        private CouponActionSummary(AiCouponNextAction nextAction, BigDecimal amountNeeded) {
            this.nextAction = nextAction;
            this.amountNeeded = amountNeeded;
        }
    }

    private BigDecimal calculateGoodsAmount(List<ShoppingCart> cart) {
        if (cart == null || cart.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal goodsAmount = BigDecimal.ZERO;
        for (ShoppingCart item : cart) {
            if (item == null || item.getAmount() == null || item.getNumber() == null
                    || item.getNumber() < 0) {
                throw new OrderBusinessException("购物车金额异常");
            }
            goodsAmount = goodsAmount.add(
                    item.getAmount().multiply(BigDecimal.valueOf(item.getNumber())));
        }
        return goodsAmount;
    }

    private AiCouponEligibilityItem evaluate(UserCoupon coupon,
                                             BigDecimal goodsAmount,
                                             boolean cartEmpty,
                                             LocalDateTime now) {
        if (!hasValidSnapshot(coupon)) {
            return result(coupon, goodsAmount, false, INVALID_COUPON_REASON);
        }
        if (coupon.getStatus() == UserCouponStatus.LOCKED) {
            return result(coupon, goodsAmount, false, "优惠券已被订单锁定");
        }
        if (coupon.getStatus() == UserCouponStatus.USED) {
            return result(coupon, goodsAmount, false, "优惠券已使用");
        }
        if (coupon.getStatus() == UserCouponStatus.EXPIRED
                || !coupon.getValidEndTime().isAfter(now)) {
            return result(coupon, goodsAmount, false, "优惠券已过期");
        }
        if (coupon.getValidStartTime().isAfter(now)) {
            return result(coupon, goodsAmount, false, "优惠券尚未生效");
        }
        if (cartEmpty) {
            return result(coupon, goodsAmount, false, CART_EMPTY_REASON);
        }
        if (goodsAmount.compareTo(coupon.getThresholdAmount()) < 0) {
            return result(coupon, goodsAmount, false, THRESHOLD_NOT_MET_REASON);
        }
        return result(coupon, goodsAmount, true, "当前可使用");
    }

    private boolean hasValidSnapshot(UserCoupon coupon) {
        return coupon != null
                && coupon.getCouponName() != null
                && coupon.getStatus() != null
                && coupon.getThresholdAmount() != null
                && coupon.getThresholdAmount().signum() >= 0
                && coupon.getDiscountAmount() != null
                && coupon.getDiscountAmount().signum() > 0
                && coupon.getValidStartTime() != null
                && coupon.getValidEndTime() != null;
    }

    private AiCouponEligibilityItem result(UserCoupon coupon,
                                           BigDecimal goodsAmount,
                                           boolean eligible,
                                           String reason) {
        BigDecimal amountNeeded = BigDecimal.ZERO;
        if (THRESHOLD_NOT_MET_REASON.equals(reason)
                && coupon != null && coupon.getThresholdAmount() != null
                && goodsAmount.compareTo(coupon.getThresholdAmount()) < 0) {
            amountNeeded = coupon.getThresholdAmount().subtract(goodsAmount);
        }
        return AiCouponEligibilityItem.builder()
                .couponName(coupon == null ? null : coupon.getCouponName())
                .thresholdAmount(coupon == null ? null : coupon.getThresholdAmount())
                .discountAmount(coupon == null ? null : coupon.getDiscountAmount())
                .validEndTime(coupon == null || coupon.getValidEndTime() == null
                        ? null : coupon.getValidEndTime().format(TOOL_TIME_FORMATTER))
                .eligible(eligible)
                .amountNeeded(amountNeeded)
                .reason(reason)
                .build();
    }
}
