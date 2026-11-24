package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.config.ImageGcProperties;
import com.example.mystore.config.OssProperties;
import com.example.mystore.entity.vo.ImageTrashPurgeReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回收站保留期清理：是否删除只看对象键中的回收日期，不看对象最后修改时间。
 */
@ExtendWith(MockitoExtension.class)
class ImageTrashPurgeServiceImplTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private OSS ossClient;

    private ImageGcProperties properties;
    private ImageTrashPurgeServiceImpl service;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setBucketName(BUCKET);
        properties = new ImageGcProperties();
        properties.setMode(ImageGcConstants.Mode.TRASH);
        today = LocalDate.now();
        service = new ImageTrashPurgeServiceImpl(ossClient, ossProperties, properties);
    }

    @Test
    void purge_deletesOnlyObjectsOlderThanRetentionJudgedByTrashPathDate() {
        String expired = trashKey(today.minusDays(31), "products/expired.jpg");
        String atLimit = trashKey(today.minusDays(30), "products/at-limit.jpg");
        String fresh = trashKey(today.minusDays(29), "products/fresh.jpg");
        String future = trashKey(today.plusDays(1), "products/future.jpg");
        // 对象最后修改时间与判定无关：过期对象刚刚写入，未过期对象很久以前写入。
        stubTrash(List.of(
                object(expired, 100, Instant.now()),
                object(atLimit, 200, Instant.now().minus(Duration.ofDays(400))),
                object(fresh, 300, Instant.now().minus(Duration.ofDays(400))),
                object(future, 400, Instant.now())));

        ImageTrashPurgeReport report = service.purgeExpiredTrash();

        assertThat(report.isExecuted()).isTrue();
        assertThat(report.isAborted()).isFalse();
        assertThat(report.getListedObjectCount()).isEqualTo(4);
        assertThat(report.getExpiredObjectCount()).isEqualTo(1);
        assertThat(report.getDeletedObjectCount()).isEqualTo(1);
        assertThat(report.getDeletedBytes()).isEqualTo(100);
        assertThat(report.getFailedObjectCount()).isZero();
        verify(ossClient).deleteObject(BUCKET, expired);
        verify(ossClient, never()).deleteObject(BUCKET, atLimit);
        verify(ossClient, never()).deleteObject(BUCKET, fresh);
        verify(ossClient, never()).deleteObject(BUCKET, future);
    }

    @Test
    void purge_skipsTrashKeysWithoutDatedBatchLayout() {
        stubTrash(List.of(
                object("trash/not-a-date/products/a.jpg", 100, Instant.now()),
                object("trash/" + date(today.minusDays(60)), 100, Instant.now()),
                object("trash/" + date(today.minusDays(60)) + "/", 100, Instant.now()),
                object("trash/" + date(today.minusDays(60)) + "/products/kept.jpg", 100, Instant.now())));

        ImageTrashPurgeReport report = service.purgeExpiredTrash();

        assertThat(report.getSkippedObjectCount()).isEqualTo(3);
        assertThat(report.getDeletedObjectCount()).isEqualTo(1);
        verify(ossClient, never()).deleteObject(BUCKET, "trash/not-a-date/products/a.jpg");
        verify(ossClient, never()).deleteObject(BUCKET, "trash/" + date(today.minusDays(60)));
        verify(ossClient, never()).deleteObject(BUCKET, "trash/" + date(today.minusDays(60)) + "/");
        verify(ossClient).deleteObject(BUCKET, "trash/" + date(today.minusDays(60)) + "/products/kept.jpg");
    }

    @Test
    void purge_treatsRetentionDaysAsConfigurable() {
        properties.setTrashRetentionDays(7);
        String expired = trashKey(today.minusDays(8), "products/expired.jpg");
        String fresh = trashKey(today.minusDays(7), "products/at-limit.jpg");
        stubTrash(List.of(object(expired, 100, Instant.now()), object(fresh, 100, Instant.now())));

        ImageTrashPurgeReport report = service.purgeExpiredTrash();

        assertThat(report.getDeletedObjectCount()).isEqualTo(1);
        verify(ossClient).deleteObject(BUCKET, expired);
        verify(ossClient, never()).deleteObject(BUCKET, fresh);
    }

    @Test
    void purge_doesNothingInReportMode() {
        properties.setMode(ImageGcConstants.Mode.REPORT);

        ImageTrashPurgeReport report = service.purgeExpiredTrash();

        assertThat(report.isExecuted()).isFalse();
        verify(ossClient, never()).listObjects(any(ListObjectsRequest.class));
        verify(ossClient, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void purge_doesNothingWhenGcIsOff() {
        properties.setMode(ImageGcConstants.Mode.OFF);

        ImageTrashPurgeReport report = service.purgeExpiredTrash();

        assertThat(report.isExecuted()).isFalse();
        verify(ossClient, never()).listObjects(any(ListObjectsRequest.class));
        verify(ossClient, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void purge_abortsAndDeletesNothingWhenListingTrashFails() {
        when(ossClient.listObjects(any(ListObjectsRequest.class)))
                .thenThrow(new RuntimeException("oss is down"));

        ImageTrashPurgeReport report = service.purgeExpiredTrash();

        assertThat(report.isExecuted()).isTrue();
        assertThat(report.isAborted()).isTrue();
        assertThat(report.getAbortReason()).contains("列目录");
        assertThat(report.getDeletedObjectCount()).isZero();
        verify(ossClient, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void purge_isIdempotentWhenRetriedAfterSuccess() {
        String expired = trashKey(today.minusDays(31), "products/expired.jpg");
        String fresh = trashKey(today.minusDays(1), "products/fresh.jpg");
        stubStatefulTrash(List.of(object(expired, 100, Instant.now()), object(fresh, 100, Instant.now())));
        deleteRemovesFromStatefulTrash();

        ImageTrashPurgeReport first = service.purgeExpiredTrash();
        ImageTrashPurgeReport second = service.purgeExpiredTrash();

        assertThat(first.getDeletedObjectCount()).isEqualTo(1);
        assertThat(second.getDeletedObjectCount()).isZero();
        assertThat(second.getListedObjectCount()).isEqualTo(1);
        verify(ossClient, times(1)).deleteObject(BUCKET, expired);
        verify(ossClient, never()).deleteObject(BUCKET, fresh);
    }

    @Test
    void purge_retriesOnlyFailedObjectsAndNeverDeletesObjectsStillWithinRetention() {
        String failsThenSucceeds = trashKey(today.minusDays(31), "products/a.jpg");
        String deleted = trashKey(today.minusDays(40), "products/b.jpg");
        String fresh = trashKey(today.minusDays(2), "products/fresh.jpg");
        stubStatefulTrash(List.of(
                object(failsThenSucceeds, 100, Instant.now()),
                object(deleted, 200, Instant.now()),
                object(fresh, 300, Instant.now())));
        boolean[] failOnce = {true};
        doAnswer(invocation -> {
            String key = invocation.getArgument(1);
            if (key.equals(failsThenSucceeds) && failOnce[0]) {
                failOnce[0] = false;
                throw new RuntimeException("delete failed");
            }
            removeFromStatefulTrash(key);
            return null;
        }).when(ossClient).deleteObject(eq(BUCKET), anyString());

        ImageTrashPurgeReport first = service.purgeExpiredTrash();
        ImageTrashPurgeReport second = service.purgeExpiredTrash();

        assertThat(first.getDeletedObjectCount()).isEqualTo(1);
        assertThat(first.getFailedObjectCount()).isEqualTo(1);
        assertThat(second.getDeletedObjectCount()).isEqualTo(1);
        assertThat(second.getFailedObjectCount()).isZero();
        verify(ossClient, times(2)).deleteObject(BUCKET, failsThenSucceeds);
        verify(ossClient, times(1)).deleteObject(BUCKET, deleted);
        verify(ossClient, never()).deleteObject(BUCKET, fresh);
    }

    private void stubTrash(List<OSSObjectSummary> objects) {
        when(ossClient.listObjects(any(ListObjectsRequest.class))).thenAnswer(invocation -> {
            ListObjectsRequest request = invocation.getArgument(0);
            ObjectListing listing = new ObjectListing();
            listing.setObjectSummaries(request.getPrefix().startsWith(ImageGcConstants.Trash.PREFIX)
                    ? objects
                    : List.of());
            listing.setTruncated(false);
            return listing;
        });
    }

    private List<OSSObjectSummary> statefulBucket;

    private void stubStatefulTrash(List<OSSObjectSummary> objects) {
        statefulBucket = new ArrayList<>(objects);
        when(ossClient.listObjects(any(ListObjectsRequest.class))).thenAnswer(invocation -> {
            ObjectListing listing = new ObjectListing();
            listing.setObjectSummaries(new ArrayList<>(statefulBucket));
            listing.setTruncated(false);
            return listing;
        });
    }

    private void deleteRemovesFromStatefulTrash() {
        doAnswer(invocation -> {
            removeFromStatefulTrash(invocation.getArgument(1));
            return null;
        }).when(ossClient).deleteObject(eq(BUCKET), anyString());
    }

    private void removeFromStatefulTrash(String key) {
        if (statefulBucket != null) {
            statefulBucket.removeIf(object -> key.equals(object.getKey()));
        }
    }

    private String trashKey(LocalDate batchDate, String originalKey) {
        return ImageGcConstants.Trash.PREFIX + date(batchDate) + "/" + originalKey;
    }

    private static String date(LocalDate date) {
        return date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static OSSObjectSummary object(String key, long size, Instant lastModified) {
        OSSObjectSummary summary = new OSSObjectSummary();
        summary.setKey(key);
        summary.setSize(size);
        summary.setLastModified(Date.from(lastModified));
        return summary;
    }
}
