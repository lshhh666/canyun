package com.sky.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 交给证据充足性判断模型的最小知识字段，不包含向量、相似度等内部数据。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEvidenceReference {

    /** 权威知识标题。 */
    private String title;

    /** 权威知识正文。 */
    private String content;
}
