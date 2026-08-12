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
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;

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

    @Test
    void rejectsPngWithReadableDimensionsButTruncatedImageDataBeforeCallingOss() throws IOException {
        byte[] truncatedPng = truncatedPngImageData();
        assertReaderRecognizesDimensions(truncatedPng, 16, 16);
        assertImageCannotBeDecodedCompletely(truncatedPng);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", truncatedPng);

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("仅支持 PNG、JPEG 和 WebP 图片", error.getMessage());
        verifyNoInteractions(aliOssUtil);
    }

    @Test
    void rejectsJpegWithReadableDimensionsButTruncatedScanDataBeforeCallingOss() throws IOException {
        byte[] truncatedJpeg = truncatedJpegScanData();
        assertReaderRecognizesDimensions(truncatedJpeg, 16, 16);
        assertImageCannotBeDecodedCompletely(truncatedJpeg);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", truncatedJpeg);

        BaseException error = assertThrows(BaseException.class,
                () -> service.uploadImage(file, "user-avatar"));

        assertEquals("仅支持 PNG、JPEG 和 WebP 图片", error.getMessage());
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

    private static byte[] truncatedPngImageData() throws IOException {
        byte[] png = patternedImageBytes("png", 16, 16);
        int idatTypeOffset = indexOf(png, new byte[]{'I', 'D', 'A', 'T'});
        assertTrue(idatTypeOffset >= 4);
        return Arrays.copyOf(png, idatTypeOffset + 6);
    }

    private static byte[] truncatedJpegScanData() throws IOException {
        byte[] jpeg = patternedImageBytes("jpeg", 16, 16);
        int scanMarkerOffset = indexOf(jpeg, new byte[]{(byte) 0xff, (byte) 0xda});
        assertTrue(scanMarkerOffset >= 0);
        int scanHeaderLength = ((jpeg[scanMarkerOffset + 2] & 0xff) << 8) | (jpeg[scanMarkerOffset + 3] & 0xff);
        int scanDataOffset = scanMarkerOffset + 2 + scanHeaderLength;
        return Arrays.copyOf(jpeg, scanDataOffset + 1);
    }

    private static byte[] patternedImageBytes(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, ((x * 37) << 16) | ((y * 53) << 8) | ((x + y) * 29));
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private static void assertReaderRecognizesDimensions(byte[] bytes, int width, int height) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            assertTrue(readers.hasNext());
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                assertTrue(reader.getImageTypes(0).hasNext());
                assertEquals(width, reader.getWidth(0));
                assertEquals(height, reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private static void assertImageCannotBeDecodedCompletely(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            boolean[] warned = {false};
            try {
                reader.setInput(input, true, true);
                reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
                RenderedImage image = reader.readAsRenderedImage(0, null);
                if (image != null) {
                    image.getData();
                }
            } catch (IOException | RuntimeException expected) {
                return;
            } finally {
                reader.dispose();
            }
            assertTrue(warned[0], "truncated fixture must not decode without an ImageIO warning");
        }
    }

    private static int indexOf(byte[] bytes, byte[] marker) {
        for (int offset = 0; offset <= bytes.length - marker.length; offset++) {
            boolean matches = true;
            for (int index = 0; index < marker.length; index++) {
                if (bytes[offset + index] != marker[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return offset;
            }
        }
        return -1;
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
