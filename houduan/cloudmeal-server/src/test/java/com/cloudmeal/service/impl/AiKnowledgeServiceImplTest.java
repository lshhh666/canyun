package com.cloudmeal.service.impl;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.dto.AiKnowledgeCreateDTO;
import com.cloudmeal.dto.AiKnowledgePageQueryDTO;
import com.cloudmeal.dto.AiKnowledgeUpdateDTO;
import com.cloudmeal.entity.AiKnowledge;
import com.cloudmeal.enums.AiKnowledgeCategory;
import com.cloudmeal.enums.AiKnowledgeEmbeddingStatus;
import com.cloudmeal.enums.AiKnowledgeStatus;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.mapper.AiKnowledgeMapper;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.service.AiKnowledgePersistenceService;
import com.cloudmeal.service.model.AiKnowledgeRetryOutcome;
import com.cloudmeal.vo.AiKnowledgePageVO;
import com.cloudmeal.vo.AiKnowledgeSaveVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKnowledgeServiceImplTest {

    @Mock
    private AiKnowledgePersistenceService persistenceService;
    @Mock
    private AiKnowledgeMapper knowledgeMapper;

    private AiKnowledgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiKnowledgeServiceImpl(persistenceService, knowledgeMapper);
    }

    @Test
    void shouldPageCurrentKnowledgeWithoutReturningEmbedding() {
        AiKnowledgePageQueryDTO query = new AiKnowledgePageQueryDTO();
        query.setPage(1);
        query.setPageSize(10);
        query.setKeyword(" 营业 ");
        query.setCategory(AiKnowledgeCategory.SHOP);
        query.setEmbeddingStatus(AiKnowledgeEmbeddingStatus.PENDING);
        AiKnowledge knowledge = AiKnowledge.builder()
                .id(88L)
                .knowledgeKey("SHOP_HOURS")
                .title("营业时间")
                .category(AiKnowledgeCategory.SHOP)
                .content("餐云每天09:00至21:00营业。")
                .embedding("[0.25,0.0]")
                .embeddingStatus(AiKnowledgeEmbeddingStatus.PENDING)
                .versionNo(2)
                .build();
        Page<AiKnowledge> page = new Page<>();
        page.add(knowledge);
        page.setTotal(1L);
        when(knowledgeMapper.pageCurrent(
                "营业", AiKnowledgeCategory.SHOP,
                AiKnowledgeEmbeddingStatus.PENDING)).thenReturn(page);

        try {
            PageResult result = service.pageCurrent(query);

            assertThat(result.getTotal()).isEqualTo(1L);
            AiKnowledgePageVO record = (AiKnowledgePageVO) result.getRecords().get(0);
            assertThat(record.getId()).isEqualTo(88L);
            assertThat(record.getCategoryDesc()).isEqualTo("门店规则");
            assertThat(record.getEmbeddingStatusDesc()).isEqualTo("待生成");
        } finally {
            PageHelper.clearPage();
        }
    }

    @Test
    void shouldRejectInvalidPageQuery() {
        AiKnowledgePageQueryDTO query = new AiKnowledgePageQueryDTO();
        query.setPage(0);
        query.setPageSize(10);

        assertThatThrownBy(() -> service.pageCurrent(query))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_KNOWLEDGE_PAGE_INVALID);

        verifyNoInteractions(knowledgeMapper);
    }

    @Test
    void shouldSavePendingKnowledgeWithoutCallingEmbeddingProvider() {
        when(persistenceService.saveFirstVersion(any(AiKnowledge.class))).thenReturn(88L);

        Long result = service.create(validDto());

        ArgumentCaptor<AiKnowledge> captor = ArgumentCaptor.forClass(AiKnowledge.class);
        verify(persistenceService).saveFirstVersion(captor.capture());
        AiKnowledge knowledge = captor.getValue();
        assertThat(result).isEqualTo(88L);
        assertThat(knowledge.getKnowledgeKey()).isEqualTo("SHOP_HOURS");
        assertThat(knowledge.getTitle()).isEqualTo("营业时间");
        assertThat(knowledge.getCategory()).isEqualTo(AiKnowledgeCategory.SHOP);
        assertThat(knowledge.getContent()).isEqualTo("餐云每天09:00至21:00营业。");
        assertThat(knowledge.getEmbedding()).isNull();
        assertThat(knowledge.getEmbeddingModel()).isNull();
        assertThat(knowledge.getEmbeddingDimensions()).isNull();
        assertThat(knowledge.getEmbeddingStatus()).isEqualTo(AiKnowledgeEmbeddingStatus.PENDING);
        assertThat(knowledge.getVersionNo()).isEqualTo(1);
        assertThat(knowledge.getStatus()).isEqualTo(AiKnowledgeStatus.ENABLED);
        assertThat(knowledge.getCreateTime()).isNotNull();
        assertThat(knowledge.getUpdateTime()).isNotNull();
    }

    @Test
    void shouldRejectInvalidKnowledgeBeforePersistence() {
        AiKnowledgeCreateDTO dto = validDto();
        dto.setContent("  ");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_KNOWLEDGE_INVALID);

        verifyNoInteractions(persistenceService);
    }

    @Test
    void shouldSavePendingNextVersionDraft() {
        when(persistenceService.saveNextVersion(eq(7L), any(AiKnowledge.class))).thenReturn(89L);

        Long result = service.update(7L, validUpdateDto());

        ArgumentCaptor<AiKnowledge> captor = ArgumentCaptor.forClass(AiKnowledge.class);
        verify(persistenceService).saveNextVersion(eq(7L), captor.capture());
        AiKnowledge next = captor.getValue();
        assertThat(result).isEqualTo(89L);
        assertThat(next.getKnowledgeKey()).isNull();
        assertThat(next.getVersionNo()).isNull();
        assertThat(next.getTitle()).isEqualTo("营业时间");
        assertThat(next.getContent()).isEqualTo("餐云每天09:00至22:00营业。");
        assertThat(next.getEmbedding()).isNull();
        assertThat(next.getEmbeddingStatus()).isEqualTo(AiKnowledgeEmbeddingStatus.PENDING);
    }

    @Test
    void shouldRejectInvalidSourceId() {
        assertThatThrownBy(() -> service.update(0L, validUpdateDto()))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_KNOWLEDGE_UNAVAILABLE);

        verifyNoInteractions(persistenceService);
    }

    @Test
    void shouldReturnQueuedManualRetryResult() {
        when(persistenceService.retryFailedEmbedding(88L))
                .thenReturn(AiKnowledgeRetryOutcome.QUEUED);

        AiKnowledgeSaveVO result = service.retryEmbedding(88L);

        assertThat(result.getKnowledgeId()).isEqualTo(88L);
        assertThat(result.getEmbeddingStatus())
                .isEqualTo(AiKnowledgeEmbeddingStatus.PENDING);
        assertThat(result.getMessage())
                .isEqualTo(MessageConstant.AI_KNOWLEDGE_EMBEDDING_RETRY_QUEUED);
    }

    @Test
    void shouldReturnProcessingForDuplicateManualRetry() {
        when(persistenceService.retryFailedEmbedding(88L))
                .thenReturn(AiKnowledgeRetryOutcome.PROCESSING);

        AiKnowledgeSaveVO result = service.retryEmbedding(88L);

        assertThat(result.getEmbeddingStatus())
                .isEqualTo(AiKnowledgeEmbeddingStatus.PENDING);
        assertThat(result.getMessage())
                .isEqualTo(MessageConstant.AI_KNOWLEDGE_EMBEDDING_PROCESSING);
    }

    @Test
    void shouldReturnReadyWithoutRetryingUsableKnowledge() {
        when(persistenceService.retryFailedEmbedding(88L))
                .thenReturn(AiKnowledgeRetryOutcome.READY);

        AiKnowledgeSaveVO result = service.retryEmbedding(88L);

        assertThat(result.getEmbeddingStatus())
                .isEqualTo(AiKnowledgeEmbeddingStatus.READY);
        assertThat(result.getMessage())
                .isEqualTo(MessageConstant.AI_KNOWLEDGE_EMBEDDING_READY);
    }

    @Test
    void shouldRejectInvalidManualRetryIdBeforePersistence() {
        assertThatThrownBy(() -> service.retryEmbedding(0L))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_KNOWLEDGE_UNAVAILABLE);

        verifyNoInteractions(persistenceService);
    }

    private AiKnowledgeCreateDTO validDto() {
        AiKnowledgeCreateDTO dto = new AiKnowledgeCreateDTO();
        dto.setKnowledgeKey(" SHOP_HOURS ");
        dto.setTitle(" 营业时间 ");
        dto.setCategory(AiKnowledgeCategory.SHOP);
        dto.setContent(" 餐云每天09:00至21:00营业。 ");
        return dto;
    }

    private AiKnowledgeUpdateDTO validUpdateDto() {
        AiKnowledgeUpdateDTO dto = new AiKnowledgeUpdateDTO();
        dto.setTitle(" 营业时间 ");
        dto.setCategory(AiKnowledgeCategory.SHOP);
        dto.setContent(" 餐云每天09:00至22:00营业。 ");
        return dto;
    }
}
