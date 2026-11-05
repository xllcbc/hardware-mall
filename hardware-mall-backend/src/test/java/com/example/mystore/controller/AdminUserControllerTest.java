package com.example.mystore.controller;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.common.constant.UserLevelStatusConstants;
import com.example.mystore.entity.db.User;
import com.example.mystore.entity.db.UserLevel;
import com.example.mystore.mapper.UserLevelMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.junit.jupiter.api.Tag("h2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserLevelMapper userLevelMapper;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtUtil.generateToken(Map.of(
                "userId", 1L,
                "role", StatusConstants.USER_ROLE_ADMIN));

        UserLevel level = new UserLevel();
        level.setId(1002L);
        level.setName("工程客户");
        level.setIsRetail(UserLevelStatusConstants.NOT_RETAIL);
        level.setSortOrder(50);
        level.setStatus(UserLevelStatusConstants.STATUS_ENABLED);
        userLevelMapper.insert(level);

        userMapper.insert(user(1101L, "level_filter_retail", 1L));
        userMapper.insert(user(1102L, "level_filter_custom", 1002L));
    }

    @Test
    void listFiltersUsersByLevelId() throws Exception {
        mvc.perform(get("/api/admin/user/list")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("levelId", "1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(1102))
                .andExpect(jsonPath("$.data.records[0].levelId").value(1002))
                .andExpect(jsonPath("$.data.records[0].levelName").value("工程客户"));
    }

    private User user(Long id, String openid, Long levelId) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        user.setNickname(openid);
        user.setRole(StatusConstants.USER_ROLE_REGULAR);
        user.setLevelId(levelId);
        user.setStatus(StatusConstants.USER_STATUS_NORMAL);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleteTime(0L);
        return user;
    }
}
