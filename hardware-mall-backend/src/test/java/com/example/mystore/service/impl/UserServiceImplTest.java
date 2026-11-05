package com.example.mystore.service.impl;

import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.config.OssProperties;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.mystore.entity.db.User;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.service.UserLevelService;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.WechatUtil;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @BeforeAll
    static void initMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                User.class);
    }

    @Mock
    private UserMapper userMapper;

    @Mock
    private WechatUtil wechatUtil;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private RedisLockUtil redisLockUtil;

    @Mock
    private OssProperties ossProperties;

    @Mock
    private PricingRuleService pricingRuleService;

    @Mock
    private UserLevelService userLevelService;

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
        user.setLevelId(1L);
        lenient().when(redisLockUtil.tryLock(anyString())).thenReturn(true);
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
    void existingUserLoginOnlyUpdatesLastLoginTime() {
        when(wechatUtil.getSessionKey("login_code"))
                .thenReturn(Map.of("openid", "test_openid_001", "session_key", "session_key"));
        when(userMapper.selectOne(any())).thenReturn(user);

        User result = userService.login("login_code");

        assertThat(result.getId()).isEqualTo(2L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> updateCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSet())
                .contains("last_login_time")
                .doesNotContain("level_id");
        verify(userMapper, never()).updateById(any(User.class));
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
        LambdaUpdateWrapper<User> updateWrapper = captureUserUpdate();
        assertThat(updateWrapper.getSqlSet())
                .contains("nickname", "phone", "update_time")
                .doesNotContain("level_id");
        verify(userMapper, never()).updateById(any(User.class));
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
    void testUpdateUserInfo_ValidAvatarUrl() {
        when(userMapper.selectById(2L)).thenReturn(user);
        when(ossProperties.getDomain()).thenReturn("https://java0251014.oss-cn-beijing.aliyuncs.com");

        User update = new User();
        update.setId(2L);
        update.setAvatarUrl("https://java0251014.oss-cn-beijing.aliyuncs.com/avatars/test.jpg");

        User result = userService.updateUserInfo(update);

        assertThat(result.getAvatarUrl()).isEqualTo("https://java0251014.oss-cn-beijing.aliyuncs.com/avatars/test.jpg");
        assertThat(captureUserUpdate().getSqlSet()).contains("avatar_url");
    }

    @Test
    void testUpdateUserInfo_InvalidAvatarUrl_Ignored() {
        when(userMapper.selectById(2L)).thenReturn(user);
        when(ossProperties.getDomain()).thenReturn("https://java0251014.oss-cn-beijing.aliyuncs.com");

        User update = new User();
        update.setId(2L);
        update.setAvatarUrl("wxfile://tmp_abc123.jpeg");

        User result = userService.updateUserInfo(update);

        assertThat(result.getAvatarUrl()).isNull();
        assertThat(captureUserUpdate().getSqlSet()).doesNotContain("avatar_url");
    }

    @Test
    void testUpdateUserInfo_BlankAvatarUrl_SkipsValidation() {
        when(userMapper.selectById(2L)).thenReturn(user);

        User update = new User();
        update.setId(2L);
        update.setAvatarUrl("");

        User result = userService.updateUserInfo(update);

        assertThat(result.getAvatarUrl()).isNull();
        assertThat(captureUserUpdate().getSqlSet()).doesNotContain("avatar_url");
    }

    @Test
    void updatePhoneOnlyWritesPhoneFields() {
        when(wechatUtil.getPhoneNumber("phone_code")).thenReturn("13900001111");
        when(userMapper.selectById(2L)).thenReturn(user);

        userService.updatePhone(2L, "phone_code");

        assertThat(captureUserUpdate().getSqlSet())
                .contains("phone", "update_time")
                .doesNotContain("level_id");
    }

    @Test
    void updateRegionOnlyWritesRegionFields() {
        when(userMapper.selectById(2L)).thenReturn(user);

        userService.updateUserRegion(2L, "浙江省", "杭州市");

        assertThat(captureUserUpdate().getSqlSet())
                .contains("province", "city", "update_time")
                .doesNotContain("level_id");
    }

    @Test
    void testUpdateUserStatus_Disable() {
        when(userMapper.selectById(2L)).thenReturn(user);

        userService.updateUserStatus(2L, StatusConstants.USER_STATUS_DISABLED);

        LambdaUpdateWrapper<User> updateWrapper = captureUserUpdate();
        assertThat(updateWrapper.getSqlSet()).contains("status", "update_time");
        assertThat(updateWrapper.getParamNameValuePairs().values())
                .contains(StatusConstants.USER_STATUS_DISABLED);
    }

    @Test
    void testUpdateUserStatus_UserNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.updateUserStatus(999L, 0))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void testUpdateUserStatus_AdminCannotBeDisabled() {
        User admin = new User();
        admin.setId(99L);
        admin.setRole(StatusConstants.USER_ROLE_ADMIN);
        admin.setStatus(StatusConstants.USER_STATUS_NORMAL);
        when(userMapper.selectById(99L)).thenReturn(admin);

        assertThatThrownBy(() -> userService.updateUserStatus(99L, StatusConstants.USER_STATUS_DISABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("管理员账号不允许封禁");

        verify(userMapper, never()).updateById(any(User.class));
        verify(userMapper, never()).update(isNull(), any());
        verifyNoInteractions(redisUtil);
    }

    @Test
    void testUpdateUserStatus_DisableSweepSkipsExpiredTokens() {
        User user = new User();
        user.setId(2L);
        user.setRole(StatusConstants.USER_ROLE_REGULAR);
        user.setStatus(StatusConstants.USER_STATUS_NORMAL);
        when(userMapper.selectById(2L)).thenReturn(user);

        Set<String> tokens = new HashSet<>();
        tokens.add("expired_token");
        tokens.add("live_token");
        when(redisUtil.sMembers(RedisConstants.PREFIX_USER_TOKENS + 2L, String.class)).thenReturn(tokens);

        when(jwtUtil.getExpirationFromToken("expired_token"))
                .thenThrow(new ExpiredJwtException(null, null, "JWT expired"));
        when(jwtUtil.getExpirationFromToken("live_token"))
                .thenReturn(System.currentTimeMillis() + 3600_000L);

        userService.updateUserStatus(2L, StatusConstants.USER_STATUS_DISABLED);

        LambdaUpdateWrapper<User> updateWrapper = captureUserUpdate();
        assertThat(updateWrapper.getSqlSet()).contains("status");
        assertThat(updateWrapper.getParamNameValuePairs().values())
                .contains(StatusConstants.USER_STATUS_DISABLED);
        verify(redisUtil).set(eq(RedisConstants.PREFIX_TOKEN_BLACKLIST + "live_token"),
                eq(RedisConstants.TOKEN_BLACKLIST_VALUE), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisUtil, never()).set(eq(RedisConstants.PREFIX_TOKEN_BLACKLIST + "expired_token"),
                any(), anyLong(), any(TimeUnit.class));
        verify(redisUtil).delete(RedisConstants.PREFIX_USER_TOKENS + 2L);
    }

    @Test
    void updateUserLevel_persistsTargetLevel() {
        User u = new User();
        u.setId(5L);
        u.setLevelId(1L);
        when(userMapper.selectById(5L)).thenReturn(u);
        UserLevel level = new UserLevel();
        level.setId(2L);
        level.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        when(userLevelService.getById(2L)).thenReturn(level);
        userService.updateUserLevel(5L, 2L);
        assertThat(u.getLevelId()).isEqualTo(2L);
        LambdaUpdateWrapper<User> updateWrapper = captureUserUpdate();
        assertThat(updateWrapper.getSqlSet())
                .contains("level_id", "update_time")
                .doesNotContain("nickname", "phone", "status");
        verify(userMapper, never()).updateById(any(User.class));
        var lockKey = RedisConstants.PREFIX_USER_LEVEL_LOCK + 5L;
        var ordered = inOrder(redisLockUtil, userMapper);
        ordered.verify(redisLockUtil).tryLock(lockKey);
        ordered.verify(userMapper).selectById(5L);
        verify(redisLockUtil).unlock(lockKey);
    }

    @Test
    void updateUserLevel_rejectsDisabledLevel() {
        User u = new User();
        u.setId(5L);
        u.setLevelId(1L);
        when(userMapper.selectById(5L)).thenReturn(u);
        UserLevel level = new UserLevel();
        level.setId(2L);
        level.setStatus(UserLevelStatusConstants.STATUS_DISABLED);
        when(userLevelService.getById(2L)).thenReturn(level);

        assertThatThrownBy(() -> userService.updateUserLevel(5L, 2L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateUserLevel_invalidatesExistingTokens() {
        User u = new User();
        u.setId(5L);
        u.setLevelId(1L);
        when(userMapper.selectById(5L)).thenReturn(u);
        UserLevel level = new UserLevel();
        level.setId(2L);
        level.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        when(userLevelService.getById(2L)).thenReturn(level);

        Set<String> tokens = new HashSet<>();
        tokens.add("live_token");
        when(redisUtil.sMembers(RedisConstants.PREFIX_USER_TOKENS + 5L, String.class)).thenReturn(tokens);
        when(jwtUtil.getExpirationFromToken("live_token"))
                .thenReturn(System.currentTimeMillis() + 3600_000L);

        userService.updateUserLevel(5L, 2L);

        verify(redisUtil).set(eq(RedisConstants.PREFIX_TOKEN_BLACKLIST + "live_token"),
                eq(RedisConstants.TOKEN_BLACKLIST_VALUE), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisUtil).delete(RedisConstants.PREFIX_USER_TOKENS + 5L);
    }

    @Test
    void updateUserLevel_sameLevel_retryInvalidatesTokensAfterFirstFailure() {
        User u = new User();
        u.setId(5L);
        u.setLevelId(2L);
        when(userMapper.selectById(5L)).thenReturn(u);
        UserLevel level = new UserLevel();
        level.setId(2L);
        level.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        when(userLevelService.getById(2L)).thenReturn(level);

        Set<String> tokens = new HashSet<>();
        tokens.add("live_token");
        when(redisUtil.sMembers(RedisConstants.PREFIX_USER_TOKENS + 5L, String.class)).thenReturn(tokens);
        when(jwtUtil.getExpirationFromToken("live_token"))
                .thenReturn(System.currentTimeMillis() + 3600_000L);
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .doNothing()
                .when(redisUtil).set(eq(RedisConstants.PREFIX_TOKEN_BLACKLIST + "live_token"),
                        eq(RedisConstants.TOKEN_BLACKLIST_VALUE), anyLong(), eq(TimeUnit.SECONDS));

        assertThatThrownBy(() -> userService.updateUserLevel(5L, 2L))
                .isInstanceOf(RedisConnectionFailureException.class);
        userService.updateUserLevel(5L, 2L);

        verify(userMapper, never()).updateById(any(User.class));
        verify(userMapper, never()).update(isNull(), any());
        verify(redisUtil, times(2)).sMembers(RedisConstants.PREFIX_USER_TOKENS + 5L, String.class);
        verify(redisUtil, times(2)).set(eq(RedisConstants.PREFIX_TOKEN_BLACKLIST + "live_token"),
                eq(RedisConstants.TOKEN_BLACKLIST_VALUE), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisUtil).delete(RedisConstants.PREFIX_USER_TOKENS + 5L);
    }

    @Test
    void testRefreshToken() {
        User user = new User();
        user.setId(2L);
        user.setStatus(1);
        user.setRole(1);
        user.setLevelId(1L);
        when(userMapper.selectById(2L)).thenReturn(user);
        when(jwtUtil.generateToken(anyMap())).thenReturn("new_token");
        when(jwtUtil.getExpirationFromToken("old_token")).thenReturn(System.currentTimeMillis() + 3600000);

        String token = userService.refreshToken(2L, "old_token");

        assertThat(token).isEqualTo("new_token");
        verify(jwtUtil).generateToken(argThat(claims ->
                claims.containsKey("userId") && claims.get("userId").equals(2L)
                        && claims.containsKey("role")
                        && claims.get("levelId").equals(1L)
        ));
        verify(redisUtil).sAdd(RedisConstants.PREFIX_USER_TOKENS + 2L, "new_token");
        verify(redisUtil).sRemove(RedisConstants.PREFIX_USER_TOKENS + 2L, "old_token");
        verify(redisLockUtil).tryLock(RedisConstants.PREFIX_USER_LEVEL_LOCK + 2L);
        verify(redisLockUtil).unlock(RedisConstants.PREFIX_USER_LEVEL_LOCK + 2L);
    }

    @Test
    void issueLoginToken_readsCurrentLevelWhileHoldingUserIdentityLock() {
        User current = new User();
        current.setId(5L);
        current.setRole(StatusConstants.USER_ROLE_REGULAR);
        current.setStatus(StatusConstants.USER_STATUS_NORMAL);
        current.setLevelId(2L);
        when(userMapper.selectById(5L)).thenReturn(current);
        when(jwtUtil.generateToken(anyMap())).thenReturn("level_two_token");

        String token = userService.issueLoginToken(5L);

        assertThat(token).isEqualTo("level_two_token");
        verify(jwtUtil).generateToken(argThat(claims ->
                claims.get("levelId").equals(2L)
                        && claims.get("role").equals(StatusConstants.USER_ROLE_REGULAR)));
        verify(redisLockUtil).tryLock(RedisConstants.PREFIX_USER_LEVEL_LOCK + 5L);
        verify(redisUtil).sAdd(RedisConstants.PREFIX_USER_TOKENS + 5L, "level_two_token");
        verify(redisLockUtil).unlock(RedisConstants.PREFIX_USER_LEVEL_LOCK + 5L);
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

    private LambdaUpdateWrapper<User> captureUserUpdate() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> updateCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), updateCaptor.capture());
        return updateCaptor.getValue();
    }
}
