package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.service.ImageUploadService;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
public class ImageUploadServiceImpl implements ImageUploadService {

    private static final long MAX_IMAGE_SIZE = 2 * 1024 * 1024;

    @Autowired
    private AliOssUtil aliOssUtil;

    @Override
    public String uploadImage(MultipartFile file, String serverDirectory) {
        validateFile(file);
        validateServerDirectory(serverDirectory);

        String objectName = serverDirectory + "/" + UUID.randomUUID() + extensionFor(file.getContentType());
        try {
            return aliOssUtil.upload(file.getBytes(), objectName);
        } catch (Exception e) {
            log.error("Image upload failed for object: {}", objectName);
            throw new BaseException(MessageConstant.UPLOAD_FAILED);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException("文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BaseException("图片大小不能超过 2MB");
        }
        if (!isSupportedContentType(file.getContentType())) {
            throw new BaseException("仅支持 PNG、JPEG 和 WebP 图片");
        }
    }

    private void validateServerDirectory(String serverDirectory) {
        if (serverDirectory == null || !serverDirectory.matches("[a-z0-9-]+")) {
            throw new BaseException("上传目录无效");
        }
    }

    private boolean isSupportedContentType(String contentType) {
        return "image/png".equals(contentType)
                || "image/jpeg".equals(contentType)
                || "image/webp".equals(contentType);
    }

    private String extensionFor(String contentType) {
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/jpeg".equals(contentType)) {
            return ".jpg";
        }
        return ".webp";
    }
}
