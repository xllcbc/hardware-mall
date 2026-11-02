package com.example.mystore.service;

import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.vo.UserLevelVO;

import java.util.List;

public interface UserLevelService {
    List<UserLevelVO> list();
    UserLevel getById(Long id);
    UserLevel create(UserLevel level);
    UserLevel update(Long id, UserLevel level);
    void updateStatus(Long id, Integer status);
    void delete(Long id);
    String getName(Long id);
}
