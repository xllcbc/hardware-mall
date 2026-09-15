package com.example.mystore.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisIntegrationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    /**
     * 回归: incrWithExpire 必须真的设上 TTL。
     * 曾经因为用 Fastjson2 value 序列化器执行 Lua, 脚本参数 "60" 变成带引号的 JSON,
     * 导致 EXPIRE 报错且仅在首次(v==1)触发 → key 永久无 TTL、限流计数只涨不清。
     */
    @Test
    void testIncrWithExpire_setsTtlOnFirstCall() {
        long current = redisUtil.incrWithExpire("test:incr-expire", 60);

        assertThat(current).isEqualTo(1);
        assertThat(redisUtil.getExpire("test:incr-expire")).isGreaterThan(0);
    }

    /** 回归: 无 TTL 的历史 key(模拟旧版本遗留)在下次自增时被兜底补上 TTL。 */
    @Test
    void testIncrWithExpire_healsKeyWithoutTtl() {
        stringRedisTemplate.opsForValue().set("test:incr-expire-heal", "5");
        // 无 TTL(原始 TTL = -1; 此版本 Spring 的 getExpire 对无 TTL 返回 0)
        assertThat(redisUtil.getExpire("test:incr-expire-heal")).isLessThanOrEqualTo(0);

        long current = redisUtil.incrWithExpire("test:incr-expire-heal", 60);

        assertThat(current).isEqualTo(6);
        assertThat(redisUtil.getExpire("test:incr-expire-heal")).isGreaterThan(0);
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
