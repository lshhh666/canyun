package com.sky.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.client.AiEmbeddingClient;
import com.sky.constant.MessageConstant;
import com.sky.entity.AiKnowledge;
import com.sky.exception.AiServiceException;
import com.sky.exception.BaseException;
import com.sky.mapper.AiKnowledgeMapper;
import com.sky.properties.AiProperties;
import com.sky.service.AiKnowledgeRetrievalService;
import com.sky.service.model.AiKnowledgeMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 使用当前嵌入模型在本地知识库中计算余弦相似度。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiKnowledgeRetrievalServiceImpl implements AiKnowledgeRetrievalService {

    private final AiEmbeddingClient aiEmbeddingClient;
    private final AiKnowledgeMapper aiKnowledgeMapper;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<AiKnowledgeMatch> retrieve(String question) {
        validateConfiguration();
        if (!StringUtils.hasText(question)) {
            throw new BaseException(MessageConstant.AI_MESSAGE_EMPTY);
        }

        List<Double> questionEmbedding = aiEmbeddingClient.embed(question.trim());
        validateQuestionEmbedding(questionEmbedding);

        List<AiKnowledge> candidates = aiKnowledgeMapper.selectEnabledByEmbeddingConfiguration(
                aiProperties.getEmbeddingModel(), aiProperties.getEmbeddingDimensions());
        if (candidates == null || candidates.isEmpty()) {
            log.info("知识检索完成，最终命中数量：0，无符合当前模型和维度的启用知识");
            return new ArrayList<>();
        }

        List<AiKnowledgeMatch> matches = new ArrayList<>();
        for (AiKnowledge candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            List<Double> candidateEmbedding = parseStoredEmbedding(candidate);
            if (candidateEmbedding == null || !hasNonZeroNorm(candidateEmbedding)) {
                log.warn("跳过无效知识向量，知识ID：{}", candidate.getId());
                continue;
            }

            double score = cosineSimilarity(questionEmbedding, candidateEmbedding);
            // 联调时观察被阈值筛掉的候选；候选评分不等于最终命中，不记录问题或规则全文。
            log.info("知识候选评分，知识ID：{}，相似度：{}，当前阈值：{}",
                    candidate.getId(), score, aiProperties.getRetrievalMinScore());
            if (score >= aiProperties.getRetrievalMinScore()) {
                matches.add(AiKnowledgeMatch.builder()
                        .id(candidate.getId())
                        .title(candidate.getTitle())
                        .content(candidate.getContent())
                        .category(candidate.getCategory())
                        .score(score)
                        .build());
            }
        }

        matches.sort(Comparator.comparingDouble(AiKnowledgeMatch::getScore)
                .reversed()
                .thenComparing(AiKnowledgeMatch::getId));
        // matches是所有达到阈值的知识；result才是真正交给调用方的Top K结果。
        List<AiKnowledgeMatch> result;
        if (matches.size() <= aiProperties.getRetrievalTopK()) {
            result = matches;
        } else {
            result = new ArrayList<>(matches.subList(0, aiProperties.getRetrievalTopK()));
        }

        // 先确定最终结果，再记录、返回同一个集合；无命中时也会输出数量0。
        log.info("知识检索完成，最终命中数量：{}", result.size());
        for (AiKnowledgeMatch match : result) {
            log.info("知识检索命中，知识ID：{}，相似度：{}", match.getId(), match.getScore());
        }
        return result;
    }

    private void validateConfiguration() {
        double minScore = aiProperties.getRetrievalMinScore();
        if (aiProperties.getRetrievalTopK() <= 0
                || !Double.isFinite(minScore)
                || minScore < -1D
                || minScore > 1D
                || !StringUtils.hasText(aiProperties.getEmbeddingModel())
                || aiProperties.getEmbeddingDimensions() <= 0) {
            throw new IllegalStateException("AI知识检索配置无效");
        }
    }

    private void validateQuestionEmbedding(List<Double> embedding) {
        if (!isValidEmbedding(embedding) || !hasNonZeroNorm(embedding)) {
            throw new AiServiceException(MessageConstant.AI_SERVICE_UNAVAILABLE);
        }
    }

    private List<Double> parseStoredEmbedding(AiKnowledge candidate) {
        try {
            JsonNode root = objectMapper.readTree(candidate.getEmbedding());
            if (root == null || !root.isArray()
                    || root.size() != aiProperties.getEmbeddingDimensions()) {
                return null;
            }
            List<Double> embedding = new ArrayList<>(root.size());
            for (JsonNode value : root) {
                if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                    return null;
                }
                embedding.add(value.doubleValue());
            }
            return embedding;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isValidEmbedding(List<Double> embedding) {
        if (embedding == null || embedding.size() != aiProperties.getEmbeddingDimensions()) {
            return false;
        }
        for (Double value : embedding) {
            if (value == null || !Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasNonZeroNorm(List<Double> embedding) {
        for (Double value : embedding) {
            if (value != 0D) {
                return true;
            }
        }
        return false;
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
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
        double score = dotProduct / Math.sqrt(leftNormSquared * rightNormSquared);
        return Double.isFinite(score)
                ? Math.max(-1D, Math.min(1D, score))
                : score;
    }

    private double maxAbsoluteValue(List<Double> embedding) {
        double max = 0D;
        for (Double value : embedding) {
            max = Math.max(max, Math.abs(value));
        }
        return max;
    }
}
