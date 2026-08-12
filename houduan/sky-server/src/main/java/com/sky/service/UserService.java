package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserProfileDTO;
import com.sky.vo.UserLoginVO;
import com.sky.vo.UserProfileVO;

public interface UserService {
    //用户登录
    UserLoginVO login(UserLoginDTO userLoginDTO);

    UserProfileVO getProfile();

    UserProfileVO updateProfile(UserProfileDTO userProfileDTO);
}
