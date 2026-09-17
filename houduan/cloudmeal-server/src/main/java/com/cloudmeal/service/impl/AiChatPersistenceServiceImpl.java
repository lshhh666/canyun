package com.cloudmeal.service.impl;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.entity.AiChatMessage;
import com.cloudmeal.entity.AiChatPendingAction;
import com.cloudmeal.entity.AiChatSession;
import com.cloudmeal.enums.AiChatRole;
import com.cloudmeal.enums.AiChatSessionStatus;
import com.cloudmeal.enums.AiPendingActionStatus;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.mapper.AiChatMessageMapper;
import com.cloudmeal.mapper.AiChatPendingActionMapper;
import com.cloudmeal.mapper.AiChatSessionMapper;
import com.cloudmeal.properties.AiProperties;
import com.cloudmeal.service.AiChatPersistenceService;
import com.cloudmeal.service.model.AiChatTurnClaim;
import com.cloudmeal.service.model.AiChatHistorySnapshot;
import com.cloudmeal.service.model.AiPendingActionDraft;
import com.cloudmeal.service.model.AiPendingActionReceipt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 客服会话与消息持久化服务实现。
 */
@Service
@RequiredArgsConstructor
public class AiChatPersistenceServiceImpl implements AiChatPersistenceService {

    private static final String DEFAULT_SESSION_TITLE = "新对话";
    private static final int FIRST_MESSAGE_SEQUENCE = 1;
    private static final int MAX_CONTEXT_MESSAGE_COUNT = 20;
    private static final int MAX_HISTORY_MESSAGE_COUNT = 50;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiChatPendingActionMapper pendingActionMapper;
    private final AiProperties aiProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatHistorySnapshot loadLatestHistory(Long userId, int limit) {
        validateUserId(userId);
        if (limit < 1 || limit > MAX_HISTORY_MESSAGE_COUNT) {
            throw new BaseException(MessageConstant.AI_CHAT_CONTEXT_LIMIT_INVALID);
        }

        AiChatSession session = sessionMapper.selectLatestContinuable(userId);
        if (session == null) {
            return emptyHistory();
        }

        LocalDateTime now = LocalDateTime.now();
        // 正在处理的请求只受本轮deadline约束；除此之外，闲置超时的会话不再恢复。
        if (isIdleExpired(session, now) && !isStillProcessing(session, now)) {
            session = requireOwnedSessionForUpdate(session.getId(), userId);
            now = LocalDateTime.now();
            if (session.getStatus() == AiChatSessionStatus.CLOSED) {
                return emptyHistory();
            }
            // 加锁后再次判断，避免并发的新请求刚把会话改为PROCESSING却被误关闭。
            if (isIdleExpired(session, now) && !isStillProcessing(session, now)) {
                closeSessionState(session, userId,
                        "会话长时间未活动，确认操作已失效。", now);
                return emptyHistory();
            }
        }
        List<AiChatMessage> messages = messageMapper.selectRecentMessages(session.getId(), limit);
        AiChatPendingAction pendingAction = pendingActionMapper.selectRestorable(
                session.getId(), userId, now);
        boolean processing = isStillProcessing(session, now);
        return AiChatHistorySnapshot.builder()
                .session(session)
                .messages(messages == null ? java.util.Collections.emptyList() : messages)
                .pendingAction(pendingAction)
                .processing(processing)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeForNewConversation(Long sessionId, Long userId) {
        validateUserId(userId);
        AiChatSession session = requireOwnedSessionForUpdate(sessionId, userId);
        if (session.getStatus() == AiChatSessionStatus.CLOSED) {
            // 关闭接口保持幂等，前端重试不会被当成系统异常。
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (session.getStatus() == AiChatSessionStatus.PROCESSING
                && isStillProcessing(session, now)) {
            throw new BaseException(MessageConstant.AI_CHAT_REPLY_PENDING);
        }
        if (session.getStatus() != AiChatSessionStatus.ACTIVE
                && session.getStatus() != AiChatSessionStatus.PROCESSING) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }

        closeSessionState(session, userId,
                "会话已结束，请在新对话中重新发起操作。", now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatTurnClaim saveUserMessage(Long sessionId, Long userId, String message) {
        validateUserId(userId);
        if (!StringUtils.hasText(message)) {
            throw new BaseException(MessageConstant.AI_MESSAGE_EMPTY);
        }

        int timeoutSeconds = aiProperties.getTurnTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            throw new BaseException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (sessionId == null) {
            return createProcessingSession(userId, message, LocalDateTime.now(), timeoutSeconds);
        }

        AiChatSession session = requireOwnedSessionForUpdate(sessionId, userId);
        // 获取行锁后再取时间，避免锁等待消耗本轮的处理期限。
        LocalDateTime now = LocalDateTime.now();
        if (session.getStatus() == AiChatSessionStatus.CLOSED) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_CLOSED);
        }
        if (session.getStatus() != AiChatSessionStatus.ACTIVE
                && session.getStatus() != AiChatSessionStatus.PROCESSING) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }
        if (session.getStatus() == AiChatSessionStatus.PROCESSING
                && isStillProcessing(session, now)) {
            throw new BaseException(MessageConstant.AI_CHAT_REPLY_PENDING);
        }
        if (isIdleExpired(session, now)) {
            return replaceExpiredSession(session, userId, message, now, timeoutSeconds);
        }

        Integer sequenceNo = messageMapper.selectNextSequenceNo(sessionId);
        insertMessage(sessionId, AiChatRole.USER, message, sequenceNo, now);
        int affectedRows = sessionMapper.claimProcessingTurn(sessionId, userId,
                session.getStatus().getValue(), sequenceNo, now.plusSeconds(timeoutSeconds), now);
        if (affectedRows != 1) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }
        return new AiChatTurnClaim(sessionId, sequenceNo);
    }

    @Override
    public List<AiChatMessage> listRecentMessages(Long sessionId,
                                                  Long userId,
                                                  int limit) {
        validateUserId(userId);
        validateSessionId(sessionId);
        validateContextLimit(limit);
        if (sessionMapper.selectOwned(sessionId, userId) == null) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }
        return messageMapper.selectRecentMessages(sessionId, limit);
    }

    @Override
    public List<AiChatMessage> listTurnMessages(AiChatTurnClaim claim, Long userId, int limit) {
        validateUserId(userId);
        validateClaim(claim);
        validateContextLimit(limit);
        AiChatSession session = sessionMapper.selectOwned(claim.getSessionId(), userId);
        if (session == null) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }
        requireCurrentTurn(session, claim);
        // 此处不持锁。即使校验后发生接替，SQL上界也不允许旧请求读到新提问；
        // 最终写回答仍会加锁并再次校验轮次，保证迟到结果不能落库。
        return messageMapper.selectTurnMessages(claim.getSessionId(), claim.getProcessingSequenceNo(), limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAssistantMessage(AiChatTurnClaim claim, Long userId, String answer,
                                     Long selectedOrderId) {
        validateUserId(userId);
        validateClaim(claim);
        Long sessionId = claim.getSessionId();
        if (!StringUtils.hasText(answer)) {
            throw new BaseException(MessageConstant.AI_CHAT_ANSWER_EMPTY);
        }
        if (selectedOrderId != null && selectedOrderId <= 0) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }

        AiChatSession session = requireOwnedSessionForUpdate(sessionId, userId);
        requireCurrentTurn(session, claim);

        LocalDateTime now = LocalDateTime.now();
        Integer sequenceNo = messageMapper.selectNextSequenceNo(sessionId);
        insertMessage(sessionId, AiChatRole.ASSISTANT, answer, sequenceNo, now);
        // 期限到了但尚未被新请求接替，仍接受当前轮的有效回答。
        completeTurn(claim, userId, selectedOrderId, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiPendingActionReceipt saveAssistantMessageWithPendingAction(
            AiChatTurnClaim claim, Long userId, String answer,
            Long selectedOrderId, AiPendingActionDraft pendingAction) {
        validateUserId(userId);
        validateClaim(claim);
        if (!StringUtils.hasText(answer) || pendingAction == null
                || pendingAction.getActionType() == null
                || pendingAction.getTargetOrderId() == null
                || pendingAction.getTargetOrderId() <= 0) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }
        int timeoutSeconds = aiProperties.getActionConfirmTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            throw new BaseException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }

        Long sessionId = claim.getSessionId();
        AiChatSession session = requireOwnedSessionForUpdate(sessionId, userId);
        requireCurrentTurn(session, claim);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusSeconds(timeoutSeconds);

        // 同一会话只保留最新的一次确认，旧按钮即使再次点击也不会执行。
        pendingActionMapper.supersedePending(
                sessionId, userId, "已有新的待确认操作，请使用最新按钮。", now);
        Integer sequenceNo = messageMapper.selectNextSequenceNo(sessionId);
        AiChatMessage assistantMessage = insertMessage(
                sessionId, AiChatRole.ASSISTANT, answer, sequenceNo, now);
        if (assistantMessage.getId() == null) {
            throw new BaseException(MessageConstant.AI_CHAT_MESSAGE_SAVE_FAILED);
        }

        AiChatPendingAction action = AiChatPendingAction.builder()
                .userId(userId)
                .sessionId(sessionId)
                .actionType(pendingAction.getActionType())
                .targetOrderId(pendingAction.getTargetOrderId())
                .assistantMessageId(assistantMessage.getId())
                .status(AiPendingActionStatus.PENDING_CONFIRMATION)
                .expireTime(expireTime)
                .createTime(now)
                .updateTime(now)
                .build();
        if (pendingActionMapper.insert(action) != 1 || action.getId() == null) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }

        completeTurn(claim, userId, selectedOrderId, now);
        return AiPendingActionReceipt.builder()
                .actionId(action.getId())
                .actionType(action.getActionType())
                .expireTime(expireTime)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreActiveAfterFailure(AiChatTurnClaim claim, Long userId) {
        validateUserId(userId);
        validateClaim(claim);

        AiChatSession session = requireOwnedSessionForUpdate(claim.getSessionId(), userId);
        if (!isCurrentTurn(session, claim)) {
            // 旧请求失败不能把新请求释放；重复恢复也不改变已完成的会话。
            return;
        }
        completeTurn(claim, userId, null, LocalDateTime.now());
    }

    private AiChatTurnClaim createProcessingSession(Long userId,
                                         String message,
                                         LocalDateTime now,
                                         int timeoutSeconds) {
        AiChatSession session = AiChatSession.builder()
                .userId(userId)
                .title(DEFAULT_SESSION_TITLE)
                .status(AiChatSessionStatus.PROCESSING)
                .processingSequenceNo(FIRST_MESSAGE_SEQUENCE)
                .processingDeadline(now.plusSeconds(timeoutSeconds))
                .createTime(now)
                .updateTime(now)
                .build();
        if (sessionMapper.insert(session) != 1 || session.getId() == null) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_SAVE_FAILED);
        }

        insertMessage(session.getId(), AiChatRole.USER, message,
                FIRST_MESSAGE_SEQUENCE, now);
        return new AiChatTurnClaim(session.getId(), FIRST_MESSAGE_SEQUENCE);
    }

    private AiChatSession requireOwnedSessionForUpdate(Long sessionId, Long userId) {
        validateSessionId(sessionId);
        AiChatSession session = sessionMapper.selectOwnedForUpdate(sessionId, userId);
        if (session == null) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }
        return session;
    }

    private boolean isCurrentTurn(AiChatSession session, AiChatTurnClaim claim) {
        return session.getStatus() == AiChatSessionStatus.PROCESSING
                && claim.getProcessingSequenceNo().equals(session.getProcessingSequenceNo());
    }

    private void requireCurrentTurn(AiChatSession session, AiChatTurnClaim claim) {
        if (!isCurrentTurn(session, claim)) {
            throw new BaseException(MessageConstant.AI_CHAT_TURN_STALE);
        }
    }

    private AiChatMessage insertMessage(Long sessionId,
                               AiChatRole role,
                               String content,
                               Integer sequenceNo,
                               LocalDateTime createTime) {
        if (sequenceNo == null || sequenceNo < FIRST_MESSAGE_SEQUENCE) {
            throw new BaseException(MessageConstant.AI_CHAT_MESSAGE_SAVE_FAILED);
        }
        AiChatMessage message = AiChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .sequenceNo(sequenceNo)
                .createTime(createTime)
                .build();
        if (messageMapper.insert(message) != 1) {
            throw new BaseException(MessageConstant.AI_CHAT_MESSAGE_SAVE_FAILED);
        }
        return message;
    }

    private boolean isStillProcessing(AiChatSession session, LocalDateTime now) {
        if (session.getStatus() != AiChatSessionStatus.PROCESSING) {
            return false;
        }
        LocalDateTime deadline = session.getProcessingDeadline();
        if (deadline == null && session.getUpdateTime() != null
                && aiProperties.getTurnTimeoutSeconds() > 0) {
            deadline = session.getUpdateTime().plusSeconds(aiProperties.getTurnTimeoutSeconds());
        }
        return deadline == null || deadline.isAfter(now);
    }

    private boolean isIdleExpired(AiChatSession session, LocalDateTime now) {
        int idleTimeoutMinutes = aiProperties.getSessionIdleTimeoutMinutes();
        if (idleTimeoutMinutes <= 0) {
            throw new BaseException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        return session.getUpdateTime() == null
                || !session.getUpdateTime().plusMinutes(idleTimeoutMinutes).isAfter(now);
    }

    private AiChatTurnClaim replaceExpiredSession(AiChatSession expiredSession,
                                                   Long userId,
                                                   String message,
                                                   LocalDateTime now,
        int timeoutSeconds) {
        closeSessionState(expiredSession, userId,
                "会话长时间未活动，确认操作已失效。", now);
        return createProcessingSession(userId, message, now, timeoutSeconds);
    }

    private void closeSessionState(AiChatSession session,
                                   Long userId,
                                   String pendingActionMessage,
                                   LocalDateTime now) {
        if (session.getStatus() != AiChatSessionStatus.ACTIVE
                && session.getStatus() != AiChatSessionStatus.PROCESSING) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }
        pendingActionMapper.supersedePending(session.getId(), userId,
                pendingActionMessage, now);
        int closedRows = sessionMapper.closeSession(session.getId(), userId,
                session.getStatus().getValue(), now);
        if (closedRows != 1) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }
    }

    private AiChatHistorySnapshot emptyHistory() {
        return AiChatHistorySnapshot.builder()
                .messages(java.util.Collections.emptyList())
                .processing(false)
                .build();
    }

    private void completeTurn(AiChatTurnClaim claim, Long userId,
                              Long selectedOrderId, LocalDateTime updateTime) {
        int affectedRows = sessionMapper.completeProcessingTurn(
                claim.getSessionId(), userId, claim.getProcessingSequenceNo(),
                selectedOrderId, updateTime);
        if (affectedRows != 1) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }
    }

    private void validateClaim(AiChatTurnClaim claim) {
        if (claim == null || claim.getProcessingSequenceNo() == null
                || claim.getProcessingSequenceNo() < FIRST_MESSAGE_SEQUENCE) {
            throw new BaseException(MessageConstant.AI_CHAT_TURN_STALE);
        }
        validateSessionId(claim.getSessionId());
    }

    private void validateContextLimit(int limit) {
        if (limit < 1 || limit > MAX_CONTEXT_MESSAGE_COUNT) {
            throw new BaseException(MessageConstant.AI_CHAT_CONTEXT_LIMIT_INVALID);
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }
    }

    private void validateSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }
    }
}
