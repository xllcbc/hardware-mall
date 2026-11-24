package com.example.mystore.config;

import com.example.mystore.common.constant.ImageGcConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片回收任务配置。默认关闭，首次上线只开启 {@link ImageGcConstants.Mode#REPORT} 观察报告。
 */
@Data
@Component
@ConfigurationProperties(prefix = "image.gc")
public class ImageGcProperties {

    /** 默认关闭，避免新环境首次启动即扫描 Bucket。 */
    private ImageGcConstants.Mode mode = ImageGcConstants.Mode.OFF;

    /** 宽限期：对象最后修改时间早于“当前时间减该天数”才可能被回收。 */
    private int graceDays = 7;

    /** 回收站保留天数：只按回收路径中的批次日期判断，超过该天数的副本由每日清理任务物理删除。 */
    private int trashRetentionDays = 30;

    /** 单轮删除字节数告警阈值；小于等于 0 表示关闭该维度。 */
    private long alertMaxBytes = 200L * 1024 * 1024;

    /** 单轮删除对象数告警阈值；小于等于 0 表示关闭该维度。 */
    private int alertMaxCount = 200;

    /**
     * 单轮删除量（回收模式移入回收站、或回收站清理物理删除）是否超过任一告警阈值。
     */
    public boolean exceedsDeleteThreshold(long bytes, int count) {
        boolean bytesExceeded = alertMaxBytes > 0 && bytes > alertMaxBytes;
        boolean countExceeded = alertMaxCount > 0 && count > alertMaxCount;
        return bytesExceeded || countExceeded;
    }
}
