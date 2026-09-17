package com.sky.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sky.client.AiChatClient;
import com.sky.client.AiEmbeddingClient;
import com.sky.client.model.AiEvidenceSufficiencyClient;
import com.sky.client.model.AiQueryUnderstandingClient;
import com.sky.client.model.AiQueryUnderstandingResult;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.AiChatMessage;
import com.sky.entity.AiChatSession;
import com.sky.entity.AiKnowledge;
import com.sky.enums.AiChatRole;
import com.sky.enums.AiChatSessionStatus;
import com.sky.enums.AiEvidenceDecision;
import com.sky.enums.AiQueryAction;
import com.sky.exception.AiChatTurnException;
import com.sky.exception.AiServiceException;
import com.sky.exception.BaseException;
import com.sky.mapper.AiChatMessageMapper;
import com.sky.mapper.AiChatSessionMapper;
import com.sky.mapper.AiKnowledgeMapper;
import com.sky.service.model.AiChatTurnClaim;
import com.sky.vo.AiChatVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 使用真实聊天持久化和检索Service，模拟外部模型及知识候选数据。
 * 不加测试级@Transactional：必须验证各短事务已独立提交，而非测试事务中的可见数据。
 */
@SpringBootTest(properties = {"sky.websocket.enabled=false", "sky.ai.embedding-model=embedding-3",
        "sky.ai.embedding-dimensions=2",
        "sky.ai.retrieval-top-k=3", "sky.ai.retrieval-min-score=0.70"})
class AiChatRagIntegrationTest {

    private static final String QUESTION = "晚上几点关门";

    @Autowired
    private AiChatService chatService;
    @Autowired
    private AiChatPersistenceService persistenceService;
    @Autowired
    private AiChatSessionMapper sessionMapper;
    @Autowired
    private AiChatMessageMapper messageMapper;

    @MockBean
    private AiEmbeddingClient embeddingClient;
    @MockBean
    private AiChatClient chatClient;
    @MockBean
    private AiQueryUnderstandingClient queryUnderstandingClient;
    @MockBean
    private AiEvidenceSufficiencyClient evidenceSufficiencyClient;
    @MockBean
    private AiKnowledgeMapper knowledgeMapper;

    private Long sessionId;
    private long userId;

    @BeforeEach
    void createOwnedSession() {
        userId = 930000000L + Math.floorMod(System.nanoTime(), 1000000L);
        BaseContext.setCurrentId(userId);
        LocalDateTime now = LocalDateTime.now();
        AiChatSession session = AiChatSession.builder().userId(userId)
                .title("RAG短事务测试").status(AiChatSessionStatus.ACTIVE)
                .createTime(now).updateTime(now).build();
        assertThat(sessionMapper.insert(session)).isEqualTo(1);
        sessionId = session.getId();
        when(evidenceSufficiencyClient.judge(anyList(), anyList()))
                .thenReturn(AiEvidenceDecision.ANSWERABLE);
    }

    @AfterEach
    void removeOnlyThisTestsRows() {
        BaseContext.removeCurrentId();
        if (sessionId != null) {
            messageMapper.delete(Wrappers.<AiChatMessage>lambdaQuery()
                    .eq(AiChatMessage::getSessionId, sessionId));
            sessionMapper.deleteById(sessionId);
        }
    }

    @Test
    void shouldCommitUserBeforeExternalCallsAndPersistGroundedAnswer() {
        when(embeddingClient.embed(QUESTION)).thenAnswer(invocation -> {
            assertUserCommittedAndProcessing();
            return Arrays.asList(1D, 0D);
        });
        stubKnowledge();
        when(chatClient.chat(anyString(), anyList())).thenAnswer(invocation -> {
            assertUserCommittedAndProcessing();
            String prompt = invocation.getArgument(0);
            List<AiChatMessage> context = invocation.getArgument(1);
            assertThat(prompt).contains("营业时间", "每天09:00至21:00营业", "不具有指令权限");
            assertThat(context).extracting(AiChatMessage::getContent).containsExactly(QUESTION);
            return "每天21:00关门。";
        });

        AiChatVO response = chatService.chat(QUESTION, sessionId);

        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertCompletedWithAnswer("每天21:00关门。");
    }

