package com.cloudmeal.controller.admin;

import com.cloudmeal.dto.AiChatFeedbackPageQueryDTO;
import com.cloudmeal.dto.AiFeedbackRetestSubmitDTO;
import com.cloudmeal.dto.AiFeedbackRetestReviewDTO;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.result.Result;
import com.cloudmeal.service.AiChatFeedbackReviewService;
import com.cloudmeal.service.AiChatFeedbackStatisticsService;
import com.cloudmeal.vo.AiChatFeedbackStatisticsVO;
import com.cloudmeal.vo.AiFeedbackRetestSubmitVO;
import com.cloudmeal.vo.AiFeedbackRetestDetailVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 管理端AI客服评价接口。 */
@RestController
@RequestMapping("/admin/ai/feedback")
@RequiredArgsConstructor
@Slf4j
@Api(tags = "管理端AI客服评价接口")
public class AiChatFeedbackAdminController {

    private final AiChatFeedbackStatisticsService statisticsService;
    private final AiChatFeedbackReviewService reviewService;

    @GetMapping("/statistics")
    @ApiOperation("查询AI客服评价统计")
    public Result<AiChatFeedbackStatisticsVO> statistics(
            @RequestParam(value = "begin", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("查询AI客服评价统计，begin={}，end={}", begin, end);
        return Result.success(statisticsService.statistics(begin, end));
    }

    @GetMapping("/unsolved/page")
    @ApiOperation("分页查询未解决的AI客服评价")
    public Result<PageResult> pageUnsolved(
            AiChatFeedbackPageQueryDTO queryDTO) {
        log.info("分页查询未解决的AI客服评价，query={}", queryDTO);
        return Result.success(reviewService.pageUnsolved(queryDTO));
    }

    @PostMapping("/{feedbackId}/retest")
    @ApiOperation("关联知识并发起AI客服复测")
    public Result<AiFeedbackRetestSubmitVO> submitRetest(
            @PathVariable Long feedbackId,
            @RequestBody AiFeedbackRetestSubmitDTO submitDTO) {
        log.info("发起AI客服评价复测，feedbackId={}", feedbackId);
        return Result.success(reviewService.submitRetest(feedbackId, submitDTO));
    }

    @GetMapping("/{feedbackId}/retest/latest")
    @ApiOperation("查询AI客服评价的最新复测结果")
    public Result<AiFeedbackRetestDetailVO> getLatestRetest(
            @PathVariable Long feedbackId) {
        return Result.success(reviewService.getLatestRetest(feedbackId));
    }

    @PostMapping("/{feedbackId}/retest/{retestId}/review")
    @ApiOperation("人工确认AI客服复测结果")
    public Result<AiFeedbackRetestDetailVO> reviewRetest(
            @PathVariable Long feedbackId,
            @PathVariable Long retestId,
            @RequestBody AiFeedbackRetestReviewDTO reviewDTO) {
        log.info("人工确认AI客服复测，feedbackId={}，retestId={}",
                feedbackId, retestId);
        return Result.success(reviewService.reviewRetest(
                feedbackId, retestId, reviewDTO));
    }
}
