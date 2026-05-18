package com.example.mystore.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisIntegrationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void testSetAndGet() {
        redisUtil.set("test:key", "hello");
        Object value = redisUtil.get("test:key");
        assertThat(value).isEqualTo("hello");
    }

    @Test
    void testSetWithExpiration() throws InterruptedException {
        redisUtil.set("test:expire", "value", 1, TimeUnit.SECONDS);
        assertThat(redisUtil.get("test:expire")).isEqualTo("value");

        Thread.sleep(1500);
        assertThat(redisUtil.get("test:expire")).isNull();
    }

    @Test
    void testDelete() {
        redisUtil.set("test:del", "value");
        assertThat(redisUtil.get("test:del")).isEqualTo("value");

        redisUtil.delete("test:del");
        assertThat(redisUtil.get("test:del")).isNull();
    }

    @Test
    void testHasKey() {
        redisUtil.set("test:exists", "value");
        assertThat(redisUtil.hasKey("test:exists")).isTrue();
        assertThat(redisUtil.hasKey("test:not_exists")).isFalse();
    }

    @Test
    void testIncrAndDecr() {
        redisUtil.set("test:counter", 10);
        assertThat(redisUtil.incr("test:counter")).isEqualTo(11);
        assertThat(redisUtil.decr("test:counter")).isEqualTo(10);
    }

    @Test
    void testSetWithJitter_TTLInRange() {
        redisUtil.setWithJitter("test:jitter", "value", 60, TimeUnit.SECONDS, 10);
        long expire = redisUtil.getExpire("test:jitter");
        // TTL 应该在 60 ~ 70 秒之间
        assertThat(expire).isGreaterThanOrEqualTo(55).isLessThanOrEqualTo(75);
    }

    @Test
    void testIsNull() {
        assertThat(redisUtil.isNull("NULL")).isTrue();
        assertThat(redisUtil.isNull("other")).isFalse();
        assertThat(redisUtil.isNull(null)).isFalse();
    }

    @Test
    void testSetOperations() {
        redisUtil.sAdd("test:set", "a", "b", "c");
        var members = redisUtil.sMembers("test:set", String.class);
        assertThat(members).containsExactlyInAnyOrder("a", "b", "c");

        redisUtil.sRemove("test:set", "b");
        members = redisUtil.sMembers("test:set", String.class);
        assertThat(members).containsExactlyInAnyOrder("a", "c");
    }

    @Test
    void testHashOperations() {
        redisUtil.hSet("test:hash", "field1", "value1");
        assertThat(redisUtil.hGet("test:hash", "field1")).isEqualTo("value1");

        redisUtil.hDelete("test:hash", "field1");
        assertThat(redisUtil.hGet("test:hash", "field1")).isNull();
    }
}
