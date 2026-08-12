package com.sky.service;

import com.sky.context.BaseContext;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserProfileDTO;
import com.sky.entity.User;
import com.sky.exception.BaseException;
import com.sky.exception.UserNotLoginException;
import com.sky.mapper.UserMapper;
import com.sky.properties.JwtProperties;
import com.sky.properties.WeChatProperties;
import com.sky.service.impl.UserServiceimpl;
import com.sky.vo.UserLoginVO;
import com.sky.vo.UserProfileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private WeChatProperties weChatProperties;
    @Mock
    private JwtProperties jwtProperties;
    @InjectMocks
    private UserServiceimpl service;

    @AfterEach
    void clearContext() {
        BaseContext.removeCurrentId();
    }

    @Test
    void loginProjectsNormalizedProfileForExistingUser() {
        TestableUserService service = new TestableUserService(userMapper, weChatProperties, jwtProperties);
        when(jwtProperties.getUserSecretKey()).thenReturn("test-secret-key");
        when(jwtProperties.getUserTtl()).thenReturn(3600000L);
        when(userMapper.getByOpenid("openid")).thenReturn(User.builder()
                .id(7L).openid("openid").name(" 小餐 ").avatar(" https://img/a.png ").build());

        UserLoginVO result = service.login(new UserLoginDTO());

        assertEquals("小餐", result.getName());
        assertEquals("https://img/a.png", result.getAvatar());
        assertTrue(result.getProfileCompleted());
    }

    @Test
    void loginMarksProfileIncompleteWhenNameIsBlank() {
        TestableUserService service = new TestableUserService(userMapper, weChatProperties, jwtProperties);
        when(jwtProperties.getUserSecretKey()).thenReturn("test-secret-key");
        when(jwtProperties.getUserTtl()).thenReturn(3600000L);
        when(userMapper.getByOpenid("openid")).thenReturn(User.builder()
                .id(7L).openid("openid").name(" ").avatar("https://img/a.png").build());

        UserLoginVO result = service.login(new UserLoginDTO());

        assertFalse(result.getProfileCompleted());
    }

    @Test
    void getProfileUsesAuthenticatedUser() {
        BaseContext.setCurrentId(7L);
        when(userMapper.getById(7L)).thenReturn(User.builder()
                .id(7L).name("小餐").avatar("https://img/a.png").build());

        UserProfileVO result = service.getProfile();

        verify(userMapper).getById(7L);
        assertEquals(7L, result.getId());
    }

    @Test
    void getProfileRejectsMissingAuthenticatedUser() {
        BaseContext.setCurrentId(7L);
        when(userMapper.getById(7L)).thenReturn(null);

        UserNotLoginException exception = assertThrows(UserNotLoginException.class, () -> service.getProfile());

        assertEquals(MessageConstant.USER_NOT_LOGIN, exception.getMessage());
    }

    @Test
    void updateProfileUsesAuthenticatedUserAndNormalizesName() {
        BaseContext.setCurrentId(7L);
        UserProfileVO result = service.updateProfile(
                UserProfileDTO.builder().name("  小餐  ").avatar("https://img/a.png").build());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateProfile(saved.capture());
        assertEquals(7L, saved.getValue().getId());
        assertEquals("小餐", saved.getValue().getName());
        assertTrue(result.getProfileCompleted());
    }

    @Test
    void updateProfileRejectsInvalidValues() {
        BaseContext.setCurrentId(7L);

        BaseException blankName = assertThrows(BaseException.class,
                () -> service.updateProfile(UserProfileDTO.builder().name(" ").avatar("https://img/a.png").build()));
        BaseException longName = assertThrows(BaseException.class,
                () -> service.updateProfile(UserProfileDTO.builder().name("123456789012345678901234567890123").avatar("https://img/a.png").build()));
        BaseException invalidAvatar = assertThrows(BaseException.class,
                () -> service.updateProfile(UserProfileDTO.builder().name("小餐").avatar("ftp://img/a.png").build()));
        BaseException schemeOnlyAvatar = assertThrows(BaseException.class,
                () -> service.updateProfile(UserProfileDTO.builder().name("小餐").avatar("https://").build()));
        BaseException hostlessAvatar = assertThrows(BaseException.class,
                () -> service.updateProfile(UserProfileDTO.builder().name("小餐").avatar("http:///avatar.png").build()));

        assertEquals("昵称不能为空", blankName.getMessage());
        assertEquals("昵称不能超过32个字符", longName.getMessage());
        assertEquals("头像地址无效", invalidAvatar.getMessage());
        assertEquals("头像地址无效", schemeOnlyAvatar.getMessage());
        assertEquals("头像地址无效", hostlessAvatar.getMessage());
    }

    private static class TestableUserService extends UserServiceimpl {
        private TestableUserService(UserMapper userMapper, WeChatProperties weChatProperties, JwtProperties jwtProperties) {
            ReflectionTestUtils.setField(this, "userMapper", userMapper);
            ReflectionTestUtils.setField(this, "weChatProperties", weChatProperties);
            ReflectionTestUtils.setField(this, "jwtProperties", jwtProperties);
        }

        @Override
        protected String getString(UserLoginDTO userLoginDTO) {
            return "openid";
        }
    }
}
