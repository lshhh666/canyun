package com.cloudmeal.service.impl;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.mapper.AiChatFeedbackMapper;
import com.cloudmeal.service.AiChatFeedbackStatisticsService;
import com.cloudmeal.vo.AiChatFeedbackStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 管理端AI客服评价统计实现。 */
@Service
@RequiredArgsConstructor
public class AiChatFeedbackStatisticsServiceImpl
        implements AiChatFeedbackStatisticsService {

    private static final int RATE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final AiChatFeedbackMapper feedbackMapper;

    @Override
    @Transactional(readOnly = true)
    public AiChatFeedbackStatisticsVO statistics(LocalDate begin, LocalDate end) {
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new BaseException(MessageConstant.AI_CHAT_FEEDBACK_DATE_RANGE_INVALID);
        }
        if (LocalDate.MAX.equals(end)) {
            throw new BaseException(MessageConstant.AI_CHAT_FEEDBACK_DATE_RANGE_INVALID);
        }

        LocalDateTime beginTime = begin == null ? null : begin.atStartOfDay();
        LocalDateTime endExclusive = end == null ? null : end.plusDays(1).atStartOfDay();
        AiChatFeedbackStatisticsVO aggregate =
                feedbackMapper.statistics(beginTime, endExclusive);

        long helpfulCount = valueOrZero(
                aggregate == null ? null : aggregate.getHelpfulCount());
        long unsolvedCount = valueOrZero(
                aggregate == null ? null : aggregate.getUnsolvedCount());
        long totalCount = valueOrZero(
                aggregate == null ? null : aggregate.getTotalCount());

        BigDecimal resolutionRate = totalCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(helpfulCount)
                        .multiply(ONE_HUNDRED)
                        .divide(BigDecimal.valueOf(totalCount), RATE_SCALE,
                                RoundingMode.HALF_UP);

        return AiChatFeedbackStatisticsVO.builder()
                .helpfulCount(helpfulCount)
                .unsolvedCount(unsolvedCount)
                .totalCount(totalCount)
                .resolutionRate(resolutionRate)
                .build();
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
