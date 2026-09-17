package com.cloudmeal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmeal.entity.AiFeedbackKnowledgeRelation;
import com.cloudmeal.entity.AiKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 未解决评价与知识关联数据访问层。 */
@Mapper
public interface AiFeedbackKnowledgeRelationMapper
        extends BaseMapper<AiFeedbackKnowledgeRelation> {

    /** 用提交的新选择替换旧关联前，删除该反馈现有的知识关联。 */
    int deleteByFeedbackId(@Param("feedbackId") Long feedbackId);

    int countByFeedbackId(@Param("feedbackId") Long feedbackId);

    /** 加载该反馈关联的当前可用知识，不返回向量字段。 */
    List<AiKnowledge> selectReadyKnowledge(
            @Param("feedbackId") Long feedbackId,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDimensions") int embeddingDimensions);

    /** 当前至少一条关联知识可用于指定向量空间时返回正数。 */
    int countReadyKnowledge(@Param("feedbackId") Long feedbackId,
                            @Param("embeddingModel") String embeddingModel,
                            @Param("embeddingDimensions") int embeddingDimensions);
}
