package com.sky.controller.user;

import com.sky.service.ImageUploadService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAvatarControllerTest {

    @Test
    void uploadAvatarDelegatesMultipartFileAndReturnsUrl() throws Exception {
        ImageUploadService imageUploadService = Mockito.mock(ImageUploadService.class);
        when(imageUploadService.uploadImage(any(), eq("user-avatar"))).thenReturn("https://oss/avatar.png");
        UserAvatarController controller = new UserAvatarController();
        ReflectionTestUtils.setField(controller, "imageUploadService", imageUploadService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(multipart("/user/user/avatar")
                        .file("file", new byte[]{1})
                        .contentType("image/png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value("https://oss/avatar.png"));

        verify(imageUploadService).uploadImage(any(), eq("user-avatar"));
    }
}
