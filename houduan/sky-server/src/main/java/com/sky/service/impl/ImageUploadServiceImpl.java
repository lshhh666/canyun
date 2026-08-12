package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.service.ImageUploadService;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
        try {
            return extensionFor(file.getBytes());
        } catch (IOException e) {
            throw new BaseException("仅支持 PNG、JPEG 和 WebP 图片");
        }
    }

    private void validateServerDirectory(String serverDirectory) {
        if (serverDirectory == null || !serverDirectory.matches("[a-z0-9-]+")) {
            throw new BaseException("上传目录无效");
        }
    }

    private String extensionFor(byte[] bytes) {
        if (isPng(bytes)) {
            return ".png";
        }
        if (isJpeg(bytes)) {
            return ".jpg";
        }
        if (isWebp(bytes)) {
            return ".webp";
        }
        throw new BaseException("仅支持 PNG、JPEG 和 WebP 图片");
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
}
