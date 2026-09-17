package com.sky.dto;

import com.sky.enums.AiKnowledgeCategory;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端修改AI客服权威知识时提交的新版本内容。
 * knowledgeKey从旧版本继承，不能由前端修改。
 */
@Data
public class AiKnowledgeUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新版本知识标题。 */
    private String title;

    /** 新版本知识所属业务分类。 */
    private AiKnowledgeCategory category;

    /** 新版本权威规则正文。 */
    private String content;
}
