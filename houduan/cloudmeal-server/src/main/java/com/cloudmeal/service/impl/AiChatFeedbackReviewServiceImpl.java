package com.cloudmeal.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.dto.AiChatFeedbackPageQueryDTO;
import com.cloudmeal.dto.AiFeedbackRetestSubmitDTO;
import com.cloudmeal.dto.AiFeedbackRetestReviewDTO;
import com.cloudmeal.entity.AiChatFeedback;
import com.cloudmeal.entity.AiFeedbackKnowledgeRelation;
import com.cloudmeal.entity.AiFeedbackRetest;
import com.cloudmeal.enums.AiChatFeedbackHandleStatus;
import com.cloudmeal.enums.AiChatFeedbackResult;
import com.cloudmeal.enums.AiFeedbackRetestExecutionStatus;
import com.cloudmeal.enums.AiFeedbackRetestReviewResult;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.context.BaseContext;
import com.cloudmeal.mapper.AiChatFeedbackMapper;
import com.cloudmeal.mapper.AiChatMessageMapper;
import com.cloudmeal.mapper.AiFeedbackKnowledgeRelationMapper;
import com.cloudmeal.mapper.AiFeedbackRetestMapper;
import com.cloudmeal.mapper.AiKnowledgeMapper;
import com.cloudmeal.properties.AiProperties;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.service.AiChatFeedbackReviewService;
import com.cloudmeal.vo.AiChatFeedbackPageVO;
import com.cloudmeal.vo.AiChatFeedbackTurnVO;
import com.cloudmeal.vo.AiFeedbackRetestSubmitVO;
import com.cloudmeal.vo.AiFeedbackRetestDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理端AI客服未解决评价复核实现。 */
@Service
@RequiredArgsConstructor
public class AiChatFeedbackReviewServiceImpl
        implements AiChatFeedbackReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RETEST_QUESTION_LENGTH = 100;
    private static final int MAX_RETEST_KNOWLEDGE_COUNT = 10;
    private static final int MAX_KNOWLEDGE_KEY_LENGTH = 64;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    private final AiChatFeedbackMapper feedbackMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiFeedbackKnowledgeRelationMapper relationMapper;
    private final AiFeedbackRetestMapper retestMapper;
    private final AiKnowledgeMapper knowledgeMapper;
    private final AiProperties aiProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResult pageUnsolved(AiChatFeedbackPageQueryDTO queryDTO) {
        validate(queryDTO);
        if (LocalDate.MAX.equals(queryDTO.getEnd())) {
            throw new BaseException(MessageConstant.AI_CHAT_FEEDBACK_DATE_RANGE_INVALID);
        }

        LocalDateTime beginTime = queryDTO.getBegin() == null
                ? null : queryDTO.getBegin().atStartOfDay();
        LocalDateTime endExclusive = queryDTO.getEnd() == null
                ? null : queryDTO.getEnd().plusDays(1).atStartOfDay();

        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        Page<AiChatFeedback> feedbackPage =
                feedbackMapper.pageUnsolved(beginTime, endExclusive);
        if (feedbackPage == null || feedbackPage.isEmpty()) {
            long total = feedbackPage == null ? 0L : feedbackPage.getTotal();
            return new PageResult(total, Collections.emptyList());
        }

        List<Long> sessionIds = feedbackPage.getResult().stream()
                .map(AiChatFeedback::getSessionId)
                .collect(Collectors.toList());
        List<AiChatFeedbackTurnVO> turns =
                messageMapper.selectLatestTurnsBySessionIds(sessionIds);
        Map<Long, AiChatFeedbackTurnVO> turnBySessionId =
                (turns == null ? Collections.<AiChatFeedbackTurnVO>emptyList()
                        : turns).stream()
                        .collect(Collectors.toMap(
                                AiChatFeedbackTurnVO::getSessionId,
                                Function.identity(),
                                (first, ignored) -> first));

        List<AiChatFeedbackPageVO> records = feedbackPage.getResult().stream()
                .map(feedback -> toPageVO(
                        feedback, turnBySessionId.get(feedback.getSessionId())))
                .collect(Collectors.toList());
        return new PageResult(feedbackPage.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiFeedbackRetestSubmitVO submitRetest(
            Long feedbackId, AiFeedbackRetestSubmitDTO submitDTO) {
        String question = normalizeQuestion(feedbackId, submitDTO);
        List<String> knowledgeKeys = normalizeKnowledgeKeys(submitDTO);
        Long operatorId = BaseContext.getCurrentId();
        if (operatorId == null || operatorId <= 0) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        // 所有复测创建都先锁反馈行；同一反馈的并发请求由数据库串行处理。
        AiChatFeedback feedback = feedbackMapper.selectByIdForUpdate(feedbackId);
        if (feedback == null || feedback.getResult() != AiChatFeedbackResult.UNSOLVED) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_UNAVAILABLE);
        }
        if (feedback.getHandleStatus() == AiChatFeedbackHandleStatus.HANDLED) {
            return AiFeedbackRetestSubmitVO.builder()
                    .feedbackId(feedbackId)
                    .handleStatus(AiChatFeedbackHandleStatus.HANDLED)
                    .message(MessageConstant.AI_FEEDBACK_ALREADY_HANDLED)
                    .build();
        }

        AiFeedbackRetest unfinished =
                retestMapper.selectUnfinishedByFeedbackIdForUpdate(feedbackId);
        if (unfinished != null) {
            return existingRetestResult(feedback, unfinished);
        }

        int readyCount = knowledgeMapper.countReadyCurrentByKeys(
                knowledgeKeys, aiProperties.getEmbeddingModel(),
                aiProperties.getEmbeddingDimensions());
        if (readyCount != knowledgeKeys.size()) {
            throw new BaseException(
                    MessageConstant.AI_FEEDBACK_RETEST_KNOWLEDGE_UNAVAILABLE);
        }

        relationMapper.deleteByFeedbackId(feedbackId);
        for (String knowledgeKey : knowledgeKeys) {
            AiFeedbackKnowledgeRelation relation =
                    AiFeedbackKnowledgeRelation.builder()
                            .feedbackId(feedbackId)
                            .knowledgeKey(knowledgeKey)
                            .operatorId(operatorId)
                            .createTime(now)
                            .build();
            if (relationMapper.insert(relation) != 1) {
                throw new BaseException(
                        MessageConstant.AI_FEEDBACK_RETEST_SAVE_FAILED);
            }
        }

        AiFeedbackRetest retest = AiFeedbackRetest.builder()
                .feedbackId(feedbackId)
                .question(question)
                .executionStatus(AiFeedbackRetestExecutionStatus.PENDING)
                .retryCount(0)
                .maxRetryCount(DEFAULT_MAX_RETRY_COUNT)
                .nextRetryTime(now)
                .initiatorId(operatorId)
                .createTime(now)
                .updateTime(now)
                .build();
        if (retestMapper.insert(retest) != 1
                || feedbackMapper.markPendingRetest(feedbackId, now) != 1) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_SAVE_FAILED);
        }

        return AiFeedbackRetestSubmitVO.builder()
                .feedbackId(feedbackId)
                .retestId(retest.getId())
                .handleStatus(AiChatFeedbackHandleStatus.PENDING_RETEST)
                .executionStatus(AiFeedbackRetestExecutionStatus.PENDING)
                .message(MessageConstant.AI_FEEDBACK_RETEST_QUEUED)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AiFeedbackRetestDetailVO getLatestRetest(Long feedbackId) {
        AiChatFeedback feedback = requireFeedback(feedbackId, false);
        AiFeedbackRetest retest =
                retestMapper.selectLatestByFeedbackId(feedbackId);
        if (retest == null) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_NOT_FOUND);
        }
        return toRetestDetail(feedback, retest, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiFeedbackRetestDetailVO reviewRetest(
            Long feedbackId, Long retestId,
            AiFeedbackRetestReviewDTO reviewDTO) {
        if (feedbackId == null || feedbackId <= 0
                || retestId == null || retestId <= 0
                || reviewDTO == null || reviewDTO.getReviewResult() == null) {
            throw new BaseException(
                    MessageConstant.AI_FEEDBACK_RETEST_REVIEW_INVALID);
        }
        Long reviewerId = BaseContext.getCurrentId();
        if (reviewerId == null || reviewerId <= 0) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        // 统一按反馈行、复测行的顺序加锁，避免并发确认和发起新复测互相死锁。
        AiChatFeedback feedback = requireFeedback(feedbackId, true);
        AiFeedbackRetest retest = retestMapper.selectByIdForUpdate(retestId);
        if (retest == null || !feedbackId.equals(retest.getFeedbackId())) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_NOT_FOUND);
        }
        if (retest.getReviewResult() != null) {
            return toRetestDetail(feedback, retest,
                    MessageConstant.AI_FEEDBACK_RETEST_ALREADY_REVIEWED);
        }
        if (retest.getExecutionStatus()
                != AiFeedbackRetestExecutionStatus.SUCCEEDED) {
            throw new BaseException(
                    MessageConstant.AI_FEEDBACK_RETEST_NOT_REVIEWABLE);
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        AiFeedbackRetestReviewResult reviewResult =
                reviewDTO.getReviewResult();
        if (retestMapper.markReviewed(
                retestId, reviewResult.getValue(), reviewerId, now) != 1) {
            throw new BaseException(
                    MessageConstant.AI_FEEDBACK_RETEST_REVIEW_FAILED);
        }
        if (reviewResult == AiFeedbackRetestReviewResult.CORRECT
                && feedbackMapper.markHandled(
                feedbackId, reviewerId, now) != 1) {
            throw new BaseException(
                    MessageConstant.AI_FEEDBACK_RETEST_REVIEW_FAILED);
        }

        retest.setReviewResult(reviewResult);
        retest.setReviewerId(reviewerId);
        retest.setReviewTime(now);
        retest.setUpdateTime(now);
        if (reviewResult == AiFeedbackRetestReviewResult.CORRECT) {
            feedback.setHandleStatus(AiChatFeedbackHandleStatus.HANDLED);
            feedback.setHandlerId(reviewerId);
            feedback.setHandleTime(now);
            feedback.setUpdateTime(now);
        }
        String message = reviewResult == AiFeedbackRetestReviewResult.CORRECT
                ? MessageConstant.AI_FEEDBACK_RETEST_CONFIRMED_CORRECT
                : MessageConstant.AI_FEEDBACK_RETEST_CONFIRMED_INCORRECT;
        return toRetestDetail(feedback, retest, message);
    }

    private void validate(AiChatFeedbackPageQueryDTO queryDTO) {
        if (queryDTO == null || queryDTO.getPage() == null
                || queryDTO.getPageSize() == null || queryDTO.getPage() < 1
                || queryDTO.getPageSize() < 1
                || queryDTO.getPageSize() > MAX_PAGE_SIZE) {
            throw new BaseException(MessageConstant.AI_CHAT_FEEDBACK_PAGE_INVALID);
        }
        LocalDate begin = queryDTO.getBegin();
        LocalDate end = queryDTO.getEnd();
        if (begin != null && end != null && begin.isAfter(end)) {
            throw new BaseException(
                    MessageConstant.AI_CHAT_FEEDBACK_DATE_RANGE_INVALID);
        }
    }

    private AiChatFeedback requireFeedback(Long feedbackId, boolean forUpdate) {
        if (feedbackId == null || feedbackId <= 0) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_INVALID);
        }
        AiChatFeedback feedback = forUpdate
                ? feedbackMapper.selectByIdForUpdate(feedbackId)
                : feedbackMapper.selectById(feedbackId);
        if (feedback == null
                || feedback.getResult() != AiChatFeedbackResult.UNSOLVED) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_UNAVAILABLE);
        }
        return feedback;
    }

    private AiFeedbackRetestDetailVO toRetestDetail(
            AiChatFeedback feedback, AiFeedbackRetest retest,
            String message) {
        AiFeedbackRetestExecutionStatus executionStatus =
                retest.getExecutionStatus();
        AiFeedbackRetestReviewResult reviewResult = retest.getReviewResult();
        AiChatFeedbackHandleStatus handleStatus = feedback.getHandleStatus();
        return AiFeedbackRetestDetailVO.builder()
                .feedbackId(feedback.getId())
                .retestId(retest.getId())
                .question(retest.getQuestion())
                .answer(retest.getAnswer())
                .usedKnowledgeIds(retest.getUsedKnowledgeIds())
                .executionStatus(executionStatus)
                .executionStatusDesc(executionStatus == null
                        ? null : executionStatus.getDesc())
                .reviewResult(reviewResult)
                .reviewResultDesc(reviewResult == null
                        ? null : reviewResult.getDesc())
                .retryCount(retest.getRetryCount())
                .maxRetryCount(retest.getMaxRetryCount())
                .nextRetryTime(retest.getNextRetryTime())
                .lastError(retest.getLastError())
                .handleStatus(handleStatus)
                .handleStatusDesc(handleStatus == null
                        ? null : handleStatus.getDesc())
                .updateTime(retest.getUpdateTime())
                .message(message)
                .build();
    }

    private AiChatFeedbackPageVO toPageVO(
            AiChatFeedback feedback, AiChatFeedbackTurnVO turn) {
        return AiChatFeedbackPageVO.builder()
                .feedbackId(feedback.getId())
                .sessionId(feedback.getSessionId())
                .userId(feedback.getUserId())
                .userQuestion(turn == null ? null : turn.getUserQuestion())
                .aiAnswer(turn == null ? null : turn.getAiAnswer())
                .handleStatus(feedback.getHandleStatus())
                .handleStatusDesc(feedback.getHandleStatus() == null
                        ? AiChatFeedbackHandleStatus.PENDING.getDesc()
                        : feedback.getHandleStatus().getDesc())
                .feedbackTime(feedback.getUpdateTime())
                .build();
    }

    private String normalizeQuestion(
            Long feedbackId, AiFeedbackRetestSubmitDTO submitDTO) {
        if (feedbackId == null || feedbackId <= 0 || submitDTO == null
                || !StringUtils.hasText(submitDTO.getQuestion())) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_INVALID);
        }
        String question = submitDTO.getQuestion().trim();
        if (question.length() > MAX_RETEST_QUESTION_LENGTH) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_INVALID);
        }
        return question;
    }

    private List<String> normalizeKnowledgeKeys(
            AiFeedbackRetestSubmitDTO submitDTO) {
        if (submitDTO == null || submitDTO.getKnowledgeKeys() == null
                || submitDTO.getKnowledgeKeys().isEmpty()
                || submitDTO.getKnowledgeKeys().size()
                > MAX_RETEST_KNOWLEDGE_COUNT) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_INVALID);
        }
        Set<String> uniqueKeys = new LinkedHashSet<>();
        for (String key : submitDTO.getKnowledgeKeys()) {
            if (!StringUtils.hasText(key)) {
                throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_INVALID);
            }
            String normalized = key.trim();
            if (normalized.length() > MAX_KNOWLEDGE_KEY_LENGTH) {
                throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_INVALID);
            }
            uniqueKeys.add(normalized);
        }
        if (uniqueKeys.isEmpty()
                || uniqueKeys.size() > MAX_RETEST_KNOWLEDGE_COUNT) {
            throw new BaseException(MessageConstant.AI_FEEDBACK_RETEST_INVALID);
        }
        return new ArrayList<>(uniqueKeys);
    }

    private AiFeedbackRetestSubmitVO existingRetestResult(
            AiChatFeedback feedback, AiFeedbackRetest retest) {
        String message;
        if (retest.getExecutionStatus()
                == AiFeedbackRetestExecutionStatus.PROCESSING) {
            message = MessageConstant.AI_FEEDBACK_RETEST_PROCESSING;
        } else if (retest.getExecutionStatus()
                == AiFeedbackRetestExecutionStatus.SUCCEEDED) {
            message = MessageConstant.AI_FEEDBACK_RETEST_REVIEW_PENDING;
        } else {
            message = MessageConstant.AI_FEEDBACK_RETEST_QUEUED;
        }
        return AiFeedbackRetestSubmitVO.builder()
                .feedbackId(feedback.getId())
                .retestId(retest.getId())
                .handleStatus(feedback.getHandleStatus())
                .executionStatus(retest.getExecutionStatus())
                .message(message)
                .build();
    }
}
