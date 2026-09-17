package com.cloudmeal.service;

import com.cloudmeal.dto.AiChatFeedbackDTO;

/** 保存当前登录用户对已结束AI客服会话的评价。 */
public interface AiChatFeedbackService {

    void submit(AiChatFeedbackDTO feedbackDTO);
}
