package com.example.mystore.service;

import com.example.mystore.entity.vo.ImageGcReport;

/**
 * 图片回收：以数据库为唯一真相源，计算白名单前缀下“超过宽限期且零引用”的图片对象。
 */
public interface ImageGcService {

    /**
     * 运行一次图片回收。{@code report} 模式只输出报告，不复制、不删除任何对象；
     * {@code trash} 模式把候选对象复制到回收站后删除原对象；列目录或读取引用失败时整轮中止。
     */
    ImageGcReport runOnce();

    /**
     * 从回收站恢复单张图片。入参为回收站对象键 {@code trash/<yyyyMMdd>/<原对象键>}：
     * 复制回原对象键，确认可访问后删除回收站副本。复制失败或恢复后不可访问时保留回收站副本并返回 false。
     * 运维通过启动参数 {@code image.gc.restore-object-key} 触发，不新增 HTTP 接口。
     */
    boolean restoreFromTrash(String trashObjectKey);
}
