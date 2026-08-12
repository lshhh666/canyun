package com.sky.service;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.service.impl.ImageUploadServiceImpl;
import com.sky.utils.AliOssUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceImplTest {

    @Mock
    private AliOssUtil aliOssUtil;

    @InjectMocks
    private ImageUploadServiceImpl service;

    @Test
    void rejectsEmptyFileBeforeCallingOss() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[0]);

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("文件不能为空", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    @Test
    void rejectsNonImageBeforeCallingOss() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("仅支持 PNG、JPEG 和 WebP 图片", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    @Test
    void rejectsFileLargerThanTwoMiBBeforeCallingOss() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[2 * 1024 * 1024 + 1]);

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("图片大小不能超过 2MB", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    @Test
    void uploadsPngUsingGeneratedNameUnderSuppliedDirectory() {
        MockMultipartFile file = new MockMultipartFile("file", "../../avatar.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        when(aliOssUtil.upload(any(byte[].class), any(String.class))).thenReturn("https://oss/avatar.png");

        String result = service.uploadImage(file, "user-avatar");

        assertEquals("https://oss/avatar.png", result);
        ArgumentCaptor<String> objectName = ArgumentCaptor.forClass(String.class);
        verify(aliOssUtil).upload(eq(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}), objectName.capture());
        assertTrue(objectName.getValue().matches("user-avatar/[0-9a-f-]+\\.png"));
    }

    @Test
    void uploadsJpegAndWebpWithContentTypeExtension() {
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.bin", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 0, 0, 0, 0});
        MockMultipartFile webp = new MockMultipartFile("file", "avatar.bin", "image/webp",
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});
        when(aliOssUtil.upload(any(byte[].class), any(String.class))).thenReturn("https://oss/image");

        service.uploadImage(jpeg, "user-avatar");
        service.uploadImage(webp, "user-avatar");

        ArgumentCaptor<String> objectName = ArgumentCaptor.forClass(String.class);
        verify(aliOssUtil, org.mockito.Mockito.times(2)).upload(any(byte[].class), objectName.capture());
        assertTrue(objectName.getAllValues().get(0).matches("user-avatar/[0-9a-f-]+\\.jpg"));
        assertTrue(objectName.getAllValues().get(1).matches("user-avatar/[0-9a-f-]+\\.webp"));
    }

    @Test
    void mapsOssFailureToUploadFailedMessage() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        when(aliOssUtil.upload(any(byte[].class), any(String.class))).thenThrow(new RuntimeException("OSS unavailable"));

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals(MessageConstant.UPLOAD_FAILED, error.getMessage());
    }

    @Test
    void rejectsTextDisguisedAsPngBeforeCallingOss() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("仅支持 PNG、JPEG 和 WebP 图片", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }
}
