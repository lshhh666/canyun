package com.cloudmeal.service.impl;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.enums.AiCouponNextAction;
import com.cloudmeal.enums.AiOrderDetailOutcome;
import com.cloudmeal.enums.AiOrderQueryOutcome;
import com.cloudmeal.enums.AiOrderStatus;
import com.cloudmeal.exception.AiServiceException;
import com.cloudmeal.service.AiCouponEligibilityService;
import com.cloudmeal.service.AiOrderDetailService;
import com.cloudmeal.service.AiOrderStatusService;
import com.cloudmeal.service.AiToolCallingService;
import com.cloudmeal.service.model.AiCouponEligibilityItem;
import com.cloudmeal.service.model.AiCouponEligibilityResult;
import com.cloudmeal.service.model.AiOrderDetailItem;
import com.cloudmeal.service.model.AiOrderDetailResult;
import com.cloudmeal.service.model.AiOrderStatusItem;
import com.cloudmeal.service.model.AiOrderStatusResult;
import com.cloudmeal.service.model.AiToolAnswer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 餐云AI客服受控工具执行器。证据层完成路由后，Java负责查询、计算和生成答案。
 */
@Slf4j
@Service
public class AiToolCallingServiceImpl implements AiToolCallingService {

    private static final Pattern LABELED_ORDER_REFERENCE = Pattern.compile(
            "(?:订单号|尾号)\\s*[:：#]?\\s*(\\d{4,})");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d{4,}$");
    private static final DateTimeFormatter ORDER_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter DELIVERY_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final AiCouponEligibilityService couponEligibilityService;
    private final AiOrderStatusService orderStatusService;
    private final AiOrderDetailService orderDetailService;

    public AiToolCallingServiceImpl(AiCouponEligibilityService couponEligibilityService,
                                    AiOrderStatusService orderStatusService,
                                    AiOrderDetailService orderDetailService) {
        this.couponEligibilityService = couponEligibilityService;
        this.orderStatusService = orderStatusService;
        this.orderDetailService = orderDetailService;
    }

    @Override
    public String answerCouponEligibility() {
        AiCouponEligibilityResult result;
        try {
            result = couponEligibilityService.checkMyCouponEligibility();
        } catch (RuntimeException ex) {
            log.warn("AI客服优惠券查询工具执行失败，异常类型={}",
                    ex.getClass().getSimpleName());
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE, ex);
        }

