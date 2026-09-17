package com.cloudmeal.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionResponse {

    /**
     * 模型生成的候选回答列表，餐云第一版只读取第一个候选回答
     */
    private List<Choice> choices;
}
