package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.pagehelper.Page;
import com.sky.entity.AiKnowledge;
import com.sky.enums.AiKnowledgeCategory;
import com.sky.enums.AiKnowledgeEmbeddingStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 客服知识数据访问层。
 */
@Mapper
public interface AiKnowledgeMapper extends BaseMapper<AiKnowledge> {

    /** 分页查询当前启用版本，不加载体积较大的向量字段。 */
    Page<AiKnowledge> pageCurrent(
            @Param("keyword") String keyword,
            @Param("category") AiKnowledgeCategory category,
            @Param("embeddingStatus") AiKnowledgeEmbeddingStatus embeddingStatus);

    /** 查询指定知识版本并持有排他行锁，必须在事务中调用。 */
    AiKnowledge selectByIdForUpdate(@Param("id") Long id);

    /** 仅当旧版本仍处于ENABLED（启用）时才停用。 */
    int disableEnabled(@Param("id") Long id,
                       @Param("updateTime") LocalDateTime updateTime);

    /**
     * 查询可与当前文本向量进行语义匹配的启用知识。
     * 向量内容仅供内部检索服务计算相似度，不得向外返回。
     */
    List<AiKnowledge> selectEnabledByEmbeddingConfiguration(
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDimensions") int embeddingDimensions);

    /** 异步任务仅能为当前启用且仍待生成的知识版本写入向量。 */
    int markEmbeddingReady(@Param("id") Long id,
                           @Param("embedding") String embedding,
                           @Param("embeddingModel") String embeddingModel,
                           @Param("embeddingDimensions") int embeddingDimensions,
                           @Param("now") LocalDateTime now);

    /** 自动重试耗尽后，把当前启用的待生成知识标记为同步失败。 */
    int markEmbeddingFailed(@Param("id") Long id,
                            @Param("now") LocalDateTime now);

    /** 人工重试时，仅把当前启用且最终失败的知识恢复为待生成。 */
    int markEmbeddingPendingForManualRetry(@Param("id") Long id,
                                           @Param("now") LocalDateTime now);

    /** 统计所选稳定知识标识中，当前向量空间可用的启用版本数量。 */
    int countReadyCurrentByKeys(@Param("knowledgeKeys") List<String> knowledgeKeys,
                                @Param("embeddingModel") String embeddingModel,
                                @Param("embeddingDimensions") int embeddingDimensions);
}
