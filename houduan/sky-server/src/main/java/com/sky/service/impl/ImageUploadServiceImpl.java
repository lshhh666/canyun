package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.service.ImageUploadService;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class ImageUploadServiceImpl implements ImageUploadService {

    private static final long MAX_IMAGE_SIZE = 2 * 1024 * 1024;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Override
    public String uploadImage(MultipartFile file, String serverDirectory) {
        String extension = validateFile(file);
        validateServerDirectory(serverDirectory);

        String objectName = serverDirectory + "/" + UUID.randomUUID() + extension;
        try {
            return aliOssUtil.upload(file.getBytes(), objectName);
        } catch (Exception e) {
            log.error("Image upload failed for object: {}", objectName);
            throw new BaseException(MessageConstant.UPLOAD_FAILED);
        }
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException("文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BaseException("图片大小不能超过 2MB");
        }
        if (!isSupportedContentType(file.getContentType())) {
            throw unsupportedImage();
        }
        try {
            ImageFormat format = imageFormatFor(file.getBytes());
            if (!format.contentType.equals(file.getContentType())) {
                throw unsupportedImage();
            }
            return format.extension;
        } catch (IOException e) {
            throw unsupportedImage();
        }
    }

    private void validateServerDirectory(String serverDirectory) {
        if (serverDirectory == null || !serverDirectory.matches("[a-z0-9-]+")) {
            throw new BaseException("上传目录无效");
        }
    }

    private boolean isSupportedContentType(String contentType) {
        return ImageFormat.PNG.contentType.equals(contentType)
                || ImageFormat.JPEG.contentType.equals(contentType)
                || ImageFormat.WEBP.contentType.equals(contentType);
    }

    private ImageFormat imageFormatFor(byte[] bytes) throws IOException {
        if (isPng(bytes)) {
            return validateDecodedImage(bytes, ImageFormat.PNG);
        }
        if (isJpeg(bytes)) {
            return validateDecodedImage(bytes, ImageFormat.JPEG);
        }
        if (isWebp(bytes)) {
            if (hasValidWebpImageChunk(bytes)) {
                return ImageFormat.WEBP;
            }
        }
        throw unsupportedImage();
    }

    private ImageFormat validateDecodedImage(byte[] bytes, ImageFormat format) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw unsupportedImage();
        }
        return format;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && bytes[4] == '\r'
                && bytes[5] == '\n'
                && bytes[6] == 0x1a
                && bytes[7] == '\n';
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == (byte) 0xff
                && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private boolean hasValidWebpImageChunk(byte[] bytes) {
        if (bytes.length < 20 || unsignedLittleEndianInt(bytes, 4) != bytes.length - 8) {
            return false;
        }
        int position = 12;
        while (position + 8 <= bytes.length) {
            int chunkSize = unsignedLittleEndianInt(bytes, position + 4);
            long chunkEnd = (long) position + 8 + chunkSize;
            if (chunkEnd > bytes.length) {
                return false;
            }
            if (isChunk(bytes, position, 'V', 'P', '8', ' ') && hasValidVp8Dimensions(bytes, position + 8, chunkSize)) {
                return true;
            }
            if (isChunk(bytes, position, 'V', 'P', '8', 'L') && hasValidVp8lDimensions(bytes, position + 8, chunkSize)) {
                return true;
            }
            if (isChunk(bytes, position, 'V', 'P', '8', 'X') && hasValidVp8xDimensions(bytes, position + 8, chunkSize)) {
                return true;
            }
            position = (int) (chunkEnd + (chunkSize & 1));
        }
        return position == bytes.length;
    }

    private boolean hasValidVp8Dimensions(byte[] bytes, int dataOffset, int chunkSize) {
        if (chunkSize < 10 || dataOffset + 10 > bytes.length
                || bytes[dataOffset + 3] != (byte) 0x9d
                || bytes[dataOffset + 4] != 0x01
                || bytes[dataOffset + 5] != 0x2a) {
            return false;
        }
        return unsignedLittleEndianShort(bytes, dataOffset + 6) > 0
                && unsignedLittleEndianShort(bytes, dataOffset + 8) > 0;
    }

    private boolean hasValidVp8lDimensions(byte[] bytes, int dataOffset, int chunkSize) {
        if (chunkSize < 5 || dataOffset + 5 > bytes.length || bytes[dataOffset] != 0x2f) {
            return false;
        }
        int width = 1 + ((bytes[dataOffset + 1] & 0xff) | ((bytes[dataOffset + 2] & 0x3f) << 8));
        int height = 1 + (((bytes[dataOffset + 2] & 0xc0) >> 6)
                | ((bytes[dataOffset + 3] & 0xff) << 2)
                | ((bytes[dataOffset + 4] & 0x0f) << 10));
        return width > 0 && height > 0;
    }

    private boolean hasValidVp8xDimensions(byte[] bytes, int dataOffset, int chunkSize) {
        if (chunkSize < 10 || dataOffset + 10 > bytes.length) {
            return false;
        }
        int width = 1 + unsigned24LittleEndian(bytes, dataOffset + 4);
        int height = 1 + unsigned24LittleEndian(bytes, dataOffset + 7);
        return width > 0 && height > 0;
    }

    private boolean isChunk(byte[] bytes, int offset, char first, char second, char third, char fourth) {
        return bytes[offset] == first && bytes[offset + 1] == second
                && bytes[offset + 2] == third && bytes[offset + 3] == fourth;
    }

    private int unsignedLittleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16) | ((bytes[offset + 3] & 0xff) << 24);
    }

    private int unsignedLittleEndianShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) | ((bytes[offset + 1] & 0x3f) << 8));
    }

    private int unsigned24LittleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8) | ((bytes[offset + 2] & 0xff) << 16);
    }

    private BaseException unsupportedImage() {
        return new BaseException("仅支持 PNG、JPEG 和 WebP 图片");
    }

    private enum ImageFormat {
        PNG("image/png", ".png"),
        JPEG("image/jpeg", ".jpg"),
        WEBP("image/webp", ".webp");

        private final String contentType;
        private final String extension;

        ImageFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }
}
