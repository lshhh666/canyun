package com.cloudmeal.service;

import com.cloudmeal.dto.AiKnowledgeCreateDTO;
import com.cloudmeal.dto.AiKnowledgePageQueryDTO;
import com.cloudmeal.dto.AiKnowledgeUpdateDTO;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.vo.AiKnowledgeSaveVO;

/**
 * AI客服权威知识业务服务。
 */
public interface AiKnowledgeService {

    /** 分页查询当前启用的知识版本及其向量同步状态。 */
    PageResult pageCurrent(AiKnowledgePageQueryDTO queryDTO);

    /**
     * 用短事务保存第一版知识并创建异步向量任务。
     *
     * @param dto 管理端提交的知识内容
     * @return 新知识记录ID
     */
    Long create(AiKnowledgeCreateDTO dto);

    /**
     * 保存当前启用知识的下一版本并创建异步向量任务。
     *
     * @param id  被修改的当前启用版本ID
     * @param dto 新版本内容
     * @return 新版本知识记录ID
     */
    Long update(Long id, AiKnowledgeUpdateDTO dto);

    /** 人工重新同步失败的知识；重复请求按当前状态幂等返回。 */
    AiKnowledgeSaveVO retryEmbedding(Long id);
}
