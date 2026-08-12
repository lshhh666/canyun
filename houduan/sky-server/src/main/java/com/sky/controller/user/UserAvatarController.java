package com.sky.controller.user;

import com.sky.result.Result;
import com.sky.service.ImageUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user/user")
public class UserAvatarController {

    @Autowired
    private ImageUploadService imageUploadService;

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(imageUploadService.uploadImage(file, "user-avatar"));
    }
}
