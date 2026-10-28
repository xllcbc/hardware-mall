package com.example.mystore.controller;

import com.example.mystore.common.constant.StatusConstants;
import com.example.mystore.entity.db.Category;
import com.example.mystore.mapper.CategoryMapper;
import com.example.mystore.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserLevelControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private CategoryMapper categoryMapper;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtUtil.generateToken(Map.of(
                "userId", 1L,
                "role", StatusConstants.USER_ROLE_ADMIN));
        Category category = new Category();
        category.setId(88L);
        category.setName("锁具");
        category.setSortOrder(10);
        category.setStatus(1);
        category.setDeleteTime(0L);
        categoryMapper.insert(category);
    }

    @Test
    void createFillAndEnableLevel() throws Exception {
        String body = mvc.perform(post("/api/admin/user-level")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"批发A\",\"sortOrder\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andReturn().getResponse().getContentAsString();

        long levelId = com.alibaba.fastjson2.JSON.parseObject(body)
                .getJSONObject("data")
                .getLongValue("id");

        mvc.perform(put("/api/admin/user-level/" + levelId + "/discounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"items\":[{\"categoryId\":88,\"rate\":0.8000}]}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/admin/user-level/" + levelId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"status\":1}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/user-level/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].status").value(1));
    }
}
