package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.dto.AiKnowledgeCreateDTO;
import com.sky.dto.AiKnowledgePageQueryDTO;
import com.sky.dto.AiKnowledgeUpdateDTO;
import com.sky.entity.AiKnowledge;
import com.sky.enums.AiKnowledgeCategory;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import com.sky.enums.AiKnowledgeStatus;
import com.sky.exception.BaseException;
import com.sky.mapper.AiKnowledgeMapper;
import com.sky.result.PageResult;
import com.sky.service.AiKnowledgePersistenceService;
import com.sky.service.AiKnowledgeService;
import com.sky.service.model.AiKnowledgeRetryOutcome;
import com.sky.vo.AiKnowledgePageVO;
import com.sky.vo.AiKnowledgeSaveVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 负责知识校验并提交待生成向量的知识版本，不调用外部模型。
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeServiceImpl implements AiKnowledgeService {

    private static final int FIRST_VERSION = 1;
    private static final int MAX_KNOWLEDGE_KEY_LENGTH = 64;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final AiKnowledgePersistenceService persistenceService;
    private final AiKnowledgeMapper knowledgeMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult pageCurrent(AiKnowledgePageQueryDTO queryDTO) {
        validatePageQuery(queryDTO);
        String keyword = StringUtils.hasText(queryDTO.getKeyword())
                ? queryDTO.getKeyword().trim() : null;

        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        Page<AiKnowledge> page = knowledgeMapper.pageCurrent(
                keyword, queryDTO.getCategory(), queryDTO.getEmbeddingStatus());
        if (page == null || page.isEmpty()) {
            return new PageResult(page == null ? 0L : page.getTotal(),
                    Collections.emptyList());
        }
        List<AiKnowledgePageVO> records = page.getResult().stream()
                .map(this::toPageVO)
                .collect(Collectors.toList());
        return new PageResult(page.getTotal(), records);
    }

    @Override
    public Long create(AiKnowledgeCreateDTO dto) {
        validate(dto);

        String knowledgeKey = dto.getKnowledgeKey().trim();
        String title = dto.getTitle().trim();
        String content = dto.getContent().trim();

        LocalDateTime now = LocalDateTime.now();
        AiKnowledge knowledge = AiKnowledge.builder()
                .knowledgeKey(knowledgeKey)
                .title(title)
                .category(dto.getCategory())
                .content(content)
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .versionNo(FIRST_VERSION)
                .status(AiKnowledgeStatus.ENABLED)
                .createTime(now)
                .updateTime(now)
                .build();
        return persistenceService.saveFirstVersion(knowledge);
    }

    @Override
    public Long update(Long id, AiKnowledgeUpdateDTO dto) {
        validateId(id);
        validate(dto);

        String title = dto.getTitle().trim();
        String content = dto.getContent().trim();

        AiKnowledge next = AiKnowledge.builder()
                .title(title)
                .category(dto.getCategory())
                .content(content)
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .build();
        return persistenceService.saveNextVersion(id, next);
    }

    @Override
    public AiKnowledgeSaveVO retryEmbedding(Long id) {
        validateId(id);
        AiKnowledgeRetryOutcome outcome = persistenceService.retryFailedEmbedding(id);
        AiKnowledgeEmbeddingStatus status = outcome == AiKnowledgeRetryOutcome.READY
                ? AiKnowledgeEmbeddingStatus.READY : AiKnowledgeEmbeddingStatus.PENDING;
        String message;
        if (outcome == AiKnowledgeRetryOutcome.READY) {
            message = MessageConstant.AI_KNOWLEDGE_EMBEDDING_READY;
        } else if (outcome == AiKnowledgeRetryOutcome.PROCESSING) {
            message = MessageConstant.AI_KNOWLEDGE_EMBEDDING_PROCESSING;
        } else {
            message = MessageConstant.AI_KNOWLEDGE_EMBEDDING_RETRY_QUEUED;
        }
        return AiKnowledgeSaveVO.builder()
                .knowledgeId(id)
                .embeddingStatus(status)
                .message(message)
                .build();
    }

    private void validate(AiKnowledgeCreateDTO dto) {
        if (dto == null
                || !validText(dto.getKnowledgeKey(), MAX_KNOWLEDGE_KEY_LENGTH)
                || !validKnowledgeContent(dto.getTitle(), dto.getCategory(), dto.getContent())) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_INVALID);
        }
    }

    private void validatePageQuery(AiKnowledgePageQueryDTO queryDTO) {
        if (queryDTO == null
                || queryDTO.getPage() == null
                || queryDTO.getPageSize() == null
                || queryDTO.getPage() < 1
                || queryDTO.getPageSize() < 1
                || queryDTO.getPageSize() > MAX_PAGE_SIZE
                || (queryDTO.getKeyword() != null
                && queryDTO.getKeyword().trim().codePointCount(
                0, queryDTO.getKeyword().trim().length()) > MAX_KEYWORD_LENGTH)) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_PAGE_INVALID);
        }
    }

    private AiKnowledgePageVO toPageVO(AiKnowledge knowledge) {
        return AiKnowledgePageVO.builder()
                .id(knowledge.getId())
                .knowledgeKey(knowledge.getKnowledgeKey())
                .title(knowledge.getTitle())
                .category(knowledge.getCategory())
                .categoryDesc(knowledge.getCategory() == null
                        ? null : knowledge.getCategory().getDesc())
                .content(knowledge.getContent())
                .embeddingStatus(knowledge.getEmbeddingStatus())
                .embeddingStatusDesc(knowledge.getEmbeddingStatus() == null
                        ? null : knowledge.getEmbeddingStatus().getDesc())
                .versionNo(knowledge.getVersionNo())
                .createTime(knowledge.getCreateTime())
                .updateTime(knowledge.getUpdateTime())
                .build();
    }

    private void validate(AiKnowledgeUpdateDTO dto) {
        if (dto == null
                || !validKnowledgeContent(dto.getTitle(), dto.getCategory(), dto.getContent())) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_INVALID);
        }
    }

    private boolean validKnowledgeContent(String title,
                                          AiKnowledgeCategory category,
                                          String content) {
        return validText(title, MAX_TITLE_LENGTH)
                && validText(content, MAX_CONTENT_LENGTH)
                && category != null;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BaseException(MessageConstant.AI_KNOWLEDGE_UNAVAILABLE);
        }
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value)
                && value.trim().codePointCount(0, value.trim().length()) <= maxLength;
    }

}
