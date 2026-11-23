package com.example.mystore.service;

import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.entity.vo.UserLevelVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserLevelService {
    List<UserLevelVO> list();
    UserLevel getById(Long id);
    UserLevel create(UserLevel level);
    UserLevel update(Long id, UserLevel level);
    void updateStatus(Long id, Integer status);
    void delete(Long id);
    String getName(Long id);

    /**
     * 按等级 ID 批量读取名称，一次查询覆盖去重后的全部等级。
     */
    Map<Long, String> getNames(Collection<Long> ids);
}
