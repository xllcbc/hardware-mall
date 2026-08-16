package com.example.mystore.util;

public class PageUtil {

    private PageUtil() {
    }

    /**
     * 裁剪分页 limit 到安全范围，防止超大分页拖垮数据库
     *
     * @param limit 请求传入的 limit，可能为 null
     * @param def   默认值
     * @param max   最大值
     * @return 裁剪后的 limit
     */
    public static int clampLimit(Integer limit, int def, int max) {
        if (limit == null) {
            return def;
        }
        if (limit < 1) {
            return def;
        }
        return Math.min(limit, max);
    }
}
