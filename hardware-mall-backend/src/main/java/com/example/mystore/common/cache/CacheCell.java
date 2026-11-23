package com.example.mystore.common.cache;

import java.util.Collection;
import java.util.Map;

/**
 * 一次批量回填所需的缓存单元。
 *
 * <p>由 owner 在每次调用时用「key 所有者 + 策略 + 解码器 + 传入的回源函数」临时组装，
 * 不新增常驻 bean，也不让模块感知业务类型。</p>
 *
 * @param <T> 缓存值类型
 */
public interface CacheCell<T> {

    /**
     * 本单元的缓存策略。
     */
    CachePolicy policy();

    /**
     * 本单元 key 的所有者。
     */
    CacheKeys keys();

    /**
     * 解码缓存值；无法解码（类型不符的脏值）返回 {@code null}，由模块按未命中处理。
     * 空值哨兵在解码前已被模块识别，不会传入。
     */
    T decode(Object cached);

    /**
     * 批量回源。返回值以 ID 为键；确认不存在的 ID 不出现在结果里，由模块写入空值哨兵。
     */
    Map<Long, T> loadFromSource(Collection<Long> ids);
}
