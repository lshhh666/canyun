package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/admin/common")
@Slf4j
@Api(tags = "通用接口")
public class CommonController {
    @Autowired
    AliOssUtil aliOssUtil;
    @ApiOperation("文件上传接口")
    @PostMapping("/upload")
    public Result<String> uploadAliOssFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BaseException("文件不能为空");
        }
        byte[] bytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BaseException("文件名不能为空");
        }
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));


        String newFileName = UUID.randomUUID() + suffix;
        String url;
        try {
            url = aliOssUtil.upload(bytes, newFileName);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BaseException(MessageConstant.UPLOAD_FAILED);
        }
        return Result.success(url);
    }
}
