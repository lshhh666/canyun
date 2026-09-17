package com.cloudmeal.service.impl;

import com.cloudmeal.client.model.AiEvidenceReference;
import com.cloudmeal.client.model.AiEvidenceSufficiencyClient;
import com.cloudmeal.client.model.AiQueryUnderstandingClient;
import com.cloudmeal.client.model.AiQueryUnderstandingResult;
import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.context.BaseContext;
import com.cloudmeal.entity.AiChatMessage;
import com.cloudmeal.entity.AiChatPendingAction;
import com.cloudmeal.enums.AiChatRole;
import com.cloudmeal.enums.AiEvidenceDecision;
import com.cloudmeal.enums.AiQueryAction;
import com.cloudmeal.exception.AiChatTurnException;
import com.cloudmeal.exception.AiServiceException;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.service.AiChatPersistenceService;
import com.cloudmeal.service.AiChatService;
import com.cloudmeal.service.AiGroundedAnswerService;
import com.cloudmeal.service.AiKnowledgeRetrievalService;
import com.cloudmeal.service.AiOrderCancellationService;
import com.cloudmeal.service.AiToolCallingService;
import com.cloudmeal.service.model.AiKnowledgeMatch;
import com.cloudmeal.service.model.AiChatHistorySnapshot;
import com.cloudmeal.service.model.AiChatTurnClaim;
import com.cloudmeal.service.model.AiToolAnswer;
import com.cloudmeal.service.model.AiPendingActionReceipt;
import com.cloudmeal.vo.AiChatActionVO;
import com.cloudmeal.vo.AiChatHistoryMessageVO;
import com.cloudmeal.vo.AiChatHistoryVO;
import com.cloudmeal.vo.AiChatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private static final int MAX_MESSAGE_LENGTH = 100;
    private static final int CONTEXT_MESSAGE_LIMIT = 20;
    /**
     * 页面恢复只展示最近4轮左右的对话，避免重新进入客服页时历史消息过长。
     * 这里只控制展示数量；数据库仍完整保存，模型上下文仍由CONTEXT_MESSAGE_LIMIT控制。
     */
    private static final int HISTORY_MESSAGE_LIMIT = 8;
    private static final String NO_KNOWLEDGE_ANSWER =
            AiGroundedAnswerService.NO_KNOWLEDGE_ANSWER;

    private final AiGroundedAnswerService groundedAnswerService;
    private final AiChatPersistenceService aiChatPersistenceService;
    private final AiKnowledgeRetrievalService aiKnowledgeRetrievalService;
    private final AiQueryUnderstandingClient aiQueryUnderstandingClient;
    private final AiEvidenceSufficiencyClient aiEvidenceSufficiencyClient;
    private final AiToolCallingService aiToolCallingService;
    private final AiOrderCancellationService aiOrderCancellationService;
    private final AiOrderCancellationIntentRouter aiOrderCancellationIntentRouter;
    private final AiOrderStatusIntentRouter aiOrderStatusIntentRouter;
    private final AiOrderDetailIntentRouter aiOrderDetailIntentRouter;

    @Override
    public AiChatHistoryVO getRecentHistory() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        AiChatHistorySnapshot snapshot = aiChatPersistenceService.loadLatestHistory(
                userId, HISTORY_MESSAGE_LIMIT);
        if (snapshot == null || snapshot.getSession() == null) {
            return AiChatHistoryVO.builder()
                    .processing(false)
                    .messages(Collections.emptyList())
                    .build();
        }
        Long sessionId = snapshot.getSession().getId();
        if (sessionId == null || sessionId <= 0) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }
        AiChatPendingAction pendingAction = snapshot.getPendingAction();
        List<AiChatMessage> source = snapshot.getMessages() == null
                ? Collections.emptyList() : snapshot.getMessages();
        List<AiChatHistoryMessageVO> messages = source.stream()
                .map(message -> toHistoryMessage(message, pendingAction))
                .collect(Collectors.toList());
        return AiChatHistoryVO.builder()
                .sessionId(sessionId)
                .processing(snapshot.isProcessing())
                .messages(messages)
                .build();
    }

    @Override
    public void startNewConversation(Long sessionId) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }
        aiChatPersistenceService.closeForNewConversation(sessionId, userId);
    }

    private AiChatHistoryMessageVO toHistoryMessage(AiChatMessage message,
                                                     AiChatPendingAction pendingAction) {
        if (message == null || message.getId() == null || message.getId() <= 0
                || message.getRole() == null || !StringUtils.hasText(message.getContent())
                || message.getSequenceNo() == null || message.getSequenceNo() <= 0) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
        }

        AiChatActionVO action = null;
        if (pendingAction != null
                && message.getId().equals(pendingAction.getAssistantMessageId())) {
            if (pendingAction.getId() == null || pendingAction.getId() <= 0
                    || pendingAction.getActionType() == null
                    || pendingAction.getExpireTime() == null) {
                throw new BaseException(MessageConstant.AI_CHAT_SESSION_STATE_ERROR);
            }
            action = AiChatActionVO.builder()
                    .actionId(pendingAction.getId())
                    .actionType(pendingAction.getActionType().name())
                    .label("确认取消")
                    .expireTime(pendingAction.getExpireTime())
                    .build();
        }
        return AiChatHistoryMessageVO.builder()
                .messageId(message.getId())
                .role(message.getRole().name().toLowerCase(Locale.ROOT))
                .content(message.getContent())
                .sequenceNo(message.getSequenceNo())
                .createTime(message.getCreateTime())
                .action(action)
                .build();
    }

    @Override
    public AiChatVO chat(String message, Long sessionId) {
        if (!StringUtils.hasText(message)) {
            throw new BaseException(MessageConstant.AI_MESSAGE_EMPTY);
        }

        String normalizedMessage = message.trim();
        int messageLength = normalizedMessage.codePointCount(0, normalizedMessage.length());
        if (messageLength > MAX_MESSAGE_LENGTH) {
            throw new BaseException(MessageConstant.AI_MESSAGE_TOO_LONG);
        }

        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        AiChatTurnClaim claim = aiChatPersistenceService.saveUserMessage(
                sessionId, userId, normalizedMessage);
        Long actualSessionId = claim.getSessionId();

        try {
            // 用户提问已在前一个短事务中提交；向量检索和聊天模型调用均不持有数据库事务。
            AiToolAnswer draft;
            // 明确的实时订单状态问题走后端高置信度快速路由，不让模型在查询前索要尾号。
            if (aiOrderCancellationIntentRouter
                    .isCancellationEligibilityQuestion(normalizedMessage)) {
                draft = answerCancellationEligibility(
                        actualSessionId, userId, normalizedMessage);
            } else if (aiOrderCancellationIntentRouter.isCancellationRequest(normalizedMessage)) {
                draft = prepareCancellationWithSelection(actualSessionId, userId);
            } else if (aiOrderDetailIntentRouter.isSelectedOrderDetailQuestion(normalizedMessage)) {
                draft = answerFromOrderDetailTool(actualSessionId, normalizedMessage);
            } else if (aiOrderStatusIntentRouter.isExplicitOrderStatusQuestion(normalizedMessage)) {
                draft = answerFromOrderStatusTool(normalizedMessage);
            } else {
                List<AiKnowledgeMatch> knowledge = retrieveKnowledge(normalizedMessage);
                List<AiChatMessage> contextMessages = aiChatPersistenceService.listTurnMessages(
                        claim, userId, CONTEXT_MESSAGE_LIMIT);
                if (aiOrderCancellationIntentRouter.isDisplayedCancellationSelection(
                        contextMessages, normalizedMessage)) {
                    draft = prepareCancellationFromOrderReference(
                            normalizedMessage, userId);
                } else if (aiOrderStatusIntentRouter.isDisplayedOrderSelection(
                        contextMessages, normalizedMessage)) {
                    draft = answerFromOrderStatusTool(normalizedMessage);
                } else if (knowledge.isEmpty()) {
                    AiQueryUnderstandingResult understanding =
                            aiQueryUnderstandingClient.understand(contextMessages);
                    draft = handleUnderstandingResult(
                            understanding, contextMessages, normalizedMessage, actualSessionId);
                } else {
                    draft = answerFromSufficientEvidence(
                            knowledge, contextMessages, normalizedMessage, actualSessionId);
                }
            }
            // 无论回答来自模型还是固定兜底，都统一保存，并恢复ACTIVE（可继续对话）。
            if (draft == null || !StringUtils.hasText(draft.getAnswer())) {
                throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
            }
            AiChatActionVO action = null;
            if (draft.getPendingAction() != null) {
                AiPendingActionReceipt receipt =
                        aiChatPersistenceService.saveAssistantMessageWithPendingAction(
                                claim, userId, draft.getAnswer(), draft.getSelectedOrderId(),
                                draft.getPendingAction());
                action = AiChatActionVO.builder()
                        .actionId(receipt.getActionId())
                        .actionType(receipt.getActionType().name())
                        .label("确认取消")
                        .expireTime(receipt.getExpireTime())
                        .build();
            } else if (draft.getSelectedOrderId() == null) {
                aiChatPersistenceService.saveAssistantMessage(claim, userId, draft.getAnswer());
            } else {
                aiChatPersistenceService.saveAssistantMessage(
                        claim, userId, draft.getAnswer(), draft.getSelectedOrderId());
            }
            return AiChatVO.builder()
                    .sessionId(actualSessionId)
                    .answer(draft.getAnswer())
                    .action(action)
                    .build();
        } catch (RuntimeException originalException) {
            restoreSessionAfterFailure(claim, userId, originalException);
            throw new AiChatTurnException(
                    resolveClientErrorMessage(originalException),
                    actualSessionId,
                    originalException);
        }
    }

    @Override
    public AiChatVO confirmAction(Long actionId) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }
        Long sessionId = aiOrderCancellationService.getOwnedActionSessionId(actionId, userId);
        AiChatTurnClaim claim = aiChatPersistenceService.saveUserMessage(
                sessionId, userId, "确认取消订单");
        try {
            String answer = aiOrderCancellationService.confirmCancellation(actionId, userId);
            if (!StringUtils.hasText(answer)) {
                throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
            }
            aiChatPersistenceService.saveAssistantMessage(claim, userId, answer);
            return AiChatVO.builder()
                    .sessionId(sessionId)
                    .answer(answer)
                    .build();
        } catch (RuntimeException originalException) {
            restoreSessionAfterFailure(claim, userId, originalException);
            throw new AiChatTurnException(
                    resolveClientErrorMessage(originalException),
                    sessionId,
                    originalException);
        }
    }

    private AiToolAnswer handleUnderstandingResult(AiQueryUnderstandingResult understanding,
                                             List<AiChatMessage> contextMessages,
                                             String latestMessage,
                                             Long sessionId) {
        if (understanding == null || understanding.getAction() == null) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (understanding.getAction() == AiQueryAction.CLARIFY) {
            if (!StringUtils.hasText(understanding.getClarification())) {
                throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
            }
            // 澄清也是本轮正式的ASSISTANT（客服回答），由chat方法统一保存并恢复会话状态。
            return answerOnly(understanding.getClarification());
        }
        if (understanding.getAction() != AiQueryAction.SEARCH
                || !StringUtils.hasText(understanding.getQuery())) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }

        // 只允许一次改写后重检，仍无知识就固定兜底，避免模型循环改写或凭空作答。
        List<AiKnowledgeMatch> rewrittenKnowledge = retrieveKnowledge(understanding.getQuery());
        if (rewrittenKnowledge.isEmpty()) {
            return answerWithoutKnowledge(contextMessages, latestMessage, sessionId);
        }
        return answerFromSufficientEvidence(
                rewrittenKnowledge, contextMessages, understanding.getQuery(), sessionId);
    }

    /**
     * 没有静态知识时仍需区分“证据不足”和“必须查询当前用户数据”。
     * 这里只允许进入只读白名单工具；模型即使误判为可直接回答，也只能得到固定兜底。
     */
    private AiToolAnswer answerWithoutKnowledge(List<AiChatMessage> contextMessages,
                                          String latestMessage,
                                          Long sessionId) {
        AiEvidenceDecision decision = aiEvidenceSufficiencyClient.judge(
                contextMessages, java.util.Collections.emptyList());
        if (decision == null) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (decision == AiEvidenceDecision.COUPON_ELIGIBILITY_REQUIRED) {
            return answerFromCouponTool();
        }
        if (decision == AiEvidenceDecision.ORDER_STATUS_REQUIRED) {
            return answerFromOrderStatusTool(latestMessage);
        }
        if (decision == AiEvidenceDecision.ORDER_DETAIL_REQUIRED) {
            return answerFromOrderDetailTool(sessionId, latestMessage);
        }
        return answerOnly(NO_KNOWLEDGE_ANSWER);
    }

    private AiToolAnswer answerFromSufficientEvidence(List<AiKnowledgeMatch> knowledge,
                                                List<AiChatMessage> contextMessages,
                                                String answerQuestion,
                                                Long sessionId) {
        List<AiEvidenceReference> references = knowledge.stream()
                .map(match -> AiEvidenceReference.builder()
                        .title(match == null ? null : match.getTitle())
                        .content(match == null ? null : match.getContent())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        AiEvidenceDecision decision = aiEvidenceSufficiencyClient.judge(
                contextMessages, references);
        if (decision == AiEvidenceDecision.INSUFFICIENT) {
            return answerOnly(NO_KNOWLEDGE_ANSWER);
        }
        if (decision == AiEvidenceDecision.COUPON_ELIGIBILITY_REQUIRED) {
            return answerFromCouponTool();
        }
        if (decision == AiEvidenceDecision.ORDER_STATUS_REQUIRED) {
            return answerFromOrderStatusTool(answerQuestion);
        }
        if (decision == AiEvidenceDecision.ORDER_DETAIL_REQUIRED) {
            return answerFromOrderDetailTool(sessionId, answerQuestion);
        }
        if (decision != AiEvidenceDecision.ANSWERABLE) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        // 历史消息只用于理解问题和判断证据，不能参与最终事实生成。
        // 否则历史中的旧回答可能覆盖本轮刚检索到的权威知识。
        return answerOnly(groundedAnswerService.generate(answerQuestion, knowledge));
    }

    private AiToolAnswer answerFromCouponTool() {
        String toolAnswer = aiToolCallingService.answerCouponEligibility();
        if (!StringUtils.hasText(toolAnswer)) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        return answerOnly(toolAnswer);
    }

    private AiToolAnswer answerFromOrderStatusTool(String latestMessage) {
        AiToolAnswer toolAnswer = aiToolCallingService.answerOrderStatusWithContext(latestMessage);
        if (toolAnswer == null || !StringUtils.hasText(toolAnswer.getAnswer())) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        return toolAnswer;
    }

    private AiToolAnswer answerCancellationEligibility(Long sessionId, Long userId,
                                                        String latestMessage) {
        String suffix = aiOrderCancellationIntentRouter
                .extractOrderNumberSuffix(latestMessage);
        if (!StringUtils.hasText(suffix)) {
            return aiOrderCancellationService
                    .checkSelectedOrderCancellation(sessionId, userId);
        }

        // 先通过“当前用户的进行中订单”工具把尾号解析为内部订单主键；
        // 查询不到或尾号不唯一时直接返回工具的澄清结果，绝不猜订单。
        AiToolAnswer selection = answerFromOrderStatusTool(suffix);
        if (selection.getSelectedOrderId() == null) {
            return selection;
        }
        AiToolAnswer eligibility = aiOrderCancellationService
                .checkOrderCancellation(selection.getSelectedOrderId(), userId);
        eligibility.setSelectedOrderId(selection.getSelectedOrderId());
        return eligibility;
    }

    private AiToolAnswer prepareCancellationWithSelection(Long sessionId, Long userId) {
        AiToolAnswer preparation = aiOrderCancellationService
                .prepareCancellation(sessionId, userId);
        if (preparation == null) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (!preparation.isOrderSelectionRequired()) {
            return preparation;
        }
        return prepareCancellationFromOrderReference("", userId);
    }

    private AiToolAnswer prepareCancellationFromOrderReference(String orderReference,
                                                               Long userId) {
        AiToolAnswer selection = answerFromOrderStatusTool(orderReference);
        if (selection.getSelectedOrderId() == null) {
            selection.setAnswer(toCancellationSelectionPrompt(selection.getAnswer()));
            return selection;
        }
        AiToolAnswer preparation = aiOrderCancellationService
                .prepareOrderCancellation(selection.getSelectedOrderId(), userId);
        if (preparation == null || !StringUtils.hasText(preparation.getAnswer())) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        return preparation;
    }

    private String toCancellationSelectionPrompt(String answer) {
        if (!StringUtils.hasText(answer)) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        if (answer.contains("请回复上面显示的订单尾号")) {
            return answer.replace("请回复上面显示的订单尾号",
                    "请选择要取消的订单尾号");
        }
        return answer;
    }

    private AiToolAnswer answerFromOrderDetailTool(Long sessionId, String latestMessage) {
        String answer = aiToolCallingService.answerSelectedOrderDetail(sessionId, latestMessage);
        if (!StringUtils.hasText(answer)) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        return answerOnly(answer);
    }

    private AiToolAnswer answerOnly(String answer) {
        return AiToolAnswer.builder().answer(answer).build();
    }

    private List<AiKnowledgeMatch> retrieveKnowledge(String query) {
        List<AiKnowledgeMatch> knowledge = aiKnowledgeRetrievalService.retrieve(query);
        if (knowledge == null) {
            // 检索结果异常不等同于正常无命中，交给chat方法的失败恢复流程。
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
        return knowledge;
    }

    private String resolveClientErrorMessage(RuntimeException exception) {
        if (exception instanceof BaseException && StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage();
        }
        return MessageConstant.AI_SERVICE_UNAVAILABLE;
    }

    private void restoreSessionAfterFailure(AiChatTurnClaim claim,
                                            Long userId,
                                            RuntimeException originalException) {
        try {
            aiChatPersistenceService.restoreActiveAfterFailure(claim, userId);
        } catch (RuntimeException restoreException) {
            originalException.addSuppressed(restoreException);
        }
    }
}
