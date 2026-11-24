package com.example.mystore.service;

import com.example.mystore.entity.vo.ImageTrashPurgeReport;

/**
 * 回收站保留期清理：物理删除回收站中路径日期超过保留期的副本。
 */
public interface ImageTrashPurgeService {

    /**
     * 运行一次回收站清理。只删除 {@code trash/<yyyyMMdd>/...} 中日期超过保留期的对象，
     * 是否过期只看路径日期、不看对象最后修改时间；未过期副本永久保留。
     * 非 {@code trash} 模式或 GC 关闭时不执行任何 OSS 操作。
     */
    ImageTrashPurgeReport purgeExpiredTrash();
}
