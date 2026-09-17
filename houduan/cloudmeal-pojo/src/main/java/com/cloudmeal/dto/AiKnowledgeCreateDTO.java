package com.cloudmeal.dto;

import com.cloudmeal.enums.AiKnowledgeCategory;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端创建AI客服权威知识时提交的业务字段。
 * 版本、状态、向量和审计时间全部由服务端生成，不能由前端指定。
 */
@Data
public class AiKnowledgeCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 同一条业务规则跨版本保持不变的标识。 */
    private String knowledgeKey;

    /** 便于管理和参与语义检索的知识标题。 */
    private String title;

    /** 知识所属业务分类。 */
    private AiKnowledgeCategory category;

    /** 提供给AI客服的权威规则正文。 */
    private String content;
}
