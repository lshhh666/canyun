package com.cloudmeal.service;

import com.cloudmeal.vo.AiChatFeedbackStatisticsVO;

import java.time.LocalDate;

/** 管理端AI客服评价统计服务。 */
public interface AiChatFeedbackStatisticsService {

    AiChatFeedbackStatisticsVO statistics(LocalDate begin, LocalDate end);
}
