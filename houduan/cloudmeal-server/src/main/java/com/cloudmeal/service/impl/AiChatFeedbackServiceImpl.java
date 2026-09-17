package com.cloudmeal.service.impl;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.context.BaseContext;
import com.cloudmeal.dto.AiChatFeedbackDTO;
import com.cloudmeal.entity.AiChatFeedback;
import com.cloudmeal.entity.AiChatSession;
import com.cloudmeal.enums.AiChatSessionStatus;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.mapper.AiChatFeedbackMapper;
import com.cloudmeal.mapper.AiChatSessionMapper;
import com.cloudmeal.service.AiChatFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 会话评价使用独立短事务。
 * 它不会参与关闭会话的事务，因此评价失败也不会撤销已经完成的会话关闭。
 */
@Service
@RequiredArgsConstructor
public class AiChatFeedbackServiceImpl implements AiChatFeedbackService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatFeedbackMapper feedbackMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(AiChatFeedbackDTO feedbackDTO) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || userId <= 0) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }
        if (feedbackDTO == null || feedbackDTO.getSessionId() == null
                || feedbackDTO.getSessionId() <= 0 || feedbackDTO.getResult() == null) {
            throw new BaseException(MessageConstant.AI_CHAT_FEEDBACK_INVALID);
        }

        AiChatSession session = sessionMapper.selectOwned(feedbackDTO.getSessionId(), userId);
        if (session == null) {
            throw new BaseException(MessageConstant.AI_CHAT_SESSION_UNAVAILABLE);
        }
        if (session.getStatus() != AiChatSessionStatus.CLOSED) {
            throw new BaseException(MessageConstant.AI_CHAT_FEEDBACK_SESSION_NOT_CLOSED);
        }

        LocalDateTime now = LocalDateTime.now();
        AiChatFeedback existing = feedbackMapper.selectBySessionIdForUpdate(session.getId());
        if (existing != null && existing.getResult() != feedbackDTO.getResult()) {
            feedbackMapper.obsoleteActiveRetests(existing.getId(), now);
            feedbackMapper.deleteKnowledgeRelations(existing.getId());
        }
        AiChatFeedback feedback = AiChatFeedback.builder()
                .sessionId(session.getId())
                .userId(userId)
                .result(feedbackDTO.getResult())
                .createTime(now)
                .updateTime(now)
                .build();
        try {
            feedbackMapper.upsert(feedback, feedbackDTO.getResult().getValue());
        } catch (DataAccessException ex) {
            throw new BaseException(MessageConstant.AI_CHAT_FEEDBACK_SAVE_FAILED, ex);
        }
    }
}
