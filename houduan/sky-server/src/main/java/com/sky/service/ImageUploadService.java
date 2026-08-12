package com.sky.service;

import org.springframework.web.multipart.MultipartFile;

public interface ImageUploadService {

    String uploadImage(MultipartFile file, String serverDirectory);
}
