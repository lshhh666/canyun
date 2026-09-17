package com.sky.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sky.constant.MessageConstant;
import com.sky.entity.AiChatMessage;
import com.sky.entity.AiChatSession;
import com.sky.enums.AiChatRole;
import com.sky.enums.AiChatSessionStatus;
import com.sky.exception.BaseException;
import com.sky.mapper.AiChatMessageMapper;
import com.sky.mapper.AiChatSessionMapper;
import com.sky.service.model.AiChatTurnClaim;
import com.sky.service.model.AiChatHistorySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 客服会话状态机的真实数据库测试。
 * 每个测试完成后自动回滚，不保留会话或消息。
 */
@SpringBootTest(properties = "sky.websocket.enabled=false")
@Transactional
class AiChatPersistenceServiceIntegrationTest {

    private static final Long USER_ID = 910000001L;

    @Autowired
    private AiChatPersistenceService persistenceService;

    @Autowired
    private AiChatSessionMapper sessionMapper;

    @Autowired
    private AiChatMessageMapper messageMapper;

    @Test
    void shouldCreateProcessingSessionAndSaveFirstUserMessage() {
        AiChatTurnClaim claim = persistenceService.saveUserMessage(null, USER_ID, "订单可以取消吗？");
        Long sessionId = claim.getSessionId();

        AiChatSession session = sessionMapper.selectById(sessionId);
        assertEquals(AiChatSessionStatus.PROCESSING, session.getStatus());
        assertEquals(1, session.getProcessingSequenceNo());
        assertTrue(session.getProcessingDeadline().isAfter(LocalDateTime.now()));
        assertEquals(1, claim.getProcessingSequenceNo());

        List<AiChatMessage> messages =
                persistenceService.listRecentMessages(sessionId, USER_ID, 10);
        assertEquals(1, messages.size());
        assertEquals(AiChatRole.USER, messages.get(0).getRole());
        assertEquals("订单可以取消吗？", messages.get(0).getContent());
        assertEquals(1, messages.get(0).getSequenceNo());
    }

    @Test
    void shouldAcceptQuestionOnlyFromActiveSessionAndContinueSequence() {
        AiChatSession session = insertSession(USER_ID, AiChatSessionStatus.ACTIVE);
        insertMessage(session.getId(), AiChatRole.USER, "第一条问题", 1);
        insertMessage(session.getId(), AiChatRole.ASSISTANT, "第一条回答", 2);

        AiChatTurnClaim claim = persistenceService.saveUserMessage(
                session.getId(), USER_ID, "第二条问题");

        assertEquals(session.getId(), claim.getSessionId());
        assertEquals(3, claim.getProcessingSequenceNo());
        assertEquals(AiChatSessionStatus.PROCESSING,
                sessionMapper.selectById(session.getId()).getStatus());
        List<AiChatMessage> messages = messageMapper.selectRecentMessages(session.getId(), 10);
        assertEquals(3, messages.size());
        assertEquals(AiChatRole.USER, messages.get(2).getRole());
        assertEquals("第二条问题", messages.get(2).getContent());
        assertEquals(3, messages.get(2).getSequenceNo());
    }

    @Test
    void shouldRejectAnotherQuestionWhileProcessing() {
        Long sessionId = persistenceService.saveUserMessage(null, USER_ID, "第一条问题").getSessionId();

        BaseException exception = assertThrows(BaseException.class,
                () -> persistenceService.saveUserMessage(sessionId, USER_ID, "第二条问题"));

        assertEquals(MessageConstant.AI_CHAT_REPLY_PENDING, exception.getMessage());
        assertEquals(1, messageMapper.selectRecentMessages(sessionId, 10).size());
        assertEquals(AiChatSessionStatus.PROCESSING,
                sessionMapper.selectById(sessionId).getStatus());
    }

