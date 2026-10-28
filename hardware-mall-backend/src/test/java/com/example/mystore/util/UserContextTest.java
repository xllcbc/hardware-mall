package com.example.mystore.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void setAndGetLevelId() {
        UserContext.setLevelId(2L);
        assertThat(UserContext.getLevelId()).isEqualTo(2L);
    }

    @Test
    void clearRemovesLevelId() {
        UserContext.setLevelId(2L);
        UserContext.clear();
        assertThat(UserContext.getLevelId()).isNull();
    }
}
