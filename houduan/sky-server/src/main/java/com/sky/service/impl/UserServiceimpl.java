package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.JwtClaimsConstant;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserProfileDTO;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.exception.LoginFailedException;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.UserMapper;
import com.sky.properties.JwtProperties;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import com.sky.vo.UserProfileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceimpl implements UserService {
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private UserMapper userMapper;

    public static final String WX_LOGIN="https://api.weixin.qq.com/sns/jscode2session";
    @Override
    public UserLoginVO login(UserLoginDTO userLoginDTO) {

        String openid = getString(userLoginDTO);

        if(openid==null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        User user=userMapper.getByOpenid(openid);
        if(user==null){
            user=User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        }
        //生成JWT
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID,user.getId());
        String token= JwtUtil.createJWT(jwtProperties.getUserSecretKey(),jwtProperties.getUserTtl(),claims);

       UserProfileVO profile = toProfile(user);
       return UserLoginVO.builder()
               .id(user.getId())
               .openid(user.getOpenid())
               .token(token)
               .name(profile.getName())
               .avatar(profile.getAvatar())
               .profileCompleted(profile.getProfileCompleted())
               .build();
    }

    @Override
    public UserProfileVO getProfile() {
        Long userId = BaseContext.getCurrentId();
        return toProfile(userMapper.getById(userId));
    }

    @Override
    public UserProfileVO updateProfile(UserProfileDTO userProfileDTO) {
        String name = trimToNull(userProfileDTO.getName());
        String avatar = trimToNull(userProfileDTO.getAvatar());
        validateProfile(name, avatar);

        Long userId = BaseContext.getCurrentId();
        User user = User.builder().id(userId).name(name).avatar(avatar).build();
        userMapper.updateProfile(user);
        return toProfile(user);
    }

    private UserProfileVO toProfile(User user) {
        if (user == null) {
            throw new UserNotLoginException(MessageConstant.USER_NOT_LOGIN);
        }
        String name = trimToNull(user.getName());
        String avatar = trimToNull(user.getAvatar());
        return UserProfileVO.builder()
                .id(user.getId())
                .name(name)
                .avatar(avatar)
                .profileCompleted(name != null && avatar != null)
                .build();
    }

    private void validateProfile(String name, String avatar) {
        if (name == null) {
            throw new BaseException("昵称不能为空");
        }
        if (name.length() > 32) {
            throw new BaseException("昵称不能超过32个字符");
        }
        if (!isHttpAvatarUrl(avatar)) {
            throw new BaseException("头像地址无效");
        }
    }

    private boolean isHttpAvatarUrl(String avatar) {
        if (avatar == null) {
            return false;
        }
        try {
            URI uri = new URI(avatar);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected String getString(UserLoginDTO userLoginDTO) {
        // 1. 拼接微信接口地址

        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", weChatProperties.getAppid());
        paramMap.put("secret", weChatProperties.getSecret());
        paramMap.put("js_code", userLoginDTO.getCode());
        paramMap.put("grant_type", "authorization_code");

        // 2. 发请求，拿到响应 JSON
        String json = HttpClientUtil.doGet(WX_LOGIN, paramMap);

        // 3. 解析 JSON 取 openid
        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");
        return openid;
    }
}
