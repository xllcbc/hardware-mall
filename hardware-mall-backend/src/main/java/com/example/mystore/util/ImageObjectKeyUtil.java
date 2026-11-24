package com.example.mystore.util;

import com.example.mystore.common.constant.ImageGcConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 图片对象键的纯函数工具：白名单前缀判断与回收站键布局解析。
 * 回收站布局固定为 {@code trash/<yyyyMMdd>/<原对象键>}，解析失败时一律返回 empty 让调用方保守处理。
 */
public final class ImageObjectKeyUtil {

    private ImageObjectKeyUtil() {
    }

    /** 是否落在允许扫描/恢复的白名单前缀下。 */
    public static boolean isWhitelisted(String objectKey) {
        if (objectKey == null) {
            return false;
        }
        return ImageGcConstants.Prefix.SCAN.stream().anyMatch(objectKey::startsWith);
    }

    /** 解析回收批次日期；布局不完整或日期非法时返回 empty。 */
    public static Optional<LocalDate> trashBatchDate(String trashObjectKey) {
        if (trashObjectKey == null || !trashObjectKey.startsWith(ImageGcConstants.Trash.PREFIX)) {
            return Optional.empty();
        }
        String rest = trashObjectKey.substring(ImageGcConstants.Trash.PREFIX.length());
        int separator = rest.indexOf('/');
        if (separator <= 0 || separator == rest.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(
                    rest.substring(0, separator), DateTimeFormatter.BASIC_ISO_DATE));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * 从回收站键解析原对象键：批次日期必须合法，且原对象键必须落在白名单前缀下。
     */
    public static Optional<String> originalKeyFromTrash(String trashObjectKey) {
        if (trashBatchDate(trashObjectKey).isEmpty()) {
            return Optional.empty();
        }
        String rest = trashObjectKey.substring(ImageGcConstants.Trash.PREFIX.length());
        String originalKey = rest.substring(rest.indexOf('/') + 1);
        return isWhitelisted(originalKey) ? Optional.of(originalKey) : Optional.empty();
    }
}
