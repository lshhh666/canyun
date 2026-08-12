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
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Iterator;
import java.util.UUID;

@Service
@Slf4j
public class ImageUploadServiceImpl implements ImageUploadService {

    private static final long MAX_IMAGE_SIZE = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 4096;
    private static final long MAX_IMAGE_PIXELS = 16_777_216L;

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
        } catch (RuntimeException e) {
            if (e instanceof BaseException) {
                throw e;
            }
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
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw unsupportedImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw unsupportedImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                ImageFormat format = ImageFormat.fromReaderFormat(reader.getFormatName());
                if (!reader.getImageTypes(0).hasNext()) {
                    throw unsupportedImage();
                }
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                if (format == ImageFormat.WEBP) {
                    reader.readAsRenderedImage(0, null);
                }
                return format;
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw unsupportedImage();
        }
        if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                || (long) width * height > MAX_IMAGE_PIXELS) {
            throw new BaseException("图片尺寸过大");
        }
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

        private static ImageFormat fromReaderFormat(String formatName) {
            if ("png".equalsIgnoreCase(formatName)) {
                return PNG;
            }
            if ("jpeg".equalsIgnoreCase(formatName) || "jpg".equalsIgnoreCase(formatName)) {
                return JPEG;
            }
            if ("webp".equalsIgnoreCase(formatName)) {
                return WEBP;
            }
            throw new BaseException("仅支持 PNG、JPEG 和 WebP 图片");
        }
    }
}
