package com.example.mystore.service.impl;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.User;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.WechatUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private WechatUtil wechatUtil;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(2L);
        user.setOpenid("test_openid_001");
        user.setNickname("张三");
        user.setPhone("13800001001");
        user.setRole(StatusConstants.USER_ROLE_REGULAR);
        user.setStatus(StatusConstants.USER_STATUS_NORMAL);
    }

    @Test
    void testGetUserInfo_MasksOpenid() {
        when(userMapper.selectById(2L)).thenReturn(user);

        User result = userService.getUserInfo(2L);

        assertThat(result).isNotNull();
        assertThat(result.getNickname()).isEqualTo("张三");
        assertThat(result.getOpenid()).isNull(); // openid 应该被脱敏
    }

    @Test
    void testGetUserInfo_UserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        User result = userService.getUserInfo(999L);

        assertThat(result).isNull();
    }

    @Test
    void testUpdateUserInfo() {
        when(userMapper.selectById(2L)).thenReturn(user);

        User update = new User();
        update.setId(2L);
        update.setNickname("张三丰");
        update.setPhone("13800001111");

        User result = userService.updateUserInfo(update);

        assertThat(result.getNickname()).isEqualTo("张三丰");
        assertThat(result.getPhone()).isEqualTo("13800001111");
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    void testUpdateUserInfo_UserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        User update = new User();
        update.setId(999L);
        update.setNickname("不存在");

        assertThatThrownBy(() -> userService.updateUserInfo(update))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void testUpdateUserStatus_Disable() {
        when(userMapper.selectById(2L)).thenReturn(user);

        userService.updateUserStatus(2L, StatusConstants.USER_STATUS_DISABLED);

        verify(userMapper).updateById(org.mockito.Mockito.<User>argThat(u -> u.getStatus() == StatusConstants.USER_STATUS_DISABLED));
    }

    @Test
    void testUpdateUserStatus_UserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updateUserStatus(999L, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void testRefreshToken() {
        when(jwtUtil.generateToken(anyMap())).thenReturn("mock_token_123");

        String token = userService.refreshToken(2L);

        assertThat(token).isEqualTo("mock_token_123");
        verify(jwtUtil).generateToken(argThat(claims ->
                claims.containsKey("userId") && claims.get("userId").equals(2L)
        ));
    }

    @Test
    void testGetSessionKey() {
        when(redisUtil.get("wechat:session:" + 2L)).thenReturn("session_key_abc");

        String sessionKey = userService.getSessionKey(2L);

        assertThat(sessionKey).isEqualTo("session_key_abc");
    }

    @Test
    void testGetSessionKey_Null() {
        when(redisUtil.get("wechat:session:" + 2L)).thenReturn(null);

        String sessionKey = userService.getSessionKey(2L);

        assertThat(sessionKey).isNull();
    }
}
