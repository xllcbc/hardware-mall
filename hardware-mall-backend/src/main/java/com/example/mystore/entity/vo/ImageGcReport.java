package com.example.mystore.entity.vo;

import com.example.mystore.common.constant.ImageGcConstants;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 一次图片回收任务的只读报告。任务通过结构化日志输出该报告，不对外暴露 HTTP 接口。
 */
@Value
@Builder
public class ImageGcReport {

    /** 本轮是否真正执行了扫描；mode=off 时为 false。 */
    boolean executed;

    /** 本轮模式：off / report / trash。 */
    ImageGcConstants.Mode mode;

    /** 本轮是否因失败而中止；中止时不复制、不删除任何对象。 */
    boolean aborted;

    /** 中止原因，未中止时为 null。 */
    String abortReason;

    /** 白名单前缀下扫描到的对象数。 */
    int scannedObjectCount;

    /** 本轮从数据库成功映射到对象键的引用数（重复引用重复计数）。 */
    int mappedReferenceCount;

    /** 扫描对象中至少被一个数据库引用指向的对象数。 */
    int referencedObjectCount;

    /** 扫描对象中未被任何数据库引用指向的对象数（包含仍在宽限期内的对象）。 */
    int unreferencedObjectCount;

    /** 超过宽限期且零引用的候选对象数。 */
    int candidateCount;

    /** 候选对象可回收字节数。 */
    long reclaimableBytes;

    /** 候选对象键样例，按对象键排序后截断。 */
    List<String> sampleObjectKeys;

    /** trash 模式下成功复制到回收站并删除原对象的数量；report 模式恒为 0。 */
    int trashedObjectCount;

    /** trash 模式下复制或删除失败、原对象因此保留的数量。 */
    int trashFailedObjectCount;

    /** trash 模式下成功移入回收站的对象总字节数；report 模式恒为 0。 */
    long trashedBytes;

    public static ImageGcReport notExecuted(ImageGcConstants.Mode mode) {
        return ImageGcReport.builder()
                .executed(false)
                .mode(mode)
                .sampleObjectKeys(List.of())
                .build();
    }
}
