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
        /** 单次回收量超过配置阈值。 */
        public static final String THRESHOLD = "IMAGE_GC_THRESHOLD";
        /** 回收站清理失败、中止或部分删除失败（失败对象下一轮重试）。 */
        public static final String PURGE_FAILED = "IMAGE_TRASH_PURGE_FAILED";
        /** 单次回收站清理量超过配置阈值。 */
        public static final String PURGE_THRESHOLD = "IMAGE_TRASH_PURGE_THRESHOLD";
    }

    /** 回收站对象键布局：trash/&lt;yyyyMMdd&gt;/&lt;原对象键&gt;。该前缀永不参与扫描。 */
    public static final class Trash {

        private Trash() {
        }

        public static final String PREFIX = "trash/";
    }

    /** 运行模式 */
    public enum Mode {
        /** 关闭：任务不列目录、不读库。 */
        OFF,
        /** 只读报告：只输出统计与样例，不复制、不删除。 */
        REPORT,
        /** 回收：先复制到回收站，复制成功后才删除原对象。 */
        TRASH
    }
}
