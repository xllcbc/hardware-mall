package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.db.User;

public interface UserService {
    User login(String code);
    User getUserInfo(Long userId);
    User getUserByOpenid(String openid);
    User updateUserInfo(User user);
    User updatePhone(Long userId, String phoneCode);
    String refreshToken(Long userId, String oldToken);
    Page<User> getUserPage(Integer page, Integer limit, String province, String city, Integer status);
    void updateUserStatus(Long id, Integer status);
    void updateUserRegion(Long id, String province, String city);
    void logout(Long userId, String token);
    /** 预留方法：当前业务无调用方，用于未来老版微信解密（签名校验/手机号解密/unionid 统一身份） */
    String getSessionKey(Long userId);
}