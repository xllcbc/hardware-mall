package com.example.mystore.common.cache;

/**
 * 一个缓存 namespace 的 key 所有者。
 *
 * <p>key 的拼装只发生在实现类里，回填模块与失效路径都通过它取 key，
 * 避免在调用点拼出一个永远不会被失效删除的 key。</p>
 */
public interface CacheKeys {

    /**
     * 当前代际下的缓存 key。
     */
    String currentKey(long id);

    /**
     * 指定代际下的缓存 key；没有代际概念的 namespace 忽略 {@code generation}。
     */
    String keyFor(long id, String generation);

    /**
     * 当前代际标识；没有代际概念的 namespace 返回固定值。
     */
    String currentGeneration();

    /**
     * 旋转代际。失效路径必须「先旋转代际，再按旧代际取 key 并删除」。
     */
    void rotateGeneration();
}
