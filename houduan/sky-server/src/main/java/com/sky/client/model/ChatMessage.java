package com.sky.client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * 消息角色，例如 system、user、assistant 或 tool
     */
    private String role;

    /**
     * 消息的文本内容
     */
    private String content;
}
