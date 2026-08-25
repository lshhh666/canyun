package com.sky.controller.user;

import com.sky.service.AiChatService;
import com.sky.vo.AiChatVO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiChatControllerTest {

    @Test
    void shouldDelegateMessageToServiceAndReturnAnswer() throws Exception {
        AiChatService aiChatService = Mockito.mock(AiChatService.class);
        when(aiChatService.chat("你好")).thenReturn(AiChatVO.builder()
                .answer("您好，请问需要什么帮助？")
                .build());
        AiChatController controller = new AiChatController(aiChatService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/user/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.answer").value("您好，请问需要什么帮助？"));

        verify(aiChatService).chat("你好");
    }
}
