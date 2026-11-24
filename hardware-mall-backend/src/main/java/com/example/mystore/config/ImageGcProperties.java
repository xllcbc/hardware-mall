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
}