    @Test
    void shouldSaveAssistantMessageAndRestoreActiveAfterSuccess() {
        AiChatTurnClaim claim = persistenceService.saveUserMessage(null, USER_ID, "订单可以取消吗？");
        Long sessionId = claim.getSessionId();

        persistenceService.saveAssistantMessage(
                claim, USER_ID, "商家接单前可以取消订单。");

        assertEquals(AiChatSessionStatus.ACTIVE,
                sessionMapper.selectById(sessionId).getStatus());
        List<AiChatMessage> messages = messageMapper.selectRecentMessages(sessionId, 10);
        assertEquals(2, messages.size());
        assertEquals(AiChatRole.ASSISTANT, messages.get(1).getRole());
        assertEquals("商家接单前可以取消订单。", messages.get(1).getContent());
        assertEquals(2, messages.get(1).getSequenceNo());
    }

    @Test
    void shouldStoreSelectedOrderOnlyWhenAssistantAnswerIsSaved() {
        AiChatTurnClaim claim = persistenceService.saveUserMessage(
                null, USER_ID, "2038");

        persistenceService.saveAssistantMessage(
                claim, USER_ID, "订单尾号2038当前状态：待接单。", 88L);

        AiChatSession session = sessionMapper.selectById(claim.getSessionId());
        assertEquals(AiChatSessionStatus.ACTIVE, session.getStatus());
        assertEquals(88L, session.getSelectedOrderId());
        assertEquals(2, messageMapper.selectRecentMessages(claim.getSessionId(), 10).size());
    }

    @Test
    void shouldKeepUserMessageAndRestoreActiveAfterModelFailure() {
        AiChatTurnClaim claim = persistenceService.saveUserMessage(null, USER_ID, "优惠券可以叠加吗？");
        Long sessionId = claim.getSessionId();

        persistenceService.restoreActiveAfterFailure(claim, USER_ID);

        assertEquals(AiChatSessionStatus.ACTIVE,
                sessionMapper.selectById(sessionId).getStatus());
        List<AiChatMessage> messages = messageMapper.selectRecentMessages(sessionId, 10);
        assertEquals(1, messages.size());
        assertEquals(AiChatRole.USER, messages.get(0).getRole());
        assertEquals("优惠券可以叠加吗？", messages.get(0).getContent());
    }

    @Test
    void shouldRejectQuestionWhenSessionIsClosed() {
        AiChatSession session = insertSession(USER_ID, AiChatSessionStatus.CLOSED);

        BaseException exception = assertThrows(BaseException.class,
                () -> persistenceService.saveUserMessage(
                        session.getId(), USER_ID, "还能继续提问吗？"));

        assertEquals(MessageConstant.AI_CHAT_SESSION_CLOSED, exception.getMessage());
    }

    @Test
    void shouldUseSameResponseForMissingAndForeignSession() {
        AiChatSession session = insertSession(USER_ID, AiChatSessionStatus.ACTIVE);

        BaseException foreignException = assertThrows(BaseException.class,
                () -> persistenceService.saveUserMessage(
                        session.getId(), USER_ID + 1, "尝试越权访问"));
        BaseException missingException = assertThrows(BaseException.class,
                () -> persistenceService.saveUserMessage(
                        Long.MAX_VALUE, USER_ID, "访问不存在的会话"));

        assertEquals(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE,
                foreignException.getMessage());
        assertEquals(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE,
                missingException.getMessage());
    }

    @Test
    void shouldRejectAssistantMessageUnlessSessionIsProcessing() {
        AiChatSession session = insertSession(USER_ID, AiChatSessionStatus.ACTIVE);

        BaseException exception = assertThrows(BaseException.class,
                () -> persistenceService.saveAssistantMessage(
                        new AiChatTurnClaim(session.getId(), 1), USER_ID, "不应保存的重复回答"));

        assertEquals(MessageConstant.AI_CHAT_TURN_STALE, exception.getMessage());
        assertEquals(0, messageMapper.selectRecentMessages(session.getId(), 10).size());
    }

