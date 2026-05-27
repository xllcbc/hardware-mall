package com.example.mystore.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mystore.entity.db.User;

public interface UserService {
    User login(String code);
    User getUserInfo(Long userId);
    User updateUserInfo(User user);
    User updatePhone(Long userId, String phoneCode);
    String refreshToken(Long userId);
    Page<User> getUserPage(Integer page, Integer limit, String province, String city, Integer status);
    void updateUserStatus(Long id, Integer status);
    void logout(Long userId, String token);
    String getSessionKey(Long userId);
}