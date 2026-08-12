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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
    void uploadsPngUsingGeneratedNameUnderSuppliedDirectory() throws IOException {
        byte[] png = imageBytes("png");
        MockMultipartFile file = new MockMultipartFile("file", "../../avatar.png", "image/png",
                png);
        when(aliOssUtil.upload(any(byte[].class), any(String.class))).thenReturn("https://oss/avatar.png");

        String result = service.uploadImage(file, "user-avatar");

        assertEquals("https://oss/avatar.png", result);
        ArgumentCaptor<String> objectName = ArgumentCaptor.forClass(String.class);
        verify(aliOssUtil).upload(eq(png), objectName.capture());
        assertTrue(objectName.getValue().matches("user-avatar/[0-9a-f-]+\\.png"));
    }

    @Test
    void uploadsJpegAndWebpWithVerifiedExtensions() throws IOException {
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.bin", "image/jpeg",
                imageBytes("jpeg"));
        MockMultipartFile webp = new MockMultipartFile("file", "avatar.bin", "image/webp",
                validWebpBytes());
        when(aliOssUtil.upload(any(byte[].class), any(String.class))).thenReturn("https://oss/image");

        service.uploadImage(jpeg, "user-avatar");
        service.uploadImage(webp, "user-avatar");

        ArgumentCaptor<String> objectName = ArgumentCaptor.forClass(String.class);
        verify(aliOssUtil, org.mockito.Mockito.times(2)).upload(any(byte[].class), objectName.capture());
        assertTrue(objectName.getAllValues().get(0).matches("user-avatar/[0-9a-f-]+\\.jpg"));
        assertTrue(objectName.getAllValues().get(1).matches("user-avatar/[0-9a-f-]+\\.webp"));
    }

    @Test
    void mapsOssFailureToUploadFailedMessage() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png",
                imageBytes("png"));
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

    @Test
    void rejectsPngMagicWithGarbagePayloadBeforeCallingOss() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3});

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("仅支持 PNG、JPEG 和 WebP 图片", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    @Test
    void rejectsValidPngWithNonWhitelistedMimeBeforeCallingOss() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "text/plain", imageBytes("png"));

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("仅支持 PNG、JPEG 和 WebP 图片", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    @Test
    void rejectsMimeThatDoesNotMatchValidatedImageFormatBeforeCallingOss() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/jpeg", imageBytes("png"));

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("仅支持 PNG、JPEG 和 WebP 图片", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    @Test
    void rejectsVp8xContainerWithoutDecodableImageBeforeCallingOss() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", pseudoVp8xWebpBytes());

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("仅支持 PNG、JPEG 和 WebP 图片", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    @Test
    void rejectsFakeVp8PayloadBeforeCallingOss() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", fakeVp8WebpBytes());

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("仅支持 PNG、JPEG 和 WebP 图片", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    @Test
    void rejectsImageWiderThanMaximumBeforeCallingOss() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", imageBytes("png", 4097, 1));

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("图片尺寸过大", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    private static byte[] imageBytes(String format) throws IOException {
        return imageBytes(format, 1, 1);
    }

    private static byte[] imageBytes(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private static byte[] validWebpBytes() {
        return Base64.getDecoder().decode("UklGRkAAAABXRUJQVlA4IDQAAADwAQCdASoBAAEAAQAcJaACdLoB+AAETAAA/vW4f/6aR40jxpHxcP/ugT90CfugT/3NoAAA");
    }

    private static byte[] pseudoVp8xWebpBytes() {
        return new byte[]{
                'R', 'I', 'F', 'F', 22, 0, 0, 0, 'W', 'E', 'B', 'P',
                'V', 'P', '8', 'X', 10, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    }

    private static byte[] fakeVp8WebpBytes() {
        return new byte[]{
                'R', 'I', 'F', 'F', 14, 0, 0, 0, 'W', 'E', 'B', 'P',
                'V', 'P', '8', ' ', 2, 0, 0, 0, 1, 2
        };
    }
}