    @Test
    void shouldReclaimExpiredTurnAndFenceLateAnswerAndFailure() {
        AiChatTurnClaim oldTurn = persistenceService.saveUserMessage(null, USER_ID, "问题A");
        expireTurn(oldTurn);
        AiChatTurnClaim newTurn = persistenceService.saveUserMessage(oldTurn.getSessionId(), USER_ID, "问题B");

        assertEquals(2, newTurn.getProcessingSequenceNo());
        BaseException stale = assertThrows(BaseException.class,
                () -> persistenceService.saveAssistantMessage(oldTurn, USER_ID, "迟到的A回答"));
        assertEquals(MessageConstant.AI_CHAT_TURN_STALE, stale.getMessage());
        persistenceService.restoreActiveAfterFailure(oldTurn, USER_ID);

        AiChatSession current = sessionMapper.selectById(newTurn.getSessionId());
        assertEquals(AiChatSessionStatus.PROCESSING, current.getStatus());
        assertEquals(newTurn.getProcessingSequenceNo(), current.getProcessingSequenceNo());
        assertEquals(2, messageMapper.selectRecentMessages(newTurn.getSessionId(), 20).size());
        // 即使绕过Service，SQL的轮次条件也不允许旧请求释放新轮次。
        assertEquals(0, sessionMapper.completeProcessingTurn(oldTurn.getSessionId(), USER_ID,
                oldTurn.getProcessingSequenceNo(), null, LocalDateTime.now()));

        persistenceService.saveAssistantMessage(newTurn, USER_ID, "B回答");
        List<AiChatMessage> messages = messageMapper.selectRecentMessages(newTurn.getSessionId(), 20);
        assertEquals(Arrays.asList("问题A", "问题B", "B回答"),
                messages.stream().map(AiChatMessage::getContent).collect(Collectors.toList()));
        assertReleased(newTurn.getSessionId());
        // 重复或迟到的恢复不能破坏已经成功的结果。
        persistenceService.restoreActiveAfterFailure(oldTurn, USER_ID);
        persistenceService.restoreActiveAfterFailure(newTurn, USER_ID);
        assertReleased(newTurn.getSessionId());
    }

    @Test
    void shouldAcceptExpiredAnswerIfNoNewTurnHasReclaimed() {
        AiChatTurnClaim turn = persistenceService.saveUserMessage(null, USER_ID, "等待中的问题");
        expireTurn(turn);
        persistenceService.saveAssistantMessage(turn, USER_ID, "仍属于当前轮的回答");
        assertReleased(turn.getSessionId());
        assertEquals(2, messageMapper.selectRecentMessages(turn.getSessionId(), 20).size());
    }

    @Test
    void shouldReleaseFailedReclaimedTurnAndKeepBothQuestions() {
        AiChatTurnClaim oldTurn = persistenceService.saveUserMessage(null, USER_ID, "问题A");
        expireTurn(oldTurn);
        AiChatTurnClaim newTurn = persistenceService.saveUserMessage(oldTurn.getSessionId(), USER_ID, "问题B");
        persistenceService.restoreActiveAfterFailure(newTurn, USER_ID);
        assertReleased(newTurn.getSessionId());
        assertEquals(2, messageMapper.selectRecentMessages(newTurn.getSessionId(), 20).size());
    }

    @Test
    void shouldReplaceLongExpiredLegacyProcessingSessionWithoutDeadline() {
        AiChatSession legacy = insertSession(USER_ID, AiChatSessionStatus.PROCESSING);
        insertMessage(legacy.getId(), AiChatRole.USER, "升级前的问题", 1);
        sessionMapper.update(null, Wrappers.<AiChatSession>lambdaUpdate()
                .eq(AiChatSession::getId, legacy.getId())
                .set(AiChatSession::getUpdateTime, LocalDateTime.now().minusDays(1)));

        AiChatTurnClaim next = persistenceService.saveUserMessage(legacy.getId(), USER_ID, "升级后的问题");
        assertTrue(!legacy.getId().equals(next.getSessionId()));
        assertEquals(1, next.getProcessingSequenceNo());
        assertEquals(AiChatSessionStatus.CLOSED,
                sessionMapper.selectById(legacy.getId()).getStatus());
        assertEquals(AiChatSessionStatus.PROCESSING,
                sessionMapper.selectById(next.getSessionId()).getStatus());
    }

