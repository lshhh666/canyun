package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.AiChatSession;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.enums.AiOrderDetailOutcome;
import com.sky.exception.AiServiceException;
import com.sky.exception.BaseException;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.AiChatSessionMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderdetailMapper;
import com.sky.service.AiOrderDetailService;
import com.sky.service.model.AiOrderDetailItem;
import com.sky.service.model.AiOrderDetailResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 使用会话内部保存的订单主键，查询当前用户自己的订单详情。 */
@Service
@RequiredArgsConstructor
public class AiOrderDetailServiceImpl implements AiOrderDetailService {

    private final AiChatSessionMapper sessionMapper;
    private final OrderMapper orderMapper;
    private final OrderdetailMapper orderdetailMapper;

    @Override
    public AiOrderDetailResult querySelectedOrderDetail(Long sessionId) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
        if (sessionId == null || sessionId <= 0) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }

        AiChatSession session = sessionMapper.selectOwned(sessionId, userId);
        if (session == null) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }
        if (session.getSelectedOrderId() == null) {
            return outcome(AiOrderDetailOutcome.NO_SELECTED_ORDER);
        }

        Orders order = orderMapper.getAiDetailByIdAndUserId(
                session.getSelectedOrderId(), userId);
        if (order == null) {
            return outcome(AiOrderDetailOutcome.ORDER_UNAVAILABLE);
        }
        validateOrderAmounts(order);

        List<OrderDetail> details = orderdetailMapper.listAiDetailByOrderId(order.getId());
        if (details == null || details.isEmpty()) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        List<AiOrderDetailItem> items = details.stream()
                .map(this::toSafeItem)
                .collect(Collectors.toList());

        String number = order.getNumber().trim();
        String suffix = number.substring(Math.max(0, number.length() - 4));
        return AiOrderDetailResult.builder()
                .outcome(AiOrderDetailOutcome.FOUND)
                .orderNumberSuffix(suffix)
                .items(items)
                .goodsAmount(order.getGoodsAmount())
                .packAmount(BigDecimal.valueOf(order.getPackAmount()))
                .deliveryFee(order.getDeliveryFee())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .finalAmount(order.getAmount())
                .remark(StringUtils.hasText(order.getRemark()) ? order.getRemark().trim() : null)
                .build();
    }

    private AiOrderDetailResult outcome(AiOrderDetailOutcome outcome) {
        return AiOrderDetailResult.builder()
                .outcome(outcome)
                .items(Collections.emptyList())
                .build();
    }

    private AiOrderDetailItem toSafeItem(OrderDetail detail) {
        if (detail == null || !StringUtils.hasText(detail.getName())
                || detail.getNumber() == null || detail.getNumber() <= 0
                || detail.getAmount() == null || detail.getAmount().signum() < 0) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        return AiOrderDetailItem.builder()
                .name(detail.getName().trim())
                .flavor(StringUtils.hasText(detail.getDishFlavor())
                        ? detail.getDishFlavor().trim() : null)
                .quantity(detail.getNumber())
                .unitAmount(detail.getAmount())
                .build();
    }

    private void validateOrderAmounts(Orders order) {
        if (order.getId() == null || !StringUtils.hasText(order.getNumber())
                || order.getGoodsAmount() == null || order.getDeliveryFee() == null
                || order.getOriginalAmount() == null || order.getDiscountAmount() == null
                || order.getAmount() == null || order.getPackAmount() < 0
                || order.getGoodsAmount().signum() < 0 || order.getDeliveryFee().signum() < 0
                || order.getOriginalAmount().signum() < 0 || order.getDiscountAmount().signum() < 0
                || order.getAmount().signum() < 0) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        BigDecimal expectedOriginal = order.getGoodsAmount()
                .add(BigDecimal.valueOf(order.getPackAmount()))
                .add(order.getDeliveryFee());
        BigDecimal expectedFinal = order.getOriginalAmount().subtract(order.getDiscountAmount());
        if (expectedOriginal.compareTo(order.getOriginalAmount()) != 0
                || expectedFinal.compareTo(order.getAmount()) != 0) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
    }
}
