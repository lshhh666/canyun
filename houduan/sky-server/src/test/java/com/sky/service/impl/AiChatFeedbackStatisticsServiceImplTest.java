package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.mapper.AiChatFeedbackMapper;
import com.sky.vo.AiChatFeedbackStatisticsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatFeedbackStatisticsServiceImplTest {

    @Mock
    private AiChatFeedbackMapper feedbackMapper;

    private AiChatFeedbackStatisticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiChatFeedbackStatisticsServiceImpl(feedbackMapper);
    }

    @Test
    void shouldCalculateRateAndUseLeftClosedRightOpenDateRange() {
        LocalDate begin = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 10);
        LocalDateTime beginTime = begin.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        when(feedbackMapper.statistics(beginTime, endExclusive))
                .thenReturn(AiChatFeedbackStatisticsVO.builder()
                        .helpfulCount(80L)
                        .unsolvedCount(20L)
                        .totalCount(100L)
                        .build());

        AiChatFeedbackStatisticsVO result = service.statistics(begin, end);

        assertThat(result.getHelpfulCount()).isEqualTo(80L);
        assertThat(result.getUnsolvedCount()).isEqualTo(20L);
        assertThat(result.getTotalCount()).isEqualTo(100L);
        assertThat(result.getResolutionRate())
                .isEqualByComparingTo(new BigDecimal("80.00"));
        verify(feedbackMapper).statistics(beginTime, endExclusive);
    }

    @Test
    void shouldReturnZeroRateWhenThereIsNoFeedback() {
        when(feedbackMapper.statistics(null, null))
                .thenReturn(AiChatFeedbackStatisticsVO.builder()
                        .helpfulCount(0L)
                        .unsolvedCount(0L)
                        .totalCount(0L)
                        .build());

        AiChatFeedbackStatisticsVO result = service.statistics(null, null);

        assertThat(result.getResolutionRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldSupportSingleSidedDateRange() {
        LocalDate end = LocalDate.of(2026, 9, 10);
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        when(feedbackMapper.statistics(null, endExclusive))
                .thenReturn(AiChatFeedbackStatisticsVO.builder()
                        .helpfulCount(1L)
                        .unsolvedCount(2L)
                        .totalCount(3L)
                        .build());

        AiChatFeedbackStatisticsVO result = service.statistics(null, end);

        assertThat(result.getResolutionRate())
                .isEqualByComparingTo(new BigDecimal("33.33"));
        verify(feedbackMapper).statistics(null, endExclusive);
    }

    @Test
    void shouldRejectReversedDateRangeBeforeQueryingDatabase() {
        LocalDate begin = LocalDate.of(2026, 9, 11);
        LocalDate end = LocalDate.of(2026, 9, 10);

        assertThatThrownBy(() -> service.statistics(begin, end))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_FEEDBACK_DATE_RANGE_INVALID);

        verify(feedbackMapper, never()).statistics(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectMaximumEndDateWithoutOverflow() {
        assertThatThrownBy(() -> service.statistics(null, LocalDate.MAX))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_CHAT_FEEDBACK_DATE_RANGE_INVALID);

        verify(feedbackMapper, never()).statistics(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
