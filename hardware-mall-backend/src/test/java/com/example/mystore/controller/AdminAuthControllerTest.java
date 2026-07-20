package com.example.mystore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.mystore.common.constant.RedisConstants;
import com.example.mystore.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RedisUtil redisUtil;

    private final ObjectMapper om = new ObjectMapper();

    private Map<String, String> creds(String username, String password) {
        Map<String, String> m = new HashMap<>();
        m.put("username", username);
        m.put("password", password);
        return m;
    }

    @BeforeEach
    void clearRateLimit() {
        try {
            redisUtil.delete("rate:limit:admin:login");
        } catch (Exception ignored) {
        }
    }

    @Test
    void login_withCorrectCredentials_returnsToken() throws Exception {
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(creds("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void login_withWrongPassword_returnsError() throws Exception {
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(creds("admin", "wrong_pwd"))))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void login_exceeds5PerMinute_isRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/admin/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(creds("admin", "wrong"))));
        }
        mvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(creds("admin", "wrong"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void logout_thenTokenIsBlacklisted() throws Exception {
        String token = om.readTree(mvc.perform(post("/api/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(creds("admin", "admin123"))))
                        .andReturn().getResponse().getContentAsString())
                .at("/data/token").asText();

        mvc.perform(post("/api/admin/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));

        String blacklistKey = RedisConstants.PREFIX_TOKEN_BLACKLIST + token;
        assertThat(redisUtil.hasKey(blacklistKey)).isTrue();
    }
}
