package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ImageUploadService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/common")
@Slf4j
@Api(tags = "通用接口")
public class CommonController {
    @Autowired
    ImageUploadService imageUploadService;
    @ApiOperation("文件上传接口")
    @PostMapping("/upload")
    public Result<String> uploadAliOssFile(MultipartFile file) {
        return Result.success(imageUploadService.uploadImage(file, "business-image"));
    }
}
