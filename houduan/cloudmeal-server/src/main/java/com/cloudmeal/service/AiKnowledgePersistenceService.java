package com.cloudmeal.service;

import com.cloudmeal.entity.AiKnowledge;
import com.cloudmeal.service.model.AiKnowledgeRetryOutcome;

/**
 * AI客服知识短事务持久化服务。
 */
public interface AiKnowledgePersistenceService {

    /**
     * 原子保存待同步的第一版知识及其向量任务。
     *
     * @param knowledge 尚未生成向量的第一版知识
     * @return 新知识记录ID
     */
    Long saveFirstVersion(AiKnowledge knowledge);

    /**
     * 锁住旧版本，在同一短事务中停用旧版本、作废旧任务、插入新版本及新任务。
     *
     * @param sourceId 被修改的旧版本ID
     * @param next     待生成向量的下一版本草稿
     * @return 新版本知识记录ID
     */
    Long saveNextVersion(Long sourceId, AiKnowledge next);

    /**
     * 在短事务内复用并重置当前知识版本的最终失败任务。
     * 已在同步或已经可用时幂等返回，不重复创建任务。
     */
    AiKnowledgeRetryOutcome retryFailedEmbedding(Long knowledgeId);
}
