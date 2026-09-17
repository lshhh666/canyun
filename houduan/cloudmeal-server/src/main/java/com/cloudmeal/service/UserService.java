package com.cloudmeal.service;

import com.cloudmeal.dto.UserLoginDTO;
import com.cloudmeal.dto.UserProfileDTO;
import com.cloudmeal.vo.UserLoginVO;
import com.cloudmeal.vo.UserProfileVO;

public interface UserService {
    //用户登录
    UserLoginVO login(UserLoginDTO userLoginDTO);

    UserProfileVO getProfile();

    UserProfileVO updateProfile(UserProfileDTO userProfileDTO);
}
