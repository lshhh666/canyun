package com.cloudmeal.service.impl;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.context.BaseContext;
import com.cloudmeal.entity.Orders;
import com.cloudmeal.enums.AiOrderQueryOutcome;
import com.cloudmeal.enums.AiOrderStatus;
import com.cloudmeal.exception.OrderBusinessException;
import com.cloudmeal.exception.UserNotLoginException;
import com.cloudmeal.mapper.OrderMapper;
import com.cloudmeal.service.AiOrderStatusService;
import com.cloudmeal.service.model.AiOrderStatusItem;
import com.cloudmeal.service.model.AiOrderStatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 只查询当前登录用户的进行中订单，并在多订单时要求用户明确选择。
 */
@Service
@RequiredArgsConstructor
public class AiOrderStatusServiceImpl implements AiOrderStatusService {

    private static final int MAX_SELECTABLE_ORDERS = 3;

    private final OrderMapper orderMapper;

    @Override
    public AiOrderStatusResult queryMyActiveOrder(String orderNumberSuffix) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }

        List<Orders> orders = orderMapper.listActiveByUserId(userId);
        if (orders == null) {
            throw new OrderBusinessException("订单数据异常");
        }
        if (orders.isEmpty()) {
            return AiOrderStatusResult.builder()
                    .outcome(AiOrderQueryOutcome.NO_ACTIVE_ORDER)
                    .candidates(Collections.emptyList())
                    .hasMore(false)
                    .build();
        }

        boolean hasMore = orders.size() > MAX_SELECTABLE_ORDERS;
        List<Orders> selectableOrders = orders.stream()
                .limit(MAX_SELECTABLE_ORDERS)
                .collect(Collectors.toList());
        int referenceLength = uniqueReferenceLength(selectableOrders);
        List<AiOrderStatusItem> candidates = selectableOrders.stream()
                .map(order -> toSafeItem(order, referenceLength))
                .collect(Collectors.toList());
        if (StringUtils.hasText(orderNumberSuffix)) {
            // 只能选择本轮实际展示的尾号，不能靠猜测完整订单号访问未展示的候选项。
            List<AiOrderStatusItem> matched = candidates.stream()
                    .filter(item -> orderNumberSuffix.equals(item.getOrderNumberSuffix()))
                    .collect(Collectors.toList());
            if (matched.size() == 1) {
                return found(matched.get(0), candidates, hasMore);
            }
            return orderNotFound(candidates, hasMore);
        }
        // 首次查询只有一笔进行中订单时不存在选择歧义，直接返回该订单。
        if (candidates.size() == 1) {
            return found(candidates.get(0), candidates, hasMore);
        }
        return selectionRequired(candidates, hasMore);
    }

    private AiOrderStatusResult found(AiOrderStatusItem selected,
                                      List<AiOrderStatusItem> candidates,
                                      boolean hasMore) {
        return AiOrderStatusResult.builder()
                .outcome(AiOrderQueryOutcome.FOUND)
                .selectedOrder(selected)
                .candidates(candidates)
                .hasMore(hasMore)
                .build();
    }

    private AiOrderStatusResult selectionRequired(List<AiOrderStatusItem> candidates,
                                                  boolean hasMore) {
        return AiOrderStatusResult.builder()
                .outcome(AiOrderQueryOutcome.SELECTION_REQUIRED)
                .candidates(candidates)
                .hasMore(hasMore)
                .build();
    }

    private AiOrderStatusResult orderNotFound(List<AiOrderStatusItem> candidates,
                                              boolean hasMore) {
        return AiOrderStatusResult.builder()
                .outcome(AiOrderQueryOutcome.ORDER_NOT_FOUND)
                .candidates(candidates)
                .hasMore(hasMore)
                .build();
    }

    private int uniqueReferenceLength(List<Orders> orders) {
        int maximumLength = 4;
        for (Orders order : orders) {
            validateOrder(order);
            maximumLength = Math.max(maximumLength, order.getNumber().trim().length());
        }
        for (int length = 4; length <= maximumLength; length++) {
            Set<String> references = new HashSet<>();
            boolean unique = true;
            for (Orders order : orders) {
                String number = order.getNumber().trim();
                String reference = number.substring(Math.max(0, number.length() - length));
                if (!references.add(reference)) {
                    unique = false;
                    break;
                }
            }
            if (unique) {
                return length;
            }
        }
        throw new OrderBusinessException("订单数据异常");
    }

    private AiOrderStatusItem toSafeItem(Orders order, int referenceLength) {
        validateOrder(order);
        String number = order.getNumber().trim();
        String suffix = number.substring(Math.max(0, number.length() - referenceLength));
        return AiOrderStatusItem.builder()
                .orderId(order.getId())
                .orderNumberSuffix(suffix)
                .status(AiOrderStatus.fromCode(order.getStatus()))
                .orderTime(order.getOrderTime())
                .estimatedDeliveryTime(order.getEstimatedDeliveryTime())
                .build();
    }

    private void validateOrder(Orders order) {
        if (order == null || order.getId() == null || order.getId() <= 0
                || !StringUtils.hasText(order.getNumber())
                || order.getOrderTime() == null) {
            throw new OrderBusinessException("订单数据异常");
        }
    }
}
