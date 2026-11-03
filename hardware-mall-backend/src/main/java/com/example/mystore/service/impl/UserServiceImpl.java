package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.common.exception.BusinessException;
import com.example.mystore.config.OssProperties;
import com.example.mystore.entity.db.User;
import com.example.mystore.entity.vo.UserAdminVO;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.PricingRuleService;
import com.example.mystore.service.UserService;
import com.example.mystore.service.UserLevelService;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisLockUtil;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.WechatUtil;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final long USER_IDENTITY_LOCK_RETRY_MILLIS = 20L;

    private final UserMapper userMapper;
    private final WechatUtil wechatUtil;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final RedisLockUtil redisLockUtil;
    private final OssProperties ossProperties;
    private final PricingRuleService pricingRuleService;
    private final UserLevelService userLevelService;

    @Override
    public User login(String code) {
        Map<String, String> sessionData = wechatUtil.getSessionKey(code);
        String openid = sessionData.get("openid");
        String sessionKey = sessionData.get("session_key");

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getOpenid, openid);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setRole(StatusConstants.USER_ROLE_REGULAR);
            user.setStatus(StatusConstants.USER_STATUS_NORMAL);
            user.setLevelId(pricingRuleService.resolveRetailLevelId());
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
        } else {
            if (user.getStatus() == StatusConstants.USER_STATUS_DISABLED) {
                throw new BusinessException("账号已被禁用");
            }
            LocalDateTime lastLoginTime = LocalDateTime.now();
            user.setLastLoginTime(lastLoginTime);
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getLastLoginTime, lastLoginTime));
        }

        // 预留：当前业务未使用 session_key（手机号走新版 getuserphonenumber 接口，仅用 access_token）。
        // 保留用于未来老版微信解密场景：wx.getUserInfo 签名校验、老版手机号 AES 解密、unionid 统一身份。
        redisUtil.set(RedisConstants.PREFIX_WECHAT_SESSION + user.getId(),
                sessionKey, RedisConstants.WECHAT_SESSION_TTL, TimeUnit.SECONDS);

        return user;
    }

    @Override
    public User getUserByOpenid(String openid) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getOpenid, openid);
        return userMapper.selectOne(wrapper);
    }

    @Override
    public String issueLoginToken(Long userId) {
        return withUserIdentityLock(userId, () -> {
            User user = requireLoginUser(userId);
            Map<String, Object> claims = tokenClaims(user);
            String token = jwtUtil.generateToken(claims);
            redisUtil.sAdd(RedisConstants.PREFIX_USER_TOKENS + userId, token);
            return token;
        });
    }

    @Override
    public User getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setOpenid(null);
        }
        return user;
    }

    @Override
    public User updateUserInfo(User user) {
        User existUser = userMapper.selectById(user.getId());
        if (existUser == null) {
            throw new BusinessException("用户不存在");
        }

        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getUpdateTime, LocalDateTime.now());
        if (user.getNickname() != null) {
            existUser.setNickname(user.getNickname());
            update.set(User::getNickname, existUser.getNickname());
        }
        if (StringUtils.hasText(user.getAvatarUrl())) {
            String expectedPrefix = ossProperties.getDomain() + "/";
            if (!user.getAvatarUrl().startsWith(expectedPrefix)) {
                log.warn("拒绝非法头像地址, userId={}, url={}", user.getId(), user.getAvatarUrl());
            } else {
                existUser.setAvatarUrl(user.getAvatarUrl());
                update.set(User::getAvatarUrl, existUser.getAvatarUrl());
            }
        }
        if (user.getPhone() != null) {
            existUser.setPhone(user.getPhone());
            update.set(User::getPhone, existUser.getPhone());
        }
        userMapper.update(null, update);
        return existUser;
    }

    @Override
    public User updatePhone(Long userId, String phoneCode) {
        String phone = wechatUtil.getPhoneNumber(phoneCode);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPhone(phone);
        LocalDateTime updateTime = LocalDateTime.now();
        user.setUpdateTime(updateTime);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPhone, phone)
                .set(User::getUpdateTime, updateTime));

        if (user.getOpenid() != null) {
            user.setOpenid(null);
        }
        return user;
    }

    @Override
    public void updateUserRegion(Long id, String province, String city) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setProvince(province);
        user.setCity(city);
        LocalDateTime updateTime = LocalDateTime.now();
        user.setUpdateTime(updateTime);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getProvince, province)
                .set(User::getCity, city)
                .set(User::getUpdateTime, updateTime));
    }

    @Override
    public String refreshToken(Long userId, String oldToken) {
        return withUserIdentityLock(userId, () -> {
            User user = requireLoginUser(userId);
            String newToken = jwtUtil.generateToken(tokenClaims(user));

            redisUtil.sAdd(RedisConstants.PREFIX_USER_TOKENS + userId, newToken);
            redisUtil.sRemove(RedisConstants.PREFIX_USER_TOKENS + userId, oldToken);

            long ttl = (jwtUtil.getExpirationFromToken(oldToken) - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                redisUtil.set(RedisConstants.PREFIX_TOKEN_BLACKLIST + oldToken,
                        RedisConstants.TOKEN_BLACKLIST_VALUE, ttl, TimeUnit.SECONDS);
            }

            return newToken;
        });
    }

    @Override
    public Page<UserAdminVO> getUserPage(Integer page, Integer limit, String province, String city,
                                         Integer status, Long levelId) {
        Page<User> pageParam = new Page<>(page, limit);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(province)) {
            wrapper.eq(User::getProvince, province);
        }
        if (StringUtils.hasText(city)) {
            wrapper.eq(User::getCity, city);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (levelId != null) {
            wrapper.eq(User::getLevelId, levelId);
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> source = userMapper.selectPage(pageParam, wrapper);
        Page<UserAdminVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::toAdminVO).toList());
        return result;
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getRole() == StatusConstants.USER_ROLE_ADMIN) {
            throw new BusinessException("管理员账号不允许封禁");
        }
        user.setStatus(status);
        LocalDateTime updateTime = LocalDateTime.now();
        user.setUpdateTime(updateTime);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getStatus, status)
                .set(User::getUpdateTime, updateTime));
        if (status == StatusConstants.USER_STATUS_DISABLED) {
            invalidateUserTokens(id);
        }
    }

    @Override
    public void updateUserLevel(Long id, Long levelId) {
        if (levelId == null) {
            throw new BusinessException("用户等级不能为空");
        }
        withUserIdentityLock(id, () -> {
            User user = userMapper.selectById(id);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            var level = userLevelService.getById(levelId);
            if (level.getStatus() == null
                    || level.getStatus() != UserLevelStatusConstants.STATUS_ENABLED) {
                throw new BusinessException("目标等级未启用");
            }
            Long oldLevelId = user.getLevelId();
            if (!levelId.equals(oldLevelId)) {
                user.setLevelId(levelId);
                LocalDateTime updateTime = LocalDateTime.now();
                user.setUpdateTime(updateTime);
                userMapper.update(null, new LambdaUpdateWrapper<User>()
                        .eq(User::getId, id)
                        .set(User::getLevelId, levelId)
                        .set(User::getUpdateTime, updateTime));
                log.info("用户等级变更, userId={}, levelId: {} -> {}", id, oldLevelId, levelId);
            }
            invalidateUserTokens(id);
            return null;
        });
    }

    private UserAdminVO toAdminVO(User user) {
        UserAdminVO vo = new UserAdminVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setProvince(user.getProvince());
        vo.setCity(user.getCity());
        vo.setRole(user.getRole());
        vo.setLevelId(user.getLevelId());
        vo.setLevelName(userLevelService.getName(user.getLevelId()));
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setLastLoginIp(user.getLastLoginIp());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    private User requireLoginUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() == StatusConstants.USER_STATUS_DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
        return user;
    }

    private Map<String, Object> tokenClaims(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", user.getRole());
        claims.put("levelId", user.getLevelId());
        return claims;
    }

    private <T> T withUserIdentityLock(Long userId, Supplier<T> action) {
        String lockKey = RedisConstants.PREFIX_USER_LEVEL_LOCK + userId;
        while (!redisLockUtil.tryLock(lockKey)) {
            sleepWhileWaitingForIdentityLock();
        }
        try {
            return action.get();
        } finally {
            redisLockUtil.unlock(lockKey);
        }
    }

    private void sleepWhileWaitingForIdentityLock() {
        try {
            Thread.sleep(USER_IDENTITY_LOCK_RETRY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待用户身份锁时被中断", e);
        }
    }

    private void invalidateUserTokens(Long userId) {
        java.util.Set<String> tokens = redisUtil.sMembers(RedisConstants.PREFIX_USER_TOKENS + userId, String.class);
        if (tokens != null && !tokens.isEmpty()) {
            for (String token : tokens) {
                try {
                    long ttl = (jwtUtil.getExpirationFromToken(token) - System.currentTimeMillis()) / 1000;
                    if (ttl > 0) {
                        redisUtil.set(RedisConstants.PREFIX_TOKEN_BLACKLIST + token,
                                RedisConstants.TOKEN_BLACKLIST_VALUE, ttl, TimeUnit.SECONDS);
                    }
                } catch (JwtException e) {
                    // 已自然过期或损坏的死 token 无需拉黑（拦截器本就会 401），跳过继续
                    log.info("token 失效扫描: 登记集中存在无效 token，跳过拉黑, userId={}", userId);
                }
            }
            redisUtil.delete(RedisConstants.PREFIX_USER_TOKENS + userId);
        }
    }

    @Override
    public void logout(Long userId, String token) {
        redisUtil.sRemove(RedisConstants.PREFIX_USER_TOKENS + userId, token);
        long ttl = (jwtUtil.getExpirationFromToken(token) - System.currentTimeMillis()) / 1000;
        if (ttl > 0) {
            redisUtil.set(RedisConstants.PREFIX_TOKEN_BLACKLIST + token,
                    RedisConstants.TOKEN_BLACKLIST_VALUE, ttl, TimeUnit.SECONDS);
        }
    }

    /**
     * 预留方法：当前业务无调用方。
     * 用于未来老版微信解密场景：wx.getUserInfo 签名校验、老版手机号 AES 解密、unionid 统一身份。
     */
    @Override
    public String getSessionKey(Long userId) {
        Object value = redisUtil.get(RedisConstants.PREFIX_WECHAT_SESSION + userId);
        return value == null ? null : value.toString();
    }
}
