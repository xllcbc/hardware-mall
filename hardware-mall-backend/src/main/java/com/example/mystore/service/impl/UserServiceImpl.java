package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.User;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.UserService;
import com.example.mystore.util.JwtUtil;
import com.example.mystore.util.RedisUtil;
import com.example.mystore.util.WechatUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final WechatUtil wechatUtil;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

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
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);
        } else {
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(user);
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
            throw new RuntimeException("用户不存在");
        }

        if (user.getNickname() != null) {
            existUser.setNickname(user.getNickname());
        }
        if (user.getAvatarUrl() != null) {
            existUser.setAvatarUrl(user.getAvatarUrl());
        }
        if (user.getPhone() != null) {
            existUser.setPhone(user.getPhone());
        }

        userMapper.updateById(existUser);
        return existUser;
    }

    @Override
    public User updatePhone(Long userId, String phoneCode) {
        String phone = wechatUtil.getPhoneNumber(phoneCode);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setPhone(phone);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        if (user.getOpenid() != null) {
            user.setOpenid(null);
        }
        return user;
    }

    @Override
    public void updateUserRegion(Long id, String province, String city) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setProvince(province);
        user.setCity(city);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public String refreshToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return jwtUtil.generateToken(claims);
    }

    @Override
    public Page<User> getUserPage(Integer page, Integer limit, String province, String city, Integer status) {
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
        wrapper.orderByDesc(User::getCreateTime);
        return userMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        if (status == StatusConstants.USER_STATUS_DISABLED) {
            java.util.Set<String> tokens = redisUtil.sMembers(RedisConstants.PREFIX_USER_TOKENS + id, String.class);
            if (tokens != null && !tokens.isEmpty()) {
                for (String token : tokens) {
                    redisUtil.set(RedisConstants.PREFIX_TOKEN_BLACKLIST + token, "1");
                }
                redisUtil.delete(RedisConstants.PREFIX_USER_TOKENS + id);
            }
        }
    }

    @Override
    public void logout(Long userId, String token) {
        redisUtil.sRemove(RedisConstants.PREFIX_USER_TOKENS + userId, token);
        long ttl = (jwtUtil.getExpirationFromToken(token) - System.currentTimeMillis()) / 1000;
        if (ttl > 0) {
            redisUtil.set(RedisConstants.PREFIX_TOKEN_BLACKLIST + token, "1", ttl, TimeUnit.SECONDS);
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