package com.cloudmeal.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cloudmeal.client.AiEmbeddingClient;
import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.entity.AiKnowledge;
import com.cloudmeal.enums.AiKnowledgeCategory;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.mapper.AiKnowledgeMapper;
import com.cloudmeal.properties.AiProperties;
import com.cloudmeal.service.model.AiKnowledgeMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKnowledgeRetrievalServiceImplTest {

    @Mock
    private AiEmbeddingClient embeddingClient;

    @Mock
    private AiKnowledgeMapper knowledgeMapper;

    private AiProperties properties;
    private AiKnowledgeRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEmbeddingModel("embedding-3");
        properties.setEmbeddingDimensions(2);
        properties.setRetrievalTopK(3);
        properties.setRetrievalMinScore(0.70D);
        service = new AiKnowledgeRetrievalServiceImpl(
                embeddingClient, knowledgeMapper, properties, new ObjectMapper());
    }

    @Test
    void shouldEmbedCompleteTrimmedQuestionAndUseCompatibleCandidates() {
        when(embeddingClient.embed("晚上十点还能点餐吗"))
                .thenReturn(Arrays.asList(1D, 0D));
        when(knowledgeMapper.selectEnabledByEmbeddingConfiguration("embedding-3", 2))
                .thenReturn(Arrays.asList(knowledge(10L, "[1,0]")));

        List<AiKnowledgeMatch> result = service.retrieve("  晚上十点还能点餐吗  ");

        assertThat(result).extracting(AiKnowledgeMatch::getId).containsExactly(10L);
        verify(embeddingClient).embed("晚上十点还能点餐吗");
        verify(knowledgeMapper).selectEnabledByEmbeddingConfiguration("embedding-3", 2);
    }

    @Test
    void shouldRejectBlankQuestionBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.retrieve("  "))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_MESSAGE_EMPTY);

        verifyNoInteractions(embeddingClient, knowledgeMapper);
    }

    @Test
    void shouldRejectNullQuestionBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.retrieve(null))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_MESSAGE_EMPTY);

        verifyNoInteractions(embeddingClient, knowledgeMapper);
    }

    @Test
    void shouldRejectInvalidConfigurationBeforeCallingDependencies() {
        properties.setRetrievalTopK(0);

        assertThatThrownBy(() -> service.retrieve("营业时间"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI知识检索配置无效");

        verifyNoInteractions(embeddingClient, knowledgeMapper);
    }

    @Test
    void shouldRejectEveryInvalidConfigurationValue() {
        assertInvalidConfiguration(properties -> properties.setRetrievalTopK(0));
        assertInvalidConfiguration(properties -> properties.setRetrievalTopK(-1));
        assertInvalidConfiguration(properties -> properties.setRetrievalMinScore(-1.01D));
        assertInvalidConfiguration(properties -> properties.setRetrievalMinScore(1.01D));
        assertInvalidConfiguration(properties -> properties.setRetrievalMinScore(Double.NaN));
        assertInvalidConfiguration(properties -> properties.setRetrievalMinScore(Double.POSITIVE_INFINITY));
        assertInvalidConfiguration(properties -> properties.setRetrievalMinScore(Double.NEGATIVE_INFINITY));
        assertInvalidConfiguration(properties -> properties.setEmbeddingModel("  "));
        assertInvalidConfiguration(properties -> properties.setEmbeddingDimensions(0));
        assertInvalidConfiguration(properties -> properties.setEmbeddingDimensions(-1));
    }

    @Test
    void shouldRankByCosineKeepInclusiveThresholdAndLimitToTopThree() {
        when(embeddingClient.embed("营业时间"))
                .thenReturn(Arrays.asList(1D, 0D));
        when(knowledgeMapper.selectEnabledByEmbeddingConfiguration("embedding-3", 2))
                .thenReturn(Arrays.asList(
                        knowledge(4L, "[8,6]"),
                        knowledge(5L, "[7,10]"),
                        knowledge(3L, "[1,0]"),
                        knowledge(7L, "[9,4]"),
                        knowledge(1L, "[0,1]")
                ));

        List<AiKnowledgeMatch> result = service.retrieve("营业时间");

        assertThat(result).extracting(AiKnowledgeMatch::getId)
                .containsExactly(3L, 7L, 4L);
        assertThat(result).extracting(AiKnowledgeMatch::getScore)
                .allMatch(score -> score >= 0.70D);
    }

    @Test
    void shouldKeepCandidateWhoseScoreEqualsMinimumThreshold() {
        properties.setRetrievalMinScore(0.60D);
        when(embeddingClient.embed("营业时间"))
                .thenReturn(Arrays.asList(1D, 0D));
        when(knowledgeMapper.selectEnabledByEmbeddingConfiguration("embedding-3", 2))
                .thenReturn(Arrays.asList(knowledge(6L, "[3,4]")));

        List<AiKnowledgeMatch> result = service.retrieve("营业时间");

        assertThat(result).extracting(AiKnowledgeMatch::getId).containsExactly(6L);
        assertThat(result.get(0).getScore()).isEqualTo(0.60D);
    }

    @Test
    void shouldSortEqualScoresByKnowledgeId() {
        when(embeddingClient.embed("营业时间"))
                .thenReturn(Arrays.asList(1D, 0D));
        when(knowledgeMapper.selectEnabledByEmbeddingConfiguration("embedding-3", 2))
                .thenReturn(Arrays.asList(
                        knowledge(9L, "[1,0]"),
                        knowledge(2L, "[1,0]"),
                        knowledge(5L, "[1,0]")
                ));

        List<AiKnowledgeMatch> result = service.retrieve("营业时间");

        assertThat(result).extracting(AiKnowledgeMatch::getId)
                .containsExactly(2L, 5L, 9L);
    }

    @Test
    void shouldClampFiniteNearCollinearCosineOvershootToOne() {
        properties.setEmbeddingDimensions(5);
        properties.setRetrievalMinScore(-1D);
        List<Double> question = Arrays.asList(
                1D, 0.9643367033422356D, 0.7380668114709755D,
                0.725512934535386D, 0.6486416265890862D);
        List<Double> candidate = Arrays.asList(
                1D, 0.9643367033422356D, 0.7380668114709757D,
                0.7255129345353856D, 0.6486416265890859D);
        assertThat(preClampCosine(question, candidate)).isGreaterThan(1D);
        when(embeddingClient.embed("近共线问题")).thenReturn(question);
        when(knowledgeMapper.selectEnabledByEmbeddingConfiguration("embedding-3", 5))
                .thenReturn(Arrays.asList(knowledge(11L, embeddingJson(candidate))));

        List<AiKnowledgeMatch> result = service.retrieve("近共线问题");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScore()).isEqualTo(1D);
    }

    @Test
    void shouldSkipMalformedZeroNormAndNonFiniteStoredEmbeddings() {
        when(embeddingClient.embed("营业时间"))
                .thenReturn(Arrays.asList(1D, 0D));
        when(knowledgeMapper.selectEnabledByEmbeddingConfiguration("embedding-3", 2))
                .thenReturn(Arrays.asList(
                        knowledge(1L, "not-json"),
                        knowledge(2L, "[0,0]"),
                        knowledge(3L, "[1e400,0]"),
                        knowledge(5L, "[1]"),
                        knowledge(4L, "[1,0]")
                ));

        List<AiKnowledgeMatch> result = service.retrieve("营业时间");

        assertThat(result).extracting(AiKnowledgeMatch::getId).containsExactly(4L);
    }

    @Test
    void shouldLogOnlyKnowledgeIdWhenSkippingCorruptStoredEmbedding() {
        Logger logger = (Logger) LoggerFactory.getLogger(AiKnowledgeRetrievalServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        when(embeddingClient.embed("营业时间"))
                .thenReturn(Arrays.asList(1D, 0D));
        when(knowledgeMapper.selectEnabledByEmbeddingConfiguration("embedding-3", 2))
                .thenReturn(Arrays.asList(knowledge(12L, "corrupt-embedding-secret",
                        "私密标题", "私密正文")));

        try {
            assertThat(service.retrieve("营业时间")).isEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("跳过无效知识向量，知识ID：12", "知识检索完成，最终命中数量：0")
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain("私密标题", "私密正文", "corrupt-embedding-secret"));
    }

    private void assertInvalidConfiguration(java.util.function.Consumer<AiProperties> invalidator) {
        AiProperties invalidProperties = new AiProperties();
        invalidProperties.setEmbeddingModel("embedding-3");
        invalidProperties.setEmbeddingDimensions(2);
        invalidProperties.setRetrievalTopK(3);
        invalidProperties.setRetrievalMinScore(0.70D);
        invalidator.accept(invalidProperties);
        AiKnowledgeRetrievalServiceImpl invalidService = new AiKnowledgeRetrievalServiceImpl(
                embeddingClient, knowledgeMapper, invalidProperties, new ObjectMapper());

        assertThatThrownBy(() -> invalidService.retrieve("营业时间"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI知识检索配置无效");
    }

    private AiKnowledge knowledge(Long id, String embedding) {
        return knowledge(id, embedding, "营业时间", "餐云每天09:00至21:00营业。");
    }

    private AiKnowledge knowledge(Long id, String embedding, String title, String content) {
        return AiKnowledge.builder()
                .id(id)
                .title(title)
                .content(content)
                .category(AiKnowledgeCategory.SHOP)
                .embedding(embedding)
                .build();
    }

    private String embeddingJson(List<Double> embedding) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < embedding.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(embedding.get(index));
        }
        return json.append(']').toString();
    }

    private double preClampCosine(List<Double> left, List<Double> right) {
        double leftScale = maxAbsoluteValue(left);
        double rightScale = maxAbsoluteValue(right);
        double dotProduct = 0D;
        double leftNormSquared = 0D;
        double rightNormSquared = 0D;
        for (int index = 0; index < left.size(); index++) {
            double scaledLeft = left.get(index) / leftScale;
            double scaledRight = right.get(index) / rightScale;
            dotProduct += scaledLeft * scaledRight;
            leftNormSquared += scaledLeft * scaledLeft;
            rightNormSquared += scaledRight * scaledRight;
        }
        return dotProduct / Math.sqrt(leftNormSquared * rightNormSquared);
    }

    private double maxAbsoluteValue(List<Double> embedding) {
        double max = 0D;
        for (Double value : embedding) {
            max = Math.max(max, Math.abs(value));
        }
        return max;
    }
}
