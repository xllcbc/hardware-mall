package com.example.mystore.common.constant;

import java.util.List;

/**
 * 图片回收域常量：扫描前缀、告警类型与运行模式。
 * 与微信/支付协议常量物理隔离，此处只表达本系统图片回收语义。
 */
public final class ImageGcConstants {

    private ImageGcConstants() {
    }

    /** 对象键前缀 */
    public static final class Prefix {

        private Prefix() {
        }

        /** 白名单前缀：只有这三个目录下的对象参与扫描。 */
        public static final List<String> SCAN = List.of("products/", "avatars/", "banners/");
    }

    /** 钉钉告警类型 */
    public static final class Alert {

        private Alert() {
        }

        public static final String ABORTED = "IMAGE_GC_ABORTED";
        public static final String FAILED = "IMAGE_GC_FAILED";
    }

    /** 运行模式 */
    public enum Mode {
        /** 关闭：任务不列目录、不读库。 */
        OFF,
        /** 只读报告：只输出统计与样例，不复制、不删除。 */
        REPORT
    }
}
