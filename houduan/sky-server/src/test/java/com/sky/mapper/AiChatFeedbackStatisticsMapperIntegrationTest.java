package com.sky.mapper;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.entity.AiChatFeedback;
import com.sky.entity.AiChatMessage;
import com.sky.enums.AiChatFeedbackResult;
import com.sky.enums.AiChatRole;
import com.sky.vo.AiChatFeedbackTurnVO;
import com.sky.vo.AiChatFeedbackStatisticsVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "sky.websocket.enabled=false")
@Transactional
class AiChatFeedbackStatisticsMapperIntegrationTest {

    @Autowired
    private AiChatFeedbackMapper feedbackMapper;

    @Autowired
    private AiChatMessageMapper messageMapper;

    @Test
    void shouldAggregateFeedbackInsideRequestedTimeRange() {
        long sessionBase = System.currentTimeMillis();
        insert(sessionBase, AiChatFeedbackResult.HELPFUL,
                LocalDateTime.of(2099, 1, 1, 0, 0));
        insert(sessionBase + 1, AiChatFeedbackResult.HELPFUL,
                LocalDateTime.of(2099, 1, 1, 23, 59, 59));
        insert(sessionBase + 2, AiChatFeedbackResult.UNSOLVED,
                LocalDateTime.of(2099, 1, 2, 12, 0));
        insert(sessionBase + 3, AiChatFeedbackResult.UNSOLVED,
                LocalDateTime.of(2099, 1, 3, 0, 0));

        AiChatFeedbackStatisticsVO result = feedbackMapper.statistics(
                LocalDateTime.of(2099, 1, 1, 0, 0),
                LocalDateTime.of(2099, 1, 3, 0, 0));

        assertThat(result.getHelpfulCount()).isEqualTo(2L);
        assertThat(result.getUnsolvedCount()).isEqualTo(1L);
        assertThat(result.getTotalCount()).isEqualTo(3L);
    }

    @Test
    void statisticsShouldUseLastFeedbackTimeInsteadOfFirstFeedbackTime() {
        long sessionId = System.currentTimeMillis();
        insert(sessionId, AiChatFeedbackResult.HELPFUL,
                LocalDateTime.of(2097, 12, 31, 23, 59),
                LocalDateTime.of(2098, 1, 1, 12, 0));

        AiChatFeedbackStatisticsVO result = feedbackMapper.statistics(
                LocalDateTime.of(2098, 1, 1, 0, 0),
                LocalDateTime.of(2098, 1, 2, 0, 0));

        assertThat(result.getHelpfulCount()).isEqualTo(1L);
        assertThat(result.getUnsolvedCount()).isZero();
        assertThat(result.getTotalCount()).isEqualTo(1L);
    }

    @Test
    void shouldPageOnlyUnsolvedFeedbackAndBatchFindLatestCompletedTurn() {
        long sessionId = System.currentTimeMillis();
        LocalDateTime feedbackTime = LocalDateTime.of(2096, 1, 1, 12, 0);
        insert(sessionId, AiChatFeedbackResult.UNSOLVED,
                feedbackTime.minusDays(1), feedbackTime);
        insert(sessionId + 1, AiChatFeedbackResult.HELPFUL,
                feedbackTime.minusDays(1), feedbackTime);
        insertMessage(sessionId, 1, AiChatRole.USER, "old question");
        insertMessage(sessionId, 2, AiChatRole.ASSISTANT, "old answer");
        insertMessage(sessionId, 3, AiChatRole.USER, "latest question");
        insertMessage(sessionId, 4, AiChatRole.ASSISTANT, "latest answer");
        insertMessage(sessionId, 5, AiChatRole.USER, "unanswered question");

        PageHelper.startPage(1, 10);
        Page<AiChatFeedback> page = feedbackMapper.pageUnsolved(
                LocalDateTime.of(2096, 1, 1, 0, 0),
                LocalDateTime.of(2096, 1, 2, 0, 0));
        List<AiChatFeedbackTurnVO> turns =
                messageMapper.selectLatestTurnsBySessionIds(
                        Collections.singletonList(sessionId));

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getResult()).extracting(AiChatFeedback::getSessionId)
                .containsExactly(sessionId);
        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).getSessionId()).isEqualTo(sessionId);
        assertThat(turns.get(0).getUserQuestion())
                .isEqualTo("latest question");
        assertThat(turns.get(0).getAiAnswer()).isEqualTo("latest answer");
    }

    private void insert(long sessionId, AiChatFeedbackResult result,
                        LocalDateTime createTime) {
        insert(sessionId, result, createTime, createTime);
    }

    private void insert(long sessionId, AiChatFeedbackResult result,
                        LocalDateTime createTime, LocalDateTime updateTime) {
        AiChatFeedback feedback = AiChatFeedback.builder()
                .sessionId(sessionId)
                .userId(sessionId)
                .result(result)
                .createTime(createTime)
                .updateTime(updateTime)
                .build();
        assertThat(feedbackMapper.insert(feedback)).isEqualTo(1);
    }

    private void insertMessage(long sessionId, int sequenceNo,
                               AiChatRole role, String content) {
        AiChatMessage message = AiChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .sequenceNo(sequenceNo)
                .createTime(LocalDateTime.of(2096, 1, 1, 10, sequenceNo))
                .build();
        assertThat(messageMapper.insert(message)).isEqualTo(1);
    }
}
