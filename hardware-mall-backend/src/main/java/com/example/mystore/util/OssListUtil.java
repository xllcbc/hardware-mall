package com.example.mystore.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;

import java.util.ArrayList;
import java.util.List;

/**
 * OSS 列目录工具：按前缀翻页取全量对象摘要。
 */
public final class OssListUtil {

    private static final int DEFAULT_PAGE_SIZE = 1000;

    private OssListUtil() {
    }

    public static List<OSSObjectSummary> listAll(OSS ossClient, String bucketName, String prefix) {
        List<OSSObjectSummary> objects = new ArrayList<>();
        String marker = null;
        do {
            ListObjectsRequest request = new ListObjectsRequest(bucketName);
            request.setPrefix(prefix);
            request.setMaxKeys(DEFAULT_PAGE_SIZE);
            if (marker != null) {
                request.setMarker(marker);
            }
            ObjectListing listing = ossClient.listObjects(request);
            objects.addAll(listing.getObjectSummaries());
            marker = listing.isTruncated() ? listing.getNextMarker() : null;
        } while (marker != null);
        return objects;
    }
}
