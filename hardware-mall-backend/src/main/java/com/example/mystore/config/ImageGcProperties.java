package com.example.mystore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片回收任务配置。默认关闭，首次上线只开启 {@link Mode#REPORT} 观察报告，确认无误后再启用 {@link Mode#TRASH}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "image.gc")
public class ImageGcProperties {

    public enum Mode {
        /** 关闭：任务不列目录、不读库。 */
        OFF,
        /** 只读报告：只输出统计与样例，不复制、不删除。 */
        REPORT,
        /** 回收站：先复制到 trash/ 再删除原对象。 */
        TRASH
    }

    /** 默认关闭，避免新环境首次启动即扫描 Bucket。 */
    private Mode mode = Mode.OFF;

    /** 宽限期：对象最后修改时间早于“当前时间减该天数”才可能被回收。 */
    private int graceDays = 7;

    /** 每页列目录大小。 */
    private int listPageSize = 1000;

    /** 报告中样例对象键的最大数量。 */
    private int sampleLimit = 20;
}
