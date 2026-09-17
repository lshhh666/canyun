package com.sky.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sky.constant.MessageConstant;
import com.sky.entity.AiChatMessage;
import com.sky.entity.AiChatSession;
import com.sky.enums.AiChatSessionStatus;
import com.sky.exception.BaseException;
import com.sky.mapper.AiChatMessageMapper;
import com.sky.mapper.AiChatSessionMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用独立线程和独立数据库事务验证同一会话的并发状态转换。
 */
@SpringBootTest(properties = "sky.websocket.enabled=false")
class AiChatPersistenceConcurrencyIntegrationTest {

    @Autowired
    private AiChatPersistenceService persistenceService;

    @Autowired
    private AiChatSessionMapper sessionMapper;

    @Autowired
    private AiChatMessageMapper messageMapper;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldAllowOnlyOneConcurrentQuestionForActiveOrExpiredSession(boolean expired) throws Exception {
        long userId = 920000000L + Math.floorMod(System.nanoTime(), 1000000L);
        AiChatSession session = insertActiveSession(userId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            if (expired) {
                persistenceService.saveUserMessage(session.getId(), userId, "待接替的旧问题");
                sessionMapper.update(null, Wrappers.<AiChatSession>lambdaUpdate()
                        .eq(AiChatSession::getId, session.getId())
                        .set(AiChatSession::getProcessingDeadline, LocalDateTime.now().minusSeconds(1)));
            }
            Future<String> first = executor.submit(
                    () -> sendQuestion(session.getId(), userId, "并发问题A", ready, start));
            Future<String> second = executor.submit(
                    () -> sendQuestion(session.getId(), userId, "并发问题B", ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<String> outcomes = Arrays.asList(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, outcomes.stream()
                    .filter(MessageConstant.AI_CHAT_REPLY_PENDING::equals)
                    .count());
            assertEquals(AiChatSessionStatus.PROCESSING,
                    sessionMapper.selectById(session.getId()).getStatus());

            List<AiChatMessage> messages =
                    messageMapper.selectRecentMessages(session.getId(), 10);
            assertEquals(expired ? 2 : 1, messages.size());
            assertEquals(1, messages.get(0).getSequenceNo());
            assertEquals(expired ? 2 : 1,
                    sessionMapper.selectById(session.getId()).getProcessingSequenceNo());
        } finally {
            start.countDown();
            executor.shutdownNow();
            messageMapper.delete(Wrappers.<AiChatMessage>lambdaQuery()
                    .eq(AiChatMessage::getSessionId, session.getId()));
            sessionMapper.deleteById(session.getId());
        }
    }

    private String sendQuestion(Long sessionId,
                                Long userId,
                                String message,
                                CountDownLatch ready,
                                CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            persistenceService.saveUserMessage(sessionId, userId, message);
            return "SUCCESS";
        } catch (BaseException exception) {
            return exception.getMessage();
        }
    }

    private AiChatSession insertActiveSession(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        AiChatSession session = AiChatSession.builder()
                .userId(userId)
                .title("并发状态机测试会话")
                .status(AiChatSessionStatus.ACTIVE)
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, sessionMapper.insert(session));
        return session;
    }
}
