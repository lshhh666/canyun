package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.entity.AiChatPendingAction;
import com.sky.entity.AiChatSession;
import com.sky.entity.Orders;
import com.sky.enums.AiOrderCancellationOutcome;
import com.sky.enums.AiPendingActionStatus;
import com.sky.enums.AiPendingActionType;
import com.sky.exception.BaseException;
import com.sky.mapper.AiChatPendingActionMapper;
import com.sky.mapper.AiChatSessionMapper;
import com.sky.mapper.OrderMapper;
import com.sky.service.AiOrderCancellationService;
import com.sky.service.OrderService;
import com.sky.service.model.AiPendingActionDraft;
import com.sky.service.model.AiToolAnswer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiOrderCancellationServiceImpl implements AiOrderCancellationService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatPendingActionMapper pendingActionMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Override
    public AiToolAnswer prepareCancellation(Long sessionId, Long userId) {
        return assessSelectedOrder(sessionId, userId, true);
    }

    @Override
    public AiToolAnswer prepareOrderCancellation(Long orderId, Long userId) {
        if (orderId == null || orderId <= 0 || userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }
        Orders order = orderMapper.getAiCancellationByIdAndUserId(orderId, userId);
        AiToolAnswer result = assessOrder(order, true);
        if (order != null) {
            // 与确认消息、待确认动作在同一短事务中保存，后续不再依赖尾号猜订单。
            result.setSelectedOrderId(order.getId());
        }
        return result;
    }

    @Override
    public AiToolAnswer checkSelectedOrderCancellation(Long sessionId, Long userId) {
        return assessSelectedOrder(sessionId, userId, false);
    }

    @Override
    public AiToolAnswer checkOrderCancellation(Long orderId, Long userId) {
        if (orderId == null || orderId <= 0 || userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }
        Orders order = orderMapper.getAiCancellationByIdAndUserId(orderId, userId);
        return assessOrder(order, false);
    }

    private AiToolAnswer assessSelectedOrder(Long sessionId, Long userId,
                                             boolean createConfirmation) {
        AiChatSession session = sessionMapper.selectOwned(sessionId, userId);
        if (session == null) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }
        if (session.getSelectedOrderId() == null) {
            return selectionRequired("请先选择要取消的订单。");
        }
        Orders order = orderMapper.getAiCancellationByIdAndUserId(
                session.getSelectedOrderId(), userId);
        if (order == null) {
            return selectionRequired("当前会话没有可操作的已选订单，请重新选择。");
        }
        return assessOrder(order, createConfirmation);
    }

    private AiToolAnswer assessOrder(Orders order, boolean createConfirmation) {
        if (order == null) {
            return selectionRequired("当前会话没有可操作的已选订单，请重新选择。");
        }
        String suffix = orderNumberSuffix(order.getNumber());
        if (Orders.CANCELLED.equals(order.getStatus())) {
            return answer("订单尾号" + suffix + "已经取消，请勿重复操作。", null);
        }
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())
                || !Orders.UN_PAID.equals(order.getPayStatus())) {
            return answer("订单尾号" + suffix
                    + "当前不是待付款未支付状态，暂不支持通过云小餐取消。", null);
        }
        if (!createConfirmation) {
            return answer("订单尾号" + suffix
                    + "当前为待付款未支付，可以取消。如需取消，请告诉我“帮我取消”。", null);
        }
        return answer("订单尾号" + suffix + "当前待付款。确定要取消这笔订单吗？",
                AiPendingActionDraft.builder()
                        .actionType(AiPendingActionType.CANCEL_ORDER)
                        .targetOrderId(order.getId())
                        .build());
    }

    @Override
    public Long getOwnedActionSessionId(Long actionId, Long userId) {
        AiChatPendingAction action = requireOwnedAction(actionId, userId, false);
        return action.getSessionId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String confirmCancellation(Long actionId, Long userId) {
        AiChatPendingAction action = requireOwnedAction(actionId, userId, true);
        if (action.getActionType() != AiPendingActionType.CANCEL_ORDER) {
            throw new BaseException(MessageConstant.AI_PENDING_ACTION_STATE_ERROR);
        }
        if (action.getStatus() != AiPendingActionStatus.PENDING_CONFIRMATION) {
            return terminalResult(action);
        }

        LocalDateTime now = LocalDateTime.now();
        if (action.getExpireTime() == null || !action.getExpireTime().isAfter(now)) {
            String result = "本次取消确认已过期，请重新发起取消。";
            complete(action, AiPendingActionStatus.EXPIRED, result, now);
            return result;
        }

        Orders snapshot = orderMapper.getAiCancellationByIdAndUserId(
                action.getTargetOrderId(), userId);
        String suffix = snapshot == null ? "" : orderNumberSuffix(snapshot.getNumber());
        AiOrderCancellationOutcome outcome = orderService.cancelPendingForAi(
                action.getTargetOrderId(), userId);
        if (outcome == AiOrderCancellationOutcome.CANCELLED
                || outcome == AiOrderCancellationOutcome.ALREADY_CANCELLED) {
            String result = StringUtils.hasText(suffix)
                    ? "订单尾号" + suffix + "已取消。" : "订单已取消。";
            complete(action, AiPendingActionStatus.SUCCEEDED, result, now);
            return result;
        }
        String result = outcome == AiOrderCancellationOutcome.NOT_CANCELLABLE
                ? "订单状态已变化，当前无法取消。"
                : "没有找到可取消的订单，请前往订单页查看。";
        complete(action, AiPendingActionStatus.REJECTED, result, now);
        return result;
    }

    private void complete(AiChatPendingAction action, AiPendingActionStatus status,
                          String result, LocalDateTime now) {
        int rows = pendingActionMapper.completePending(
                action.getId(), action.getUserId(), status.getValue(), result, now);
        if (rows != 1) {
            throw new BaseException(MessageConstant.AI_PENDING_ACTION_STATE_ERROR);
        }
    }

    private String terminalResult(AiChatPendingAction action) {
        if (StringUtils.hasText(action.getResultMessage())) {
            return action.getResultMessage();
        }
        throw new BaseException(MessageConstant.AI_PENDING_ACTION_STATE_ERROR);
    }

    private AiChatPendingAction requireOwnedAction(Long actionId, Long userId,
                                                    boolean forUpdate) {
        if (actionId == null || actionId <= 0 || userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.AI_PENDING_ACTION_UNAVAILABLE);
        }
        AiChatPendingAction action = forUpdate
                ? pendingActionMapper.selectOwnedForUpdate(actionId, userId)
                : pendingActionMapper.selectOwned(actionId, userId);
        if (action == null || action.getSessionId() == null) {
            throw new BaseException(MessageConstant.AI_PENDING_ACTION_UNAVAILABLE);
        }
        return action;
    }

    private AiToolAnswer answer(String text, AiPendingActionDraft action) {
        return AiToolAnswer.builder().answer(text).pendingAction(action).build();
    }

    private AiToolAnswer selectionRequired(String text) {
        return AiToolAnswer.builder()
                .answer(text)
                .orderSelectionRequired(true)
                .build();
    }

    private String orderNumberSuffix(String number) {
        if (!StringUtils.hasText(number)) {
            throw new BaseException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        String value = number.trim();
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }
}