    @Test
    void shouldCloseIdleActiveSessionAndKeepItsMessages() {
        AiChatSession idle = insertSession(USER_ID, AiChatSessionStatus.ACTIVE);
        insertMessage(idle.getId(), AiChatRole.USER, "需要保留的旧消息", 1);
        sessionMapper.update(null, Wrappers.<AiChatSession>lambdaUpdate()
                .eq(AiChatSession::getId, idle.getId())
                .set(AiChatSession::getUpdateTime, LocalDateTime.now().minusMinutes(31)));

        AiChatHistorySnapshot history = persistenceService.loadLatestHistory(USER_ID, 8);

        assertNull(history.getSession());
        assertEquals(AiChatSessionStatus.CLOSED,
                sessionMapper.selectById(idle.getId()).getStatus());
        assertEquals(1, messageMapper.selectRecentMessages(idle.getId(), 8).size());
    }

    @Test
    void shouldNotImmediatelyReclaimRecentLegacyProcessingSession() {
        AiChatSession legacy = insertSession(USER_ID, AiChatSessionStatus.PROCESSING);
        BaseException pending = assertThrows(BaseException.class,
                () -> persistenceService.saveUserMessage(legacy.getId(), USER_ID, "新问题"));
        assertEquals(MessageConstant.AI_CHAT_REPLY_PENDING, pending.getMessage());
    }

    @Test
    void shouldProtectAllTurnOperationsWithOwnershipCheck() {
        AiChatTurnClaim turn = persistenceService.saveUserMessage(null, USER_ID, "自己的问题");
        assertEquals(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE,
                assertThrows(BaseException.class,
                        () -> persistenceService.saveAssistantMessage(turn, USER_ID + 1, "越权回答")).getMessage());
        assertEquals(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE,
                assertThrows(BaseException.class,
                        () -> persistenceService.restoreActiveAfterFailure(turn, USER_ID + 1)).getMessage());
        assertEquals(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE,
                assertThrows(BaseException.class,
                        () -> persistenceService.listTurnMessages(turn, USER_ID + 1, 20)).getMessage());
        assertEquals(AiChatSessionStatus.PROCESSING, sessionMapper.selectById(turn.getSessionId()).getStatus());
    }

    @Test
    void shouldBoundContextToTurnAndRejectStaleTurnBeforeReading() {
        AiChatTurnClaim first = persistenceService.saveUserMessage(null, USER_ID, "问题A");
        expireTurn(first);
        AiChatTurnClaim second = persistenceService.saveUserMessage(first.getSessionId(), USER_ID, "问题B");
        assertEquals(MessageConstant.AI_CHAT_TURN_STALE,
                assertThrows(BaseException.class,
                        () -> persistenceService.listTurnMessages(first, USER_ID, 20)).getMessage());
        assertEquals("问题B", persistenceService.listTurnMessages(second, USER_ID, 1).get(0).getContent());
        // 模拟校验后发生接替的时序：查询上界仍确保旧轮次只能读到A。
        List<AiChatMessage> oldContext = messageMapper.selectTurnMessages(first.getSessionId(), 1, 20);
        assertEquals(1, oldContext.size());
        assertEquals("问题A", oldContext.get(0).getContent());
    }

    private void expireTurn(AiChatTurnClaim turn) {
        assertEquals(1, sessionMapper.update(null, Wrappers.<AiChatSession>lambdaUpdate()
                .eq(AiChatSession::getId, turn.getSessionId())
                .set(AiChatSession::getProcessingDeadline, LocalDateTime.now().minusSeconds(1))));
    }

    private void assertReleased(Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        assertEquals(AiChatSessionStatus.ACTIVE, session.getStatus());
        assertNull(session.getProcessingSequenceNo());
        assertNull(session.getProcessingDeadline());
    }

    private AiChatSession insertSession(Long userId, AiChatSessionStatus status) {
        LocalDateTime now = LocalDateTime.now();
        AiChatSession session = AiChatSession.builder()
                .userId(userId)
                .title("状态机测试会话")
                .status(status)
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, sessionMapper.insert(session));
        return session;
    }

    private void insertMessage(Long sessionId,
                               AiChatRole role,
                               String content,
                               int sequenceNo) {
        AiChatMessage message = AiChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .sequenceNo(sequenceNo)
                .createTime(LocalDateTime.now())
                .build();
        assertEquals(1, messageMapper.insert(message));
    }
}
