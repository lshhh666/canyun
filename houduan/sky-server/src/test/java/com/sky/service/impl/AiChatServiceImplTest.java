package com.sky.service.impl;

import com.sky.client.AiChatClient;
import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.vo.AiChatVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceImplTest {

    @Mock
    private AiChatClient aiChatClient;

    @Test
    void shouldNormalizeMessageCallClientAndWrapAnswer() {
        AiChatServiceImpl service = new AiChatServiceImpl(aiChatClient);
        when(aiChatClient.chat(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("你好")))
                .thenReturn("您好，请问需要什么帮助？");

        AiChatVO result = service.chat("  你好  ");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiChatClient).chat(systemPromptCaptor.capture(), org.mockito.ArgumentMatchers.eq("你好"));
        assertThat(systemPromptCaptor.getValue())
                .contains("不得编造")
                .contains("不得泄露")
                .contains("其他用户")
                .contains("忽略上述规则")
                .contains("暂时无法回答这个问题");
        assertThat(result.getAnswer()).isEqualTo("您好，请问需要什么帮助？");
    }

    @Test
    void shouldRejectBlankMessageWithoutCallingClient() {
        AiChatServiceImpl service = new AiChatServiceImpl(aiChatClient);

        assertThatThrownBy(() -> service.chat("   "))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_MESSAGE_EMPTY);
        verify(aiChatClient, never()).chat(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void shouldRejectMessageLongerThanOneHundredUnicodeCharacters() {
        AiChatServiceImpl service = new AiChatServiceImpl(aiChatClient);
        String message = "你".repeat(100) + "好";

        assertThatThrownBy(() -> service.chat(message))
                .isInstanceOf(BaseException.class)
                .hasMessage(MessageConstant.AI_MESSAGE_TOO_LONG);
        verify(aiChatClient, never()).chat(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void shouldCountEmojiAsOneCharacter() {
        AiChatServiceImpl service = new AiChatServiceImpl(aiChatClient);
        String message = "😀".repeat(100);
        when(aiChatClient.chat(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(message)))
                .thenReturn("收到");

        AiChatVO result = service.chat(message);

        assertThat(result.getAnswer()).isEqualTo("收到");
        verify(aiChatClient).chat(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(message));
    }
}
