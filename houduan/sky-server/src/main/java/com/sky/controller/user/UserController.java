package com.sky.controller.user;

import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserProfileDTO;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.vo.UserLoginVO;
import com.sky.vo.UserProfileVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequestMapping("/user/user")
@Api("用户相关接口")
public class UserController {
    @Autowired
    private UserService userService;
    //用户登录
    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        UserLoginVO  userLoginVO=userService.login(userLoginDTO);
        return Result.success(userLoginVO);
    }

    @GetMapping("/profile")
    public Result<UserProfileVO> profile() {
        return Result.success(userService.getProfile());
    }

    @PutMapping("/profile")
    public Result<UserProfileVO> updateProfile(@RequestBody UserProfileDTO dto) {
        return Result.success(userService.updateProfile(dto));
    }

    //用户退出
    @ApiOperation("用户退出")
    @PostMapping("/logout")
    public Result logout() {
        return Result.success();
    }
}