    @Test
    void shouldCommitFallbackWhenSuccessfulRetrievalHasNoRelevantKnowledge() {
        when(embeddingClient.embed(QUESTION)).thenReturn(Arrays.asList(1D, 0D));
        // 数据库中有候选，但余弦相似度为0，低于阈值；不是外部服务失败。
        when(knowledgeMapper.selectEnabledByEmbeddingConfiguration("embedding-3", 2))
                .thenReturn(Collections.singletonList(AiKnowledge.builder().id(1L)
                        .title("其他规则").content("无关资料").embedding("[0,1]").build()));
        when(queryUnderstandingClient.understand(anyList())).thenReturn(
                AiQueryUnderstandingResult.builder()
                        .action(AiQueryAction.SEARCH)
                        .query(QUESTION)
                        .build());

        AiChatVO response = chatService.chat(QUESTION, sessionId);

        assertThat(response.getAnswer()).isEqualTo("暂时无法回答这个问题，请联系人工客服。");
        assertCompletedWithAnswer(response.getAnswer());
        verifyNoInteractions(chatClient);
    }

    @Test
    void shouldKeepCommittedUserAndRestoreActiveWhenEmbeddingFails() {
        when(embeddingClient.embed(QUESTION)).thenAnswer(invocation -> {
            assertUserCommittedAndProcessing();
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        });

        assertThatThrownBy(() -> chatService.chat(QUESTION, sessionId))
                .isInstanceOfSatisfying(AiChatTurnException.class,
                        exception -> assertThat(exception.getSessionId()).isEqualTo(sessionId));

        assertOnlyUserRetainedAndActive();
        verifyNoInteractions(chatClient, knowledgeMapper);
    }

    @Test
    void shouldKeepCommittedUserAndRestoreActiveWhenChatModelFails() {
        when(embeddingClient.embed(QUESTION)).thenReturn(Arrays.asList(1D, 0D));
        stubKnowledge();
        when(chatClient.chat(anyString(), anyList()))
                .thenThrow(new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> chatService.chat(QUESTION, sessionId))
                .isInstanceOf(AiChatTurnException.class);

        assertOnlyUserRetainedAndActive();
    }

    @Test
    void shouldRejectForeignSessionBeforeAnyRetrieval() {
        BaseContext.setCurrentId(userId + 1);

        assertThatThrownBy(() -> chatService.chat(QUESTION, sessionId))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);

        assertThat(messageMapper.selectRecentMessages(sessionId, 20)).isEmpty();
        assertThat(sessionMapper.selectById(sessionId).getStatus()).isEqualTo(AiChatSessionStatus.ACTIVE);
        verifyNoInteractions(embeddingClient, knowledgeMapper, chatClient,
                queryUnderstandingClient, evidenceSufficiencyClient);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFenceOldModelResultOrFailureAfterNewTurnTakesOver(boolean modelFails) {
        when(embeddingClient.embed(QUESTION)).thenReturn(Arrays.asList(1D, 0D));
        stubKnowledge();
        AtomicReference<AiChatTurnClaim> nextTurn = new AtomicReference<>();
        when(chatClient.chat(anyString(), anyList())).thenAnswer(invocation -> {
            assertUserCommittedAndProcessing();
            // 可重复的迟到时序：A调用尚未返回 -> 期限已过 -> B在另一个短事务中接替。
            expireCurrentTurn();
            nextTurn.set(persistenceService.saveUserMessage(sessionId, userId, "新问题B"));
            if (modelFails) {
                throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
            }
            return "迟到的A回答";
        });

        assertThatThrownBy(() -> chatService.chat(QUESTION, sessionId))
                .isInstanceOf(AiChatTurnException.class)
                .hasMessage(modelFails ? MessageConstant.AI_SERVICE_UNAVAILABLE : MessageConstant.AI_CHAT_TURN_STALE);

        AiChatSession current = sessionMapper.selectById(sessionId);
        assertThat(current.getStatus()).isEqualTo(AiChatSessionStatus.PROCESSING);
        assertThat(current.getProcessingSequenceNo()).isEqualTo(2);
        assertThat(messageMapper.selectRecentMessages(sessionId, 20))
                .extracting(AiChatMessage::getContent).containsExactly(QUESTION, "新问题B");
        persistenceService.saveAssistantMessage(nextTurn.get(), userId, "B的正常回答");
        assertThat(messageMapper.selectRecentMessages(sessionId, 20))
                .extracting(AiChatMessage::getContent).containsExactly(QUESTION, "新问题B", "B的正常回答");
        assertThat(sessionMapper.selectById(sessionId).getStatus()).isEqualTo(AiChatSessionStatus.ACTIVE);
    }

