package com.sky.mapper;

import com.sky.entity.AiKnowledge;
import com.sky.enums.AiKnowledgeCategory;
import com.sky.enums.AiKnowledgeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证语义检索候选集只包含当前向量空间的启用知识。
 */
@SpringBootTest(properties = "sky.websocket.enabled=false")
@Transactional
class AiKnowledgeMapperIntegrationTest {

    private static final String EMBEDDING_MODEL = "embedding-3";
    private static final int EMBEDDING_DIMENSIONS = 256;

    @Autowired
    private AiKnowledgeMapper knowledgeMapper;

    @Test
    void shouldExcludeDisabledAndIncompatibleKnowledgeFromRetrievalCandidates() {
        AiKnowledge compatible = insertKnowledge(AiKnowledgeStatus.ENABLED,
                EMBEDDING_MODEL, EMBEDDING_DIMENSIONS);
        AiKnowledge disabled = insertKnowledge(AiKnowledgeStatus.DISABLED,
                EMBEDDING_MODEL, EMBEDDING_DIMENSIONS);
        AiKnowledge wrongModel = insertKnowledge(AiKnowledgeStatus.ENABLED,
                "other-embedding-model", EMBEDDING_DIMENSIONS);
        AiKnowledge wrongDimension = insertKnowledge(AiKnowledgeStatus.ENABLED,
                EMBEDDING_MODEL, 512);

        List<AiKnowledge> candidates = knowledgeMapper.selectEnabledByEmbeddingConfiguration(
                EMBEDDING_MODEL, EMBEDDING_DIMENSIONS);

        assertThat(candidates).extracting(AiKnowledge::getId)
                .contains(compatible.getId())
                .doesNotContain(disabled.getId(), wrongModel.getId(), wrongDimension.getId());
    }

    private AiKnowledge insertKnowledge(AiKnowledgeStatus status,
                                        String embeddingModel,
                                        int embeddingDimensions) {
        LocalDateTime now = LocalDateTime.now();
        AiKnowledge knowledge = AiKnowledge.builder()
                .knowledgeKey("RETRIEVAL_" + UUID.randomUUID().toString().replace("-", ""))
                .title("检索候选测试")
                .category(AiKnowledgeCategory.SHOP)
                .content("用于验证语义检索候选过滤。")
                .embedding(embeddingJson(embeddingDimensions))
                .embeddingModel(embeddingModel)
                .embeddingDimensions(embeddingDimensions)
                .versionNo(1)
                .status(status)
                .createTime(now)
                .updateTime(now)
                .build();
        assertThat(knowledgeMapper.insert(knowledge)).isEqualTo(1);
        return knowledge;
    }

    private String embeddingJson(int dimensions) {
        StringBuilder embedding = new StringBuilder("[");
        for (int index = 0; index < dimensions; index++) {
            if (index > 0) {
                embedding.append(',');
            }
            embedding.append(index == 0 ? "1.0" : "0.0");
        }
        return embedding.append(']').toString();
    }
}
