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
    void setAndGetUserGroup() {
        UserContext.setUserGroup(2);
        assertThat(UserContext.getUserGroup()).isEqualTo(2);
    }

    @Test
    void clearRemovesUserGroup() {
        UserContext.setUserGroup(2);
        UserContext.clear();
        assertThat(UserContext.getUserGroup()).isNull();
    }
}
