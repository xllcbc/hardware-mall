package com.example.mystore.service;

import com.example.mystore.entity.vo.ImageGcReport;

/**
 * 图片回收：以数据库为唯一真相源，计算白名单前缀下“超过宽限期且零引用”的图片对象。
 */
public interface ImageGcService {

    /**
     * 运行一次图片回收。{@code report} 模式只输出报告，不复制、不删除任何对象；
     * 列目录或读取引用失败时整轮中止，返回带中止原因的报告。
     */
    ImageGcReport runOnce();
}
