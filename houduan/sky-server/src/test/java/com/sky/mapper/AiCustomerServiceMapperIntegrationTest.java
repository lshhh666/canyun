package com.sky.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.entity.AiChatMessage;
import com.sky.entity.AiChatPendingAction;
import com.sky.entity.AiChatSession;
import com.sky.entity.AiKnowledge;
import com.sky.entity.Orders;
import com.sky.enums.AiChatRole;
import com.sky.enums.AiChatSessionStatus;
import com.sky.enums.AiKnowledgeCategory;
import com.sky.enums.AiKnowledgeStatus;
import com.sky.enums.AiPendingActionStatus;
import com.sky.enums.AiPendingActionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * AI 客服会话、消息和知识的真实数据库映射测试。
 * 每个测试完成后自动回滚，不保留测试数据。
 */
@SpringBootTest(properties = "sky.websocket.enabled=false")
@Transactional
class AiCustomerServiceMapperIntegrationTest {

    @Autowired
    private AiChatSessionMapper sessionMapper;

    @Autowired
    private AiChatMessageMapper messageMapper;

    @Autowired
    private AiKnowledgeMapper knowledgeMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AiChatPendingActionMapper pendingActionMapper;

    @Test
    void shouldValidateOwnershipAllocateSequenceAndReturnRecentMessagesInOrder() {
        LocalDateTime now = LocalDateTime.now();
        AiChatSession session = AiChatSession.builder()
                .userId(900000001L)
                .title("Mapper测试会话")
                .status(AiChatSessionStatus.PROCESSING)
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, sessionMapper.insert(session));
        assertNotNull(session.getId());

        AiChatSession savedSession = sessionMapper.selectOwnedForUpdate(session.getId(), session.getUserId());
        assertNotNull(savedSession);
        assertEquals(AiChatSessionStatus.PROCESSING, savedSession.getStatus());
        assertNull(sessionMapper.selectOwnedForUpdate(session.getId(), 900000002L));
        assertEquals(1, messageMapper.selectNextSequenceNo(session.getId()));

        insertMessage(session.getId(), AiChatRole.USER, "第一条", 1, now);
        insertMessage(session.getId(), AiChatRole.ASSISTANT, "第二条", 2, now.plusSeconds(1));
        insertMessage(session.getId(), AiChatRole.USER, "第三条", 3, now.plusSeconds(2));
        insertMessage(session.getId(), AiChatRole.ASSISTANT, "第四条", 4, now.plusSeconds(3));