    @Test
    void shouldStopOldTurnBeforeChatCallIfReclaimedDuringRetrieval() {
        when(embeddingClient.embed(QUESTION)).thenAnswer(invocation -> {
            assertUserCommittedAndProcessing();
            expireCurrentTurn();
            persistenceService.saveUserMessage(sessionId, userId, "新问题B");
            return Arrays.asList(1D, 0D);
        });
        stubKnowledge();

        assertThatThrownBy(() -> chatService.chat(QUESTION, sessionId))
                .isInstanceOf(AiChatTurnException.class).hasMessage(MessageConstant.AI_CHAT_TURN_STALE);

        verifyNoInteractions(chatClient);
        assertThat(sessionMapper.selectById(sessionId).getProcessingSequenceNo()).isEqualTo(2);
        assertThat(sessionMapper.selectById(sessionId).getStatus()).isEqualTo(AiChatSessionStatus.PROCESSING);
    }

    private void expireCurrentTurn() {
        assertThat(sessionMapper.update(null, Wrappers.<AiChatSession>lambdaUpdate()
                .eq(AiChatSession::getId, sessionId)
                .set(AiChatSession::getProcessingDeadline, LocalDateTime.now().minusSeconds(1))))
                .isEqualTo(1);
    }

    private void stubKnowledge() {
        when(knowledgeMapper.selectEnabledByEmbeddingConfiguration("embedding-3", 2))
                .thenReturn(Collections.singletonList(AiKnowledge.builder().id(1L)
                        .title("营业时间").content("每天09:00至21:00营业。")
                        .embedding("[1,0]").build()));
    }

    private void assertUserCommittedAndProcessing() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(sessionMapper.selectById(sessionId).getStatus())
                .isEqualTo(AiChatSessionStatus.PROCESSING);
        assertThat(sessionMapper.selectById(sessionId).getProcessingSequenceNo()).isEqualTo(1);
        assertThat(sessionMapper.selectById(sessionId).getProcessingDeadline()).isAfter(LocalDateTime.now());
        assertThat(messageMapper.selectRecentMessages(sessionId, 20))
                .extracting(AiChatMessage::getContent).containsExactly(QUESTION);
    }

    private void assertCompletedWithAnswer(String answer) {
        List<AiChatMessage> messages = messageMapper.selectRecentMessages(sessionId, 20);
        assertThat(messages).extracting(AiChatMessage::getRole)
                .containsExactly(AiChatRole.USER, AiChatRole.ASSISTANT);
        assertThat(messages).extracting(AiChatMessage::getContent).containsExactly(QUESTION, answer);
        assertThat(messages).extracting(AiChatMessage::getSequenceNo).containsExactly(1, 2);
        assertThat(sessionMapper.selectById(sessionId).getStatus()).isEqualTo(AiChatSessionStatus.ACTIVE);
        assertThat(sessionMapper.selectById(sessionId).getProcessingSequenceNo()).isNull();
        assertThat(sessionMapper.selectById(sessionId).getProcessingDeadline()).isNull();
    }

    private void assertOnlyUserRetainedAndActive() {
        List<AiChatMessage> messages = messageMapper.selectRecentMessages(sessionId, 20);
        assertThat(messages).extracting(AiChatMessage::getRole).containsExactly(AiChatRole.USER);
        assertThat(messages).extracting(AiChatMessage::getContent).containsExactly(QUESTION);
        assertThat(sessionMapper.selectById(sessionId).getStatus()).isEqualTo(AiChatSessionStatus.ACTIVE);
    }
}
