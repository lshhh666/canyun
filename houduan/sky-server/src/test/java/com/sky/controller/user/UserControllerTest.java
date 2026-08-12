package com.sky.controller.user;

import com.sky.dto.UserProfileDTO;
import com.sky.service.UserService;
import com.sky.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    @Test
    void profileDelegatesToServiceAndReturnsProfileFields() throws Exception {
        UserService userService = Mockito.mock(UserService.class);
        when(userService.getProfile()).thenReturn(UserProfileVO.builder()
                .id(7L).name("小餐").avatar("https://img/a.png").profileCompleted(true).build());
        MockMvc mockMvc = mockMvc(userService);

        mockMvc.perform(get("/user/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.name").value("小餐"))
                .andExpect(jsonPath("$.data.avatar").value("https://img/a.png"))
                .andExpect(jsonPath("$.data.profileCompleted").value(true));

        verify(userService).getProfile();
    }

    @Test
    void updateProfileDelegatesToServiceAndReturnsProfileFields() throws Exception {
        UserService userService = Mockito.mock(UserService.class);
        when(userService.updateProfile(any(UserProfileDTO.class))).thenReturn(UserProfileVO.builder()
                .id(7L).name("小餐").avatar("https://img/a.png").profileCompleted(true).build());
        MockMvc mockMvc = mockMvc(userService);

        mockMvc.perform(put("/user/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小餐\",\"avatar\":\"https://img/a.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.name").value("小餐"))
                .andExpect(jsonPath("$.data.avatar").value("https://img/a.png"))
                .andExpect(jsonPath("$.data.profileCompleted").value(true));

        ArgumentCaptor<UserProfileDTO> captured = ArgumentCaptor.forClass(UserProfileDTO.class);
        verify(userService).updateProfile(captured.capture());
        assertEquals("小餐", captured.getValue().getName());
        assertEquals("https://img/a.png", captured.getValue().getAvatar());
    }

    private MockMvc mockMvc(UserService userService) {
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }
}