        assertEquals(5, messageMapper.selectNextSequenceNo(session.getId()));
        List<AiChatMessage> recentMessages = messageMapper.selectRecentMessages(session.getId(), 2);
        assertEquals(2, recentMessages.size());
        assertEquals(3, recentMessages.get(0).getSequenceNo());
        assertEquals(AiChatRole.USER, recentMessages.get(0).getRole());
        assertEquals("第三条", recentMessages.get(0).getContent());
        assertEquals(4, recentMessages.get(1).getSequenceNo());
        assertEquals(AiChatRole.ASSISTANT, recentMessages.get(1).getRole());
        assertEquals("第四条", recentMessages.get(1).getContent());
    }

    @Test
    void shouldPersistKnowledgeFieldMapping() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        AiKnowledge knowledge = AiKnowledge.builder()
                .knowledgeKey("TEST_" + UUID.randomUUID().toString().replace("-", ""))
                .title("订单取消规则测试")
                .category(AiKnowledgeCategory.ORDER)
                .content("商家接单前可以取消订单。")
                .embedding(embeddingJson(256))
                .embeddingModel("embedding-3")
                .embeddingDimensions(256)
                .versionNo(1)
                .status(AiKnowledgeStatus.ENABLED)
                .createTime(now)
                .updateTime(now)
                .build();

        assertEquals(1, knowledgeMapper.insert(knowledge));
        AiKnowledge saved = knowledgeMapper.selectById(knowledge.getId());

        assertNotNull(saved);
        assertEquals(knowledge.getKnowledgeKey(), saved.getKnowledgeKey());
        assertEquals(AiKnowledgeCategory.ORDER, saved.getCategory());
        assertEquals("商家接单前可以取消订单。", saved.getContent());
        ObjectMapper objectMapper = new ObjectMapper();
        assertEquals(objectMapper.readTree(embeddingJson(256)),
                objectMapper.readTree(saved.getEmbedding()));
        assertEquals("embedding-3", saved.getEmbeddingModel());
        assertEquals(256, saved.getEmbeddingDimensions());
        assertEquals(1, saved.getVersionNo());
        assertEquals(AiKnowledgeStatus.ENABLED, saved.getStatus());
    }

    @Test
    void shouldQueryOnlyCurrentUsersActiveOrdersWithSafeProjection() {
        long userId = 900000003L;
        LocalDateTime now = LocalDateTime.now();
        Orders olderActive = insertOrder(userId, Orders.CONFIRMED, now.minusMinutes(5));
        Orders newestActive = insertOrder(userId, Orders.PENDING_PAYMENT, now);
        insertOrder(userId, Orders.COMPLETED, now.plusMinutes(1));
        insertOrder(900000004L, Orders.DELIVERY_IN_PROGRESS, now.plusMinutes(2));

        List<Orders> activeOrders = orderMapper.listActiveByUserId(userId);

        assertEquals(2, activeOrders.size());
        assertEquals(newestActive.getNumber(), activeOrders.get(0).getNumber());
        assertEquals(olderActive.getNumber(), activeOrders.get(1).getNumber());
        assertNull(activeOrders.get(0).getPhone());
        assertNull(activeOrders.get(0).getAddress());
        assertNull(activeOrders.get(0).getConsignee());
    }

    @Test
    void shouldPersistLockAndCompletePendingAction() {
        long userId = 900000005L;
        LocalDateTime now = LocalDateTime.now();
        AiChatSession session = AiChatSession.builder()
                .userId(userId)
                .title("待确认动作Mapper测试")
                .status(AiChatSessionStatus.ACTIVE)
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, sessionMapper.insert(session));

        AiChatMessage promptMessage = insertMessage(session.getId(), AiChatRole.ASSISTANT,
                "确定要取消这笔订单吗？", 1, now);

        AiChatPendingAction action = AiChatPendingAction.builder()
                .userId(userId)
                .sessionId(session.getId())
                .actionType(AiPendingActionType.CANCEL_ORDER)
                .targetOrderId(880000005L)
                .assistantMessageId(promptMessage.getId())
                .status(AiPendingActionStatus.PENDING_CONFIRMATION)
                .expireTime(now.plusMinutes(5))
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, pendingActionMapper.insert(action));
        assertNotNull(action.getId());
        assertEquals(action.getId(), pendingActionMapper
                .selectRestorable(session.getId(), userId, now).getId());
        assertEquals(session.getId(), sessionMapper
                .selectLatestContinuable(userId).getId());

        AiChatPendingAction locked = pendingActionMapper.selectOwnedForUpdate(action.getId(), userId);
        assertNotNull(locked);
        assertEquals(AiPendingActionType.CANCEL_ORDER, locked.getActionType());
        assertEquals(AiPendingActionStatus.PENDING_CONFIRMATION, locked.getStatus());
        assertNull(pendingActionMapper.selectOwned(action.getId(), 900000006L));

        LocalDateTime completedAt = now.plusSeconds(1);
        assertEquals(1, pendingActionMapper.completePending(action.getId(), userId,
                AiPendingActionStatus.SUCCEEDED.getValue(), "订单已取消。", completedAt));
        AiChatPendingAction completed = pendingActionMapper.selectOwned(action.getId(), userId);
        assertEquals(AiPendingActionStatus.SUCCEEDED, completed.getStatus());
        assertEquals("订单已取消。", completed.getResultMessage());
        assertEquals(0, pendingActionMapper.completePending(action.getId(), userId,
                AiPendingActionStatus.REJECTED.getValue(), "不应覆盖", now.plusSeconds(2)));

        LocalDateTime closedAt = now.plusSeconds(3);
        assertEquals(1, sessionMapper.closeSession(session.getId(), userId,
                AiChatSessionStatus.ACTIVE.getValue(), closedAt));
        assertEquals(AiChatSessionStatus.CLOSED,
                sessionMapper.selectOwned(session.getId(), userId).getStatus());
        assertNull(sessionMapper.selectLatestContinuable(userId));
        assertEquals(0, sessionMapper.closeSession(session.getId(), userId,
                AiChatSessionStatus.ACTIVE.getValue(), now.plusSeconds(4)));
    }

    private AiChatMessage insertMessage(Long sessionId,
                               AiChatRole role,
                               String content,
                               int sequenceNo,
                               LocalDateTime createTime) {
        AiChatMessage message = AiChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .sequenceNo(sequenceNo)
                .createTime(createTime)
                .build();
        assertEquals(1, messageMapper.insert(message));
        return message;
    }

    private Orders insertOrder(long userId, Integer status, LocalDateTime orderTime) {
        Orders order = Orders.builder()
                .number("AI" + UUID.randomUUID().toString().replace("-", ""))
                .status(status)
                .userId(userId)
                .addressBookId(1L)
                .orderTime(orderTime)
                .payMethod(1)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("20.00"))
                .originalAmount(new BigDecimal("20.00"))
                .discountAmount(BigDecimal.ZERO)
                .remark("AI订单状态Mapper测试")
                .phone("13800000000")
                .address("测试地址")
                .consignee("测试用户")
                .estimatedDeliveryTime(orderTime.plusMinutes(30))
                .deliveryStatus(1)
                .packAmount(1)
                .goodsAmount(new BigDecimal("13.00"))
                .deliveryFee(new BigDecimal("6.00"))
                .tablewareNumber(1)
                .tablewareStatus(1)
                .build();
        orderMapper.add(order);
        assertNotNull(order.getId());
        return order;
    }

    private String embeddingJson(int dimensions) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < dimensions; i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append("0.0");
        }
        return result.append(']').toString();
    }
}