        // 金额、状态和下一步操作属于确定性业务结论，直接由Java生成答案，
        // 不再发起第二次模型请求，避免模型改写结论，同时降低延迟和调用成本。
        String answer = formatAnswer(result);
        if (!StringUtils.hasText(answer)) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        return answer;
    }

    @Override
    public String answerOrderStatus(String latestMessage) {
        return answerOrderStatusWithContext(latestMessage).getAnswer();
    }

    @Override
    public AiToolAnswer answerOrderStatusWithContext(String latestMessage) {
        AiOrderStatusResult result;
        try {
            result = orderStatusService.queryMyActiveOrder(
                    extractOrderNumberReference(latestMessage));
        } catch (RuntimeException ex) {
            log.warn("AI客服订单状态查询工具执行失败，异常类型={}",
                    ex.getClass().getSimpleName());
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE, ex);
        }

        String answer = formatOrderStatusAnswer(result);
        if (!StringUtils.hasText(answer)) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        Long selectedOrderId = null;
        if (result.getOutcome() == AiOrderQueryOutcome.FOUND) {
            selectedOrderId = result.getSelectedOrder().getOrderId();
            if (selectedOrderId == null || selectedOrderId <= 0) {
                throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
            }
        }
        return AiToolAnswer.builder()
                .answer(answer)
                .selectedOrderId(selectedOrderId)
                .build();
    }

    @Override
    public String answerSelectedOrderDetail(Long sessionId, String latestMessage) {
        AiOrderDetailResult result;
        try {
            result = orderDetailService.querySelectedOrderDetail(sessionId);
        } catch (RuntimeException ex) {
            log.warn("AI客服订单详情查询工具执行失败，异常类型={}",
                    ex.getClass().getSimpleName());
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE, ex);
        }
        return formatOrderDetailAnswer(result, latestMessage);
    }

    private String formatAnswer(AiCouponEligibilityResult result) {
        if (result == null || result.getNextAction() == null
                || result.getGoodsAmount() == null || result.getAmountNeeded() == null
                || result.getCoupons() == null) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }

        AiCouponNextAction action = result.getNextAction();
        if (action == AiCouponNextAction.USE_COUPON) {
            AiCouponEligibilityItem coupon = findFirstEligible(result);
            return "当前菜品金额为" + amount(result.getGoodsAmount()) + "元，"
                    + couponName(coupon) + "可以使用，下单时系统会再次校验。";
        }
        if (action == AiCouponNextAction.ADD_ITEMS) {
            AiCouponEligibilityItem coupon = findByReason(result, "购物车暂无商品");
            return couponName(coupon) + "仍在有效期内，但购物车暂无商品，请先添加商品。";
        }
        if (action == AiCouponNextAction.ADD_MORE) {
            AiCouponEligibilityItem coupon = findClosestThresholdCoupon(result);
            return "当前菜品金额为" + amount(result.getGoodsAmount()) + "元，使用"
                    + couponName(coupon) + "还差" + amount(result.getAmountNeeded())
                    + "元，请继续添加商品。";
        }
        if (action == AiCouponNextAction.WAIT_FOR_VALIDITY) {
            return couponName(findByReason(result, "优惠券尚未生效"))
                    + "尚未生效，请生效后再使用。";
        }
        if (action == AiCouponNextAction.CHECK_ORDER) {
            return couponName(findByReason(result, "优惠券已被订单锁定"))
                    + "已被订单锁定，请先查看相关订单状态。";
        }
        if (action == AiCouponNextAction.RECEIVE_NEW_COUPON) {
            return receiveNewCouponAnswer(result);
        }
        throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }

    private AiCouponEligibilityItem findFirstEligible(AiCouponEligibilityResult result) {
        return result.getCoupons().stream()
                .filter(item -> item != null && Boolean.TRUE.equals(item.getEligible()))
                .findFirst()
                .orElseThrow(() -> new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE));
    }

    private AiCouponEligibilityItem findByReason(AiCouponEligibilityResult result,
                                                 String reason) {
        return result.getCoupons().stream()
                .filter(item -> item != null && reason.equals(item.getReason()))
                .findFirst()
                .orElseThrow(() -> new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE));
    }

    private AiCouponEligibilityItem findClosestThresholdCoupon(
            AiCouponEligibilityResult result) {
        return result.getCoupons().stream()
                .filter(item -> item != null && item.getAmountNeeded() != null
                        && item.getAmountNeeded().compareTo(result.getAmountNeeded()) == 0)
                .findFirst()
                .orElseThrow(() -> new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE));
    }

    private String receiveNewCouponAnswer(AiCouponEligibilityResult result) {
        if (result.getCoupons().isEmpty()) {
            return "您当前没有优惠券，请前往领券中心领取新券。";
        }
        List<String> details = result.getCoupons().stream()
                .filter(item -> item != null && StringUtils.hasText(item.getReason()))
                .limit(3)
                .map(item -> couponName(item) + simplifyReason(item.getReason()))
                .collect(Collectors.toList());
        if (details.isEmpty()) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        String more = result.getCoupons().size() > details.size() ? "等" : "";
        return "您当前没有可用优惠券：" + String.join("；", details) + more
                + "。请前往领券中心领取新券。";
    }

    private String couponName(AiCouponEligibilityItem item) {
        if (item == null || !StringUtils.hasText(item.getCouponName())) {
            return "该优惠券";
        }
        return item.getCouponName().trim();
    }

    private String simplifyReason(String reason) {
        return reason.startsWith("优惠券") ? reason.substring("优惠券".length()) : reason;
    }

    private String amount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String extractOrderNumberReference(String latestMessage) {
        if (!StringUtils.hasText(latestMessage)) {
            return null;
        }
        String message = latestMessage.trim();
        Matcher labeledMatcher = LABELED_ORDER_REFERENCE.matcher(message);
        if (labeledMatcher.find()) {
            return labeledMatcher.group(1);
        }
        if (DIGITS_ONLY.matcher(message).matches()) {
            return message;
        }
        return null;
    }

    private String formatOrderStatusAnswer(AiOrderStatusResult result) {
        if (result == null || result.getOutcome() == null
                || result.getCandidates() == null) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (result.getOutcome() == AiOrderQueryOutcome.NO_ACTIVE_ORDER) {
            return "您当前没有进行中的订单。";
        }
        if (result.getOutcome() == AiOrderQueryOutcome.FOUND) {
            return formatSelectedOrder(result.getSelectedOrder());
        }
        if (result.getOutcome() == AiOrderQueryOutcome.SELECTION_REQUIRED) {
            return formatOrderChoices(result);
        }
        if (result.getOutcome() == AiOrderQueryOutcome.ORDER_NOT_FOUND) {
            return formatOrderNotFound(result);
        }
        throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
    }

    private String formatSelectedOrder(AiOrderStatusItem order) {
        validateOrderItem(order);
        String answer = "订单尾号" + order.getOrderNumberSuffix()
                + "当前状态：" + order.getStatus().getDesc() + "。";
        if ((order.getStatus() == AiOrderStatus.CONFIRMED
                || order.getStatus() == AiOrderStatus.DELIVERY_IN_PROGRESS)
                && order.getEstimatedDeliveryTime() != null) {
            answer += "预计" + order.getEstimatedDeliveryTime()
                    .format(DELIVERY_TIME_FORMATTER) + "送达。";
        }
        return answer;
    }

    private String formatOrderChoices(AiOrderStatusResult result) {
        List<AiOrderStatusItem> candidates = result.getCandidates();
        if (candidates.isEmpty()) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        List<String> choices = candidates.stream()
                .map(item -> {
                    validateOrderItem(item);
                    return "尾号" + item.getOrderNumberSuffix()
                            + "（" + item.getStatus().getDesc() + "，"
                            + item.getOrderTime().format(ORDER_TIME_FORMATTER) + "）";
                })
                .collect(Collectors.toList());
        String more = result.isHasMore()
                ? "（仅展示最近3笔，其他订单请前往订单页查看）"
                : "";
        return "您有多笔进行中订单：" + String.join("；", choices)
                + more + "。请回复上面显示的订单尾号。";
    }

    private String formatOrderNotFound(AiOrderStatusResult result) {
        List<AiOrderStatusItem> candidates = result.getCandidates();
        if (candidates.isEmpty()) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (candidates.size() == 1) {
            AiOrderStatusItem candidate = candidates.get(0);
            validateOrderItem(candidate);
            return "没有找到该尾号对应的进行中订单。您当前进行中的订单尾号为"
                    + candidate.getOrderNumberSuffix() + "，请重新输入。";
        }
        return "没有找到该尾号对应的进行中订单。" + formatOrderChoices(result);
    }

    private void validateOrderItem(AiOrderStatusItem item) {
        if (item == null || item.getOrderId() == null || item.getOrderId() <= 0
                || !StringUtils.hasText(item.getOrderNumberSuffix())
                || item.getStatus() == null || item.getOrderTime() == null) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
    }

    private String formatOrderDetailAnswer(AiOrderDetailResult result, String latestMessage) {
        if (result == null || result.getOutcome() == null || result.getItems() == null) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (result.getOutcome() == AiOrderDetailOutcome.NO_SELECTED_ORDER) {
            return "请先查询进行中的订单并选择订单尾号。";
        }
        if (result.getOutcome() == AiOrderDetailOutcome.ORDER_UNAVAILABLE) {
            return "当前会话没有可查询的已选订单，请重新查询进行中的订单。";
        }
        if (result.getOutcome() != AiOrderDetailOutcome.FOUND
                || !StringUtils.hasText(result.getOrderNumberSuffix())
                || result.getItems().isEmpty()
                || result.getGoodsAmount() == null || result.getPackAmount() == null
                || result.getDeliveryFee() == null || result.getDiscountAmount() == null
                || result.getFinalAmount() == null) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }

        String question = StringUtils.hasText(latestMessage) ? latestMessage.trim() : "订单详情";
        boolean asksItems = question.contains("买了什么") || question.contains("点了什么")
                || question.contains("哪些菜") || question.contains("明细")
                || question.contains("详情");
        boolean asksPackFee = question.contains("打包费");
        boolean asksDeliveryFee = question.contains("配送费");
        boolean asksDiscount = question.contains("优惠");
        boolean asksRemark = question.contains("备注");
        boolean asksTotal = question.contains("多少钱") || question.contains("金额")
                || question.contains("明细") || question.contains("详情");

        if (!asksItems && asksPackFee && !asksDeliveryFee && !asksDiscount) {
            return "订单尾号" + result.getOrderNumberSuffix() + "的打包费为"
                    + amount(result.getPackAmount()) + "元。";
        }
        if (!asksItems && asksDeliveryFee && !asksPackFee && !asksDiscount) {
            return "订单尾号" + result.getOrderNumberSuffix() + "的配送费为"
                    + amount(result.getDeliveryFee()) + "元。";
        }
        if (!asksItems && asksDiscount && !asksPackFee && !asksDeliveryFee) {
            return "订单尾号" + result.getOrderNumberSuffix() + "优惠了"
                    + amount(result.getDiscountAmount()) + "元。";
        }
        if (!asksItems && asksRemark && !asksTotal) {
            return StringUtils.hasText(result.getRemark())
                    ? "订单尾号" + result.getOrderNumberSuffix() + "的备注是：" + result.getRemark() + "。"
                    : "订单尾号" + result.getOrderNumberSuffix() + "没有填写备注。";
        }

        List<String> itemDescriptions = result.getItems().stream()
                .limit(5)
                .map(this::formatOrderDetailItem)
                .collect(Collectors.toList());
        String moreItems = result.getItems().size() > itemDescriptions.size()
                ? "等，共" + result.getItems().size() + "种商品" : "";
        String answer = "订单尾号" + result.getOrderNumberSuffix();
        if (asksItems) {
            answer += "包含：" + String.join("、", itemDescriptions) + moreItems + "。";
        }
        if (asksTotal || !asksItems) {
            answer += "最终金额" + amount(result.getFinalAmount()) + "元（商品"
                    + amount(result.getGoodsAmount()) + "元、打包"
                    + amount(result.getPackAmount()) + "元、配送"
                    + amount(result.getDeliveryFee()) + "元、优惠"
                    + amount(result.getDiscountAmount()) + "元）。";
        }
        if (asksRemark && StringUtils.hasText(result.getRemark())) {
            answer += "备注：" + result.getRemark() + "。";
        }
        return answer;
    }

    private String formatOrderDetailItem(AiOrderDetailItem item) {
        if (item == null || !StringUtils.hasText(item.getName())
                || item.getQuantity() == null || item.getQuantity() <= 0
                || item.getUnitAmount() == null || item.getUnitAmount().signum() < 0) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        String flavor = StringUtils.hasText(item.getFlavor())
                ? "（" + item.getFlavor() + "）" : "";
        return item.getName() + flavor + "×" + item.getQuantity();
    }

}
