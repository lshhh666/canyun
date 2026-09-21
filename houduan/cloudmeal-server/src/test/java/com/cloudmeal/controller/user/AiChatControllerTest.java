package com.cloudmeal.controller.user;

import com.cloudmeal.constant.MessageConstant;
import com.cloudmeal.context.BaseContext;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.exception.AiChatTurnException;
import com.cloudmeal.handler.GlobalExceptionHandler;
import com.cloudmeal.service.AiChatRateLimiter;
import com.cloudmeal.service.AiChatService;
import com.cloudmeal.vo.AiChatHistoryMessageVO;
import com.cloudmeal.vo.AiChatHistoryVO;
import com.cloudmeal.vo.AiChatVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiChatControllerTest {

    @AfterEach
    void clearUser() {
        BaseContext.removeCurrentId();
    }

    @Test
    void shouldReturnRecentHistoryWithoutAcceptingUserIdFromFrontend() throws Exception {
        AiChatService aiChatService = Mockito.mock(AiChatService.class);
        when(aiChatService.getRecentHistory()).thenReturn(AiChatHistoryVO.builder()
                .sessionId(25L)
                .processing(false)
                .messages(java.util.Collections.singletonList(
                        AiChatHistoryMessageVO.builder()
                                .messageId(45L)
                                .role("assistant")
                                .content("订单已取消。")
                                .sequenceNo(4)
                                .build()))
                .build());
        AiChatController controller = new AiChatController(aiChatService, mock(AiChatRateLimiter.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/user/ai/session/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.sessionId").value(25))
                .andExpect(jsonPath("$.data.processing").value(false))
                .andExpect(jsonPath("$.data.messages[0].content").value("订单已取消。"));

        verify(aiChatService).getRecentHistory();
    }

    @Test
    void shouldDelegateMessageToServiceAndReturnAnswer() throws Exception {
        AiChatService aiChatService = Mockito.mock(AiChatService.class);
        when(aiChatService.chat("你好", null)).thenReturn(AiChatVO.builder()
                .sessionId(25L)
                .answer("您好，请问需要什么帮助？")
                .build());
        AiChatRateLimiter limiter = mock(AiChatRateLimiter.class);
        BaseContext.setCurrentId(7L);
        AiChatController controller = new AiChatController(aiChatService, limiter);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/user/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.sessionId").value(25))
                .andExpect(jsonPath("$.data.answer").value("您好，请问需要什么帮助？"));

        verify(aiChatService).chat("你好", null);
        verify(limiter).check(7L);
    }

    @Test
    void shouldCloseSessionWithoutAcceptingUserIdFromFrontend() throws Exception {
        AiChatService aiChatService = Mockito.mock(AiChatService.class);
        AiChatController controller = new AiChatController(aiChatService, mock(AiChatRateLimiter.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/user/ai/session/25/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(aiChatService).startNewConversation(25L);
    }

    @Test
    void shouldReturnSessionIdWhenFirstTurnFailsAfterUserMessageWasSaved() throws Exception {
        AiChatService aiChatService = Mockito.mock(AiChatService.class);
        when(aiChatService.chat("你好", null)).thenThrow(new AiChatTurnException(
                MessageConstant.AI_SERVICE_UNAVAILABLE,
                25L,
                new RuntimeException("provider unavailable")));
        AiChatController controller = new AiChatController(aiChatService, mock(AiChatRateLimiter.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/user/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.AI_SERVICE_UNAVAILABLE))
                .andExpect(jsonPath("$.data.sessionId").value(25));

        verify(aiChatService).chat("你好", null);
    }

    @Test
    void shouldConfirmPendingActionWithoutAcceptingOrderIdFromFrontend() throws Exception {
        AiChatService aiChatService = Mockito.mock(AiChatService.class);
        when(aiChatService.confirmAction(35L)).thenReturn(AiChatVO.builder()
                .sessionId(25L)
                .answer("订单尾号3346已取消。")
                .build());
        AiChatController controller = new AiChatController(aiChatService, mock(AiChatRateLimiter.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/user/ai/actions/35/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.sessionId").value(25))
                .andExpect(jsonPath("$.data.answer").value("订单尾号3346已取消。"));

        verify(aiChatService).confirmAction(35L);
    }

    @Test
    void shouldReturnBusinessErrorWithoutInvokingChatWhenLimited() throws Exception {
        AiChatService aiChatService = mock(AiChatService.class);
        AiChatRateLimiter limiter = mock(AiChatRateLimiter.class);
        BaseContext.setCurrentId(7L);
        doThrow(new BaseException(MessageConstant.AI_CHAT_RATE_LIMIT_EXCEEDED))
                .when(limiter).check(7L);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiChatController(aiChatService, limiter))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(post("/user/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.AI_CHAT_RATE_LIMIT_EXCEEDED));

        verifyNoInteractions(aiChatService);
    }

    @Test
    void shouldLeaveNonChatEndpointsUnmetered() throws Exception {
        AiChatService aiChatService = mock(AiChatService.class);
        AiChatRateLimiter limiter = mock(AiChatRateLimiter.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiChatController(aiChatService, limiter))
                .build();

        mockMvc.perform(get("/user/ai/session/recent")).andExpect(status().isOk());
        mockMvc.perform(post("/user/ai/session/25/close")).andExpect(status().isOk());
        mockMvc.perform(post("/user/ai/actions/35/confirm")).andExpect(status().isOk());

        verifyNoInteractions(limiter);
    }
}
