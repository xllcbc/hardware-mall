package com.example.mystore.entity.vo;

import com.example.mystore.common.constant.ImageGcConstants;
import lombok.Builder;
import lombok.Value;

/**
 * 一次回收站保留期清理的只读报告。清理任务通过结构化日志输出该报告，不对外暴露 HTTP 接口。
 */
@Value
@Builder
public class ImageTrashPurgeReport {

    /** 本轮是否真正执行了扫描；mode 不是 trash 时为 false。 */
    boolean executed;

    /** 本轮回收站模式，只可能是 trash（其他模式直接跳过）。 */
    ImageGcConstants.Mode mode;

    /** 本轮是否因列目录失败而中止；中止时不删除任何对象。 */
    boolean aborted;

    /** 中止原因，未中止时为 null。 */
    String abortReason;

    /** 回收站下扫描到的对象数。 */
    int listedObjectCount;

    /** 其中路径日期超过保留期的对象数。 */
    int expiredObjectCount;

    /** 实际删除成功的对象数。 */
    int deletedObjectCount;

    /** 实际删除成功的对象总字节数。 */
    long deletedBytes;

    /** 删除失败的对象数；失败对象保留到下一轮重试。 */
    int failedObjectCount;

    /** 回收站路径不符合 {@code trash/<yyyyMMdd>/<原对象键>} 布局、因而跳过的对象数。 */
    int skippedObjectCount;

    public static ImageTrashPurgeReport notExecuted(ImageGcConstants.Mode mode) {
        return ImageTrashPurgeReport.builder()
                .executed(false)
                .mode(mode)
                .build();
    }
}
