package com.sky.controller.user;

import com.sky.dto.AiChatFeedbackDTO;
import com.sky.enums.AiChatFeedbackResult;
import com.sky.service.AiChatFeedbackService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiChatFeedbackControllerTest {

    @Test
    void shouldAcceptOnlySessionAndFeedbackResult() throws Exception {
        AiChatFeedbackService service = Mockito.mock(AiChatFeedbackService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AiChatFeedbackController(service))
                .build();

        mockMvc.perform(post("/user/ai/session/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":25,\"result\":\"UNSOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        ArgumentCaptor<AiChatFeedbackDTO> captor =
                ArgumentCaptor.forClass(AiChatFeedbackDTO.class);
        verify(service).submit(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo(25L);
        assertThat(captor.getValue().getResult()).isEqualTo(AiChatFeedbackResult.UNSOLVED);
    }
}
