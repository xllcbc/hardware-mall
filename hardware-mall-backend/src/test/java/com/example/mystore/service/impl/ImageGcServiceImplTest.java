package com.example.mystore.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.config.ImageGcProperties;
import com.example.mystore.config.OssProperties;
import com.example.mystore.entity.db.OrderItem;
import com.example.mystore.entity.db.Sku;
import com.example.mystore.entity.db.Spu;
import com.example.mystore.entity.db.User;
import com.example.mystore.entity.vo.ImageGcReport;
import com.example.mystore.mapper.OrderItemMapper;
import com.example.mystore.mapper.SkuMapper;
import com.example.mystore.mapper.SpuMapper;
import com.example.mystore.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageGcServiceImplTest {

    private static final String DOMAIN = "https://cdn.example.com";

    @Mock
    private OSS ossClient;
    @Mock
    private SpuMapper spuMapper;
    @Mock
    private SkuMapper skuMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private UserMapper userMapper;

    private ImageGcProperties properties;
    private ImageGcServiceImpl service;

    @BeforeEach
    void setUp() {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setBucketName("test-bucket");
        properties = new ImageGcProperties();
        properties.setMode(ImageGcConstants.Mode.REPORT);
        service = new ImageGcServiceImpl(
                ossClient, ossProperties, spuMapper, skuMapper, orderItemMapper, userMapper, properties);

        lenient().when(spuMapper.selectList(any())).thenReturn(List.of());
        lenient().when(skuMapper.selectList(any())).thenReturn(List.of());
        lenient().when(orderItemMapper.selectList(any())).thenReturn(List.of());
        lenient().when(userMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void report_countsScannedUnreferencedCandidatesAndBytesFromEveryReferenceSource() {
        Instant old = Instant.now().minus(Duration.ofDays(8));
        Instant fresh = Instant.now().minus(Duration.ofHours(1));
        stubBucket(Map.of(
                "products/", List.of(
                        object("products/spu.jpg", 100, old),
                        object("products/sku.jpg", 100, old),
                        object("products/order.jpg", 100, old),
                        object("products/orphan-old.jpg", 1000, old),
                        object("products/orphan-new.jpg", 2000, fresh)),
                "avatars/", List.of(
                        object("avatars/user.jpg", 100, old),
                        object("avatars/orphan.jpg", 3000, old)),
                "banners/", List.of(
                        object("banners/orphan.jpg", 4000, old))));

        when(spuMapper.selectList(any())).thenReturn(List.of(spu(DOMAIN + "/products/spu.jpg")));
        when(skuMapper.selectList(any())).thenReturn(List.of(sku(DOMAIN + "/products/sku.jpg")));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem(DOMAIN + "/products/order.jpg")));
        when(userMapper.selectList(any())).thenReturn(List.of(user(DOMAIN + "/avatars/user.jpg")));

        ImageGcReport report = service.runOnce();

        assertThat(report.isExecuted()).isTrue();
        assertThat(report.isAborted()).isFalse();
        assertThat(report.getScannedObjectCount()).isEqualTo(8);
        assertThat(report.getMappedReferenceCount()).isEqualTo(4);
        assertThat(report.getReferencedObjectCount()).isEqualTo(4);
        assertThat(report.getUnreferencedObjectCount()).isEqualTo(4);
        assertThat(report.getCandidateCount()).isEqualTo(3);
        assertThat(report.getReclaimableBytes()).isEqualTo(8000);
        assertThat(report.getSampleObjectKeys()).containsExactly(
                "avatars/orphan.jpg", "banners/orphan.jpg", "products/orphan-old.jpg");
        verify(ossClient, never()).deleteObject(anyString(), anyString());
        verify(ossClient, never()).copyObject(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void report_abortsWhenNoReferenceMapsToAnObjectKey() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        stubBucket(Map.of("products/", List.of(object("products/possibly-everything.jpg", 500, old))));
        when(spuMapper.selectList(any())).thenReturn(List.of(spu("data:image/png;base64,AAAA")));

        ImageGcReport report = service.runOnce();

        assertThat(report.isExecuted()).isTrue();
        assertThat(report.isAborted()).isTrue();
        assertThat(report.getAbortReason()).contains("引用");
        assertThat(report.getMappedReferenceCount()).isZero();
        assertThat(report.getCandidateCount()).isZero();
        verify(ossClient, never()).deleteObject(anyString(), anyString());
        verify(ossClient, never()).copyObject(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void report_abortsAndDeletesNothingWhenListingFails() {
        when(ossClient.listObjects(any(ListObjectsRequest.class)))
                .thenThrow(new RuntimeException("oss is down"));

        ImageGcReport report = service.runOnce();

        assertThat(report.isExecuted()).isTrue();
        assertThat(report.isAborted()).isTrue();
        assertThat(report.getAbortReason()).contains("列目录");
        assertThat(report.getCandidateCount()).isZero();
        verify(ossClient, never()).deleteObject(anyString(), anyString());
        verify(ossClient, never()).copyObject(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void report_abortsAndDeletesNothingWhenDatabaseReadFails() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        stubBucket(Map.of("products/", List.of(object("products/a.jpg", 500, old))));
        when(spuMapper.selectList(any())).thenThrow(new RuntimeException("db is down"));

        ImageGcReport report = service.runOnce();

        assertThat(report.isExecuted()).isTrue();
        assertThat(report.isAborted()).isTrue();
        assertThat(report.getAbortReason()).contains("引用");
        assertThat(report.getCandidateCount()).isZero();
        verify(ossClient, never()).deleteObject(anyString(), anyString());
        verify(ossClient, never()).copyObject(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void report_onlyScansWhitelistedPrefixesAndNeverScansTrash() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        stubBucket(Map.of("products/", List.of(
                object("products/kept.jpg", 10, old),
                object("trash/20260101/products/old.jpg", 100, old),
                object("backups/database.zip", 100, old))));
        when(spuMapper.selectList(any())).thenReturn(List.of(spu(DOMAIN + "/products/kept.jpg")));

        ImageGcReport report = service.runOnce();

        assertThat(report.getScannedObjectCount()).isEqualTo(1);
        assertThat(report.getCandidateCount()).isZero();

        ArgumentCaptor<ListObjectsRequest> requests = ArgumentCaptor.forClass(ListObjectsRequest.class);
        verify(ossClient, atLeastOnce()).listObjects(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(ListObjectsRequest::getPrefix)
                .containsExactlyInAnyOrder("products/", "avatars/", "banners/")
                .noneMatch(prefix -> prefix.startsWith("trash/"));
    }

    @Test
    void report_treatsOrderImageSnapshotAsReferenceAndNeverOrphan() {
        Instant old = Instant.now().minus(Duration.ofDays(365));
        stubBucket(Map.of("products/", List.of(object("products/snapshot.jpg", 900, old))));
        when(orderItemMapper.selectList(any()))
                .thenReturn(List.of(orderItem(DOMAIN + "/products/snapshot.jpg")));

        ImageGcReport report = service.runOnce();

        assertThat(report.isAborted()).isFalse();
        assertThat(report.getReferencedObjectCount()).isEqualTo(1);
        assertThat(report.getUnreferencedObjectCount()).isZero();
        assertThat(report.getCandidateCount()).isZero();
        assertThat(report.getSampleObjectKeys()).isEmpty();
    }

    @Test
    void trash_movesUnreferencedObjectIntoDatedTrashPrefixThenDeletesOriginal() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        stubBucket(Map.of("products/", List.of(
                object("products/kept.jpg", 10, old),
                object("products/orphan.jpg", 1000, old))));
        when(spuMapper.selectList(any())).thenReturn(List.of(spu(DOMAIN + "/products/kept.jpg")));
        properties.setMode(ImageGcConstants.Mode.TRASH);

        ImageGcReport report = service.runOnce();

        String trashKey = "trash/" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "/products/orphan.jpg";
        InOrder inOrder = inOrder(ossClient);
        inOrder.verify(ossClient).copyObject("test-bucket", "products/orphan.jpg", "test-bucket", trashKey);
        inOrder.verify(ossClient).deleteObject("test-bucket", "products/orphan.jpg");
        verify(ossClient, never()).deleteObject("test-bucket", "products/kept.jpg");
        verify(ossClient, never()).copyObject("test-bucket", "products/kept.jpg", "test-bucket",
                "trash/" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "/products/kept.jpg");
        assertThat(report.isAborted()).isFalse();
        assertThat(report.getCandidateCount()).isEqualTo(1);
        assertThat(report.getTrashedObjectCount()).isEqualTo(1);
        assertThat(report.getTrashFailedObjectCount()).isZero();
    }

    @Test
    void trash_keepsOriginalWhenCopyToTrashFailsAndStillMovesOtherCandidates() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        stubBucket(Map.of("products/", List.of(
                object("products/fails.jpg", 500, old),
                object("products/moves.jpg", 600, old))));
        when(spuMapper.selectList(any()))
                .thenReturn(List.of(spu(DOMAIN + "/products/kept.jpg")));
        properties.setMode(ImageGcConstants.Mode.TRASH);
        String batch = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        when(ossClient.copyObject("test-bucket", "products/fails.jpg", "test-bucket",
                "trash/" + batch + "/products/fails.jpg"))
                .thenThrow(new RuntimeException("copy failed"));

        ImageGcReport report = service.runOnce();

        verify(ossClient, never()).deleteObject("test-bucket", "products/fails.jpg");
        verify(ossClient).deleteObject("test-bucket", "products/moves.jpg");
        assertThat(report.isAborted()).isFalse();
        assertThat(report.getCandidateCount()).isEqualTo(2);
        assertThat(report.getTrashedObjectCount()).isEqualTo(1);
        assertThat(report.getTrashFailedObjectCount()).isEqualTo(1);
    }

    @Test
    void trash_neverMovesObjectsStillReferencedBySpuSkuOrderSnapshotOrAvatar() {
        Instant old = Instant.now().minus(Duration.ofDays(365));
        stubBucket(Map.of(
                "products/", List.of(
                        object("products/spu.jpg", 100, old),
                        object("products/sku.jpg", 100, old),
                        object("products/order.jpg", 100, old)),
                "avatars/", List.of(object("avatars/user.jpg", 100, old))));
        when(spuMapper.selectList(any())).thenReturn(List.of(spu(DOMAIN + "/products/spu.jpg")));
        when(skuMapper.selectList(any())).thenReturn(List.of(sku(DOMAIN + "/products/sku.jpg")));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem(DOMAIN + "/products/order.jpg")));
        when(userMapper.selectList(any())).thenReturn(List.of(user(DOMAIN + "/avatars/user.jpg")));
        properties.setMode(ImageGcConstants.Mode.TRASH);

        ImageGcReport report = service.runOnce();

        assertThat(report.isAborted()).isFalse();
        assertThat(report.getReferencedObjectCount()).isEqualTo(4);
        assertThat(report.getCandidateCount()).isZero();
        assertThat(report.getTrashedObjectCount()).isZero();
        assertThat(report.getTrashFailedObjectCount()).isZero();
        verify(ossClient, never()).copyObject(anyString(), anyString(), anyString(), anyString());
        verify(ossClient, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void restore_copiesTrashObjectBackVerifiesAccessAndRemovesTrashCopy() {
        when(ossClient.doesObjectExist("test-bucket", "products/a.jpg")).thenReturn(true);

        boolean restored = service.restoreFromTrash("trash/20260101/products/a.jpg");

        assertThat(restored).isTrue();
        InOrder inOrder = inOrder(ossClient);
        inOrder.verify(ossClient).copyObject(
                "test-bucket", "trash/20260101/products/a.jpg", "test-bucket", "products/a.jpg");
        inOrder.verify(ossClient).doesObjectExist("test-bucket", "products/a.jpg");
        inOrder.verify(ossClient).deleteObject("test-bucket", "trash/20260101/products/a.jpg");
    }

    @Test
    void restore_keepsTrashCopyWhenCopyBackFails() {
        when(ossClient.copyObject("test-bucket", "trash/20260101/products/a.jpg",
                "test-bucket", "products/a.jpg"))
                .thenThrow(new RuntimeException("copy failed"));

        boolean restored = service.restoreFromTrash("trash/20260101/products/a.jpg");

        assertThat(restored).isFalse();
        verify(ossClient, never()).doesObjectExist(anyString(), anyString());
        verify(ossClient, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void restore_keepsTrashCopyWhenRestoredObjectIsNotAccessible() {
        when(ossClient.doesObjectExist("test-bucket", "products/a.jpg")).thenReturn(false);

        boolean restored = service.restoreFromTrash("trash/20260101/products/a.jpg");

        assertThat(restored).isFalse();
        verify(ossClient, never()).deleteObject(anyString(), anyString());
    }

    @Test
    void restore_rejectsKeysOutsideTrashPrefixOrWithoutDatedBatch() {
        assertThat(service.restoreFromTrash("products/a.jpg")).isFalse();
        assertThat(service.restoreFromTrash("trash/not-a-date/products/a.jpg")).isFalse();
        assertThat(service.restoreFromTrash("trash/20260101/")).isFalse();
        assertThat(service.restoreFromTrash(null)).isFalse();
        verifyNoInteractions(ossClient);
    }

    @Test
    void switchingBackToOff_stopsFurtherTrashActions() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        stubBucket(Map.of("products/", List.of(object("products/orphan.jpg", 1000, old))));
        when(spuMapper.selectList(any())).thenReturn(List.of(spu(DOMAIN + "/products/kept.jpg")));
        properties.setMode(ImageGcConstants.Mode.TRASH);
        String trashKey = "trash/" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "/products/orphan.jpg";

        ImageGcReport first = service.runOnce();
        properties.setMode(ImageGcConstants.Mode.OFF);
        ImageGcReport second = service.runOnce();

        assertThat(first.getTrashedObjectCount()).isEqualTo(1);
        assertThat(second.isExecuted()).isFalse();
        assertThat(second.getMode()).isEqualTo(ImageGcConstants.Mode.OFF);
        verify(ossClient, times(1)).copyObject(
                "test-bucket", "products/orphan.jpg", "test-bucket", trashKey);
        verify(ossClient, times(1)).deleteObject("test-bucket", "products/orphan.jpg");
    }

    @Test
    void offMode_doesNotTouchOssOrDatabase() {
        properties.setMode(ImageGcConstants.Mode.OFF);

        ImageGcReport report = service.runOnce();

        assertThat(report.isExecuted()).isFalse();
        assertThat(report.getMode()).isEqualTo(ImageGcConstants.Mode.OFF);
        verifyNoInteractions(ossClient, spuMapper, skuMapper, orderItemMapper, userMapper);
    }

    @Test
    void report_countsDuplicateReferencesAsMappedInsteadOfUnmapped() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        stubBucket(Map.of("products/", List.of(object("products/shared.jpg", 10, old))));
        when(spuMapper.selectList(any())).thenReturn(List.of(
                spu(DOMAIN + "/products/shared.jpg"),
                spu(DOMAIN + "/products/shared.jpg")));

        ImageGcReport report = service.runOnce();

        assertThat(report.isAborted()).isFalse();
        assertThat(report.getMappedReferenceCount()).isEqualTo(2);
        assertThat(report.getReferencedObjectCount()).isEqualTo(1);
        assertThat(report.getCandidateCount()).isZero();
    }

    @Test
    void report_reconfirmsCandidatesAgainstTheLatestReferenceSnapshot() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        stubBucket(Map.of("products/", List.of(object("products/race.jpg", 700, old))));
        when(spuMapper.selectList(any()))
                .thenReturn(List.of(spu(DOMAIN + "/products/other.jpg")))
                .thenReturn(List.of(
                        spu(DOMAIN + "/products/other.jpg"),
                        spu(DOMAIN + "/products/race.jpg")));

        ImageGcReport report = service.runOnce();

        assertThat(report.isAborted()).isFalse();
        assertThat(report.getReferencedObjectCount()).isEqualTo(1);
        assertThat(report.getUnreferencedObjectCount()).isZero();
        assertThat(report.getCandidateCount()).isZero();
        assertThat(report.getReclaimableBytes()).isZero();
    }

    private void stubBucket(Map<String, List<OSSObjectSummary>> objectsByPrefix) {
        when(ossClient.listObjects(any(ListObjectsRequest.class))).thenAnswer(invocation -> {
            ListObjectsRequest request = invocation.getArgument(0);
            ObjectListing listing = new ObjectListing();
            listing.setObjectSummaries(objectsByPrefix.getOrDefault(request.getPrefix(), List.of()));
            listing.setTruncated(false);
            return listing;
        });
    }

    private static OSSObjectSummary object(String key, long size, Instant lastModified) {
        OSSObjectSummary summary = new OSSObjectSummary();
        summary.setKey(key);
        summary.setSize(size);
        summary.setLastModified(Date.from(lastModified));
        return summary;
    }

    private static Spu spu(String imageUrl) {
        Spu spu = new Spu();
        spu.setImages(List.of(imageUrl));
        return spu;
    }

    private static Sku sku(String imageUrl) {
        Sku sku = new Sku();
        sku.setImage(imageUrl);
        return sku;
    }

    private static OrderItem orderItem(String imageUrl) {
        OrderItem item = new OrderItem();
        item.setProductImage(imageUrl);
        return item;
    }

    private static User user(String avatarUrl) {
        User user = new User();
        user.setAvatarUrl(avatarUrl);
        return user;
    }
}
