package com.cloudmeal.controller.admin;

import com.cloudmeal.dto.AiChatFeedbackPageQueryDTO;
import com.cloudmeal.dto.AiFeedbackRetestSubmitDTO;
import com.cloudmeal.dto.AiFeedbackRetestReviewDTO;
import com.cloudmeal.enums.AiChatFeedbackHandleStatus;
import com.cloudmeal.enums.AiFeedbackRetestExecutionStatus;
import com.cloudmeal.enums.AiFeedbackRetestReviewResult;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.service.AiChatFeedbackReviewService;
import com.cloudmeal.service.AiChatFeedbackStatisticsService;
import com.cloudmeal.vo.AiChatFeedbackPageVO;
import com.cloudmeal.vo.AiChatFeedbackStatisticsVO;
import com.cloudmeal.vo.AiFeedbackRetestSubmitVO;
import com.cloudmeal.vo.AiFeedbackRetestDetailVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiChatFeedbackAdminControllerTest {

    @Test
    void shouldAcceptOptionalDateRangeAndReturnStatistics() throws Exception {
        AiChatFeedbackStatisticsService service =
                Mockito.mock(AiChatFeedbackStatisticsService.class);
        AiChatFeedbackReviewService reviewService =
                Mockito.mock(AiChatFeedbackReviewService.class);
        Mockito.when(service.statistics(
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10)))
                .thenReturn(AiChatFeedbackStatisticsVO.builder()
                        .helpfulCount(8L)
                        .unsolvedCount(2L)
                        .totalCount(10L)
                        .resolutionRate(new BigDecimal("80.00"))
                        .build());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatFeedbackAdminController(
                        service, reviewService))
                .build();

        mockMvc.perform(get("/admin/ai/feedback/statistics")
                        .param("begin", "2026-09-01")
                        .param("end", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.helpfulCount").value(8))
                .andExpect(jsonPath("$.data.unsolvedCount").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.resolutionRate").value(80.0));

        verify(service).statistics(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10));
    }

    @Test
    void shouldAllowBothDatesToBeAbsent() throws Exception {
        AiChatFeedbackStatisticsService service =
                Mockito.mock(AiChatFeedbackStatisticsService.class);
        AiChatFeedbackReviewService reviewService =
                Mockito.mock(AiChatFeedbackReviewService.class);
        Mockito.when(service.statistics(null, null))
                .thenReturn(AiChatFeedbackStatisticsVO.builder()
                        .helpfulCount(0L)
                        .unsolvedCount(0L)
                        .totalCount(0L)
                        .resolutionRate(BigDecimal.ZERO)
                        .build());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatFeedbackAdminController(
                        service, reviewService))
                .build();

        mockMvc.perform(get("/admin/ai/feedback/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.resolutionRate").value(0));

        verify(service).statistics(null, null);
    }

    @Test
    void shouldBindUnsolvedPageQueryAndReturnLatestTurn() throws Exception {
        AiChatFeedbackStatisticsService statisticsService =
                Mockito.mock(AiChatFeedbackStatisticsService.class);
        AiChatFeedbackReviewService reviewService =
                Mockito.mock(AiChatFeedbackReviewService.class);
        Mockito.when(reviewService.pageUnsolved(Mockito.any()))
                .thenReturn(new PageResult(1L, Collections.singletonList(
                        AiChatFeedbackPageVO.builder()
                                .feedbackId(8L)
                                .sessionId(20L)
                                .userId(30L)
                                .userQuestion("question")
                                .aiAnswer("answer")
                                .build())));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatFeedbackAdminController(
                        statisticsService, reviewService))
                .build();

        mockMvc.perform(get("/admin/ai/feedback/unsolved/page")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("begin", "2026-09-01")
                        .param("end", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].sessionId").value(20))
                .andExpect(jsonPath("$.data.records[0].userQuestion")
                        .value("question"))
                .andExpect(jsonPath("$.data.records[0].aiAnswer")
                        .value("answer"));

        ArgumentCaptor<AiChatFeedbackPageQueryDTO> queryCaptor =
                ArgumentCaptor.forClass(AiChatFeedbackPageQueryDTO.class);
        verify(reviewService).pageUnsolved(queryCaptor.capture());
        AiChatFeedbackPageQueryDTO query = queryCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(query.getPage()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(query.getPageSize()).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(query.getBegin())
                .isEqualTo(LocalDate.of(2026, 9, 1));
        org.assertj.core.api.Assertions.assertThat(query.getEnd())
                .isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void shouldBindRetestRequestWithoutAcceptingOperatorId() throws Exception {
        AiChatFeedbackStatisticsService statisticsService =
                Mockito.mock(AiChatFeedbackStatisticsService.class);
        AiChatFeedbackReviewService reviewService =
                Mockito.mock(AiChatFeedbackReviewService.class);
        Mockito.when(reviewService.submitRetest(
                        Mockito.eq(8L), Mockito.any()))
                .thenReturn(AiFeedbackRetestSubmitVO.builder()
                        .feedbackId(8L)
                        .retestId(91L)
                        .handleStatus(AiChatFeedbackHandleStatus.PENDING_RETEST)
                        .executionStatus(AiFeedbackRetestExecutionStatus.PENDING)
                        .message("已提交，等待系统复测")
                        .build());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatFeedbackAdminController(
                        statisticsService, reviewService))
                .build();

        mockMvc.perform(post("/admin/ai/feedback/8/retest")
                        .contentType("application/json")
                        .content("{\"question\":\"问题\","
                                + "\"knowledgeKeys\":[\"ORDER_CANCEL\"],"
                                + "\"operatorId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.retestId").value(91))
                .andExpect(jsonPath("$.data.executionStatus").value("PENDING"));

        ArgumentCaptor<AiFeedbackRetestSubmitDTO> captor =
                ArgumentCaptor.forClass(AiFeedbackRetestSubmitDTO.class);
        verify(reviewService).submitRetest(Mockito.eq(8L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getQuestion())
                .isEqualTo("问题");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getKnowledgeKeys())
                .containsExactly("ORDER_CANCEL");
    }

    @Test
    void shouldReturnLatestRetestDetails() throws Exception {
        AiChatFeedbackStatisticsService statisticsService =
                Mockito.mock(AiChatFeedbackStatisticsService.class);
        AiChatFeedbackReviewService reviewService =
                Mockito.mock(AiChatFeedbackReviewService.class);
        Mockito.when(reviewService.getLatestRetest(8L))
                .thenReturn(AiFeedbackRetestDetailVO.builder()
                        .feedbackId(8L)
                        .retestId(91L)
                        .question("问题")
                        .answer("回答")
                        .executionStatus(AiFeedbackRetestExecutionStatus.SUCCEEDED)
                        .executionStatusDesc("成功")
                        .build());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatFeedbackAdminController(
                        statisticsService, reviewService))
                .build();

        mockMvc.perform(get("/admin/ai/feedback/8/retest/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retestId").value(91))
                .andExpect(jsonPath("$.data.answer").value("回答"))
                .andExpect(jsonPath("$.data.executionStatus").value("SUCCEEDED"));
    }

    @Test
    void shouldBindReviewDecisionWithoutAcceptingReviewerId() throws Exception {
        AiChatFeedbackStatisticsService statisticsService =
                Mockito.mock(AiChatFeedbackStatisticsService.class);
        AiChatFeedbackReviewService reviewService =
                Mockito.mock(AiChatFeedbackReviewService.class);
        Mockito.when(reviewService.reviewRetest(
                        Mockito.eq(8L), Mockito.eq(91L), Mockito.any()))
                .thenReturn(AiFeedbackRetestDetailVO.builder()
                        .feedbackId(8L)
                        .retestId(91L)
                        .reviewResult(AiFeedbackRetestReviewResult.CORRECT)
                        .handleStatus(AiChatFeedbackHandleStatus.HANDLED)
                        .message("已确认回答正确，反馈已处理")
                        .build());
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatFeedbackAdminController(
                        statisticsService, reviewService))
                .build();

        mockMvc.perform(post("/admin/ai/feedback/8/retest/91/review")
                        .contentType("application/json")
                        .content("{\"reviewResult\":\"CORRECT\",\"reviewerId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewResult").value("CORRECT"))
                .andExpect(jsonPath("$.data.handleStatus").value("HANDLED"));

        ArgumentCaptor<AiFeedbackRetestReviewDTO> captor =
                ArgumentCaptor.forClass(AiFeedbackRetestReviewDTO.class);
        verify(reviewService).reviewRetest(
                Mockito.eq(8L), Mockito.eq(91L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(
                captor.getValue().getReviewResult())
                .isEqualTo(AiFeedbackRetestReviewResult.CORRECT);
    }
}
