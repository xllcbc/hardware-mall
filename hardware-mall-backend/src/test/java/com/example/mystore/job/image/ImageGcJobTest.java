package com.example.mystore.job.image;

import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.config.ImageGcProperties;
import com.example.mystore.entity.vo.ImageGcReport;
import com.example.mystore.service.ImageGcService;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.util.RedisLockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageGcJobTest {

    @Mock
    private ImageGcService imageGcService;
    @Mock
    private RedisLockUtil redisLockUtil;
    @Mock
    private DingTalkAlertService dingTalkAlertService;

    private ImageGcProperties properties;
    private ImageGcJob job;

    @BeforeEach
    void setUp() {
        properties = new ImageGcProperties();
        job = new ImageGcJob(imageGcService, redisLockUtil, dingTalkAlertService, properties);
    }

    @Test
    void runWeeklyReport_skipsWhenAnotherInstanceHoldsTheLock() {
        when(redisLockUtil.tryLock(ImageGcJob.LOCK_KEY)).thenReturn(false);

        job.runWeeklyReport();

        verifyNoInteractions(imageGcService);
        verify(redisLockUtil, never()).unlock(ImageGcJob.LOCK_KEY);
    }

    @Test
    void runWeeklyReport_runsOnceAndReleasesTheLock() {
        when(redisLockUtil.tryLock(ImageGcJob.LOCK_KEY)).thenReturn(true);
        when(imageGcService.runOnce()).thenReturn(report(true, false, null));

        job.runWeeklyReport();

        verify(imageGcService).runOnce();
        verify(redisLockUtil).unlock(ImageGcJob.LOCK_KEY);
        verifyNoInteractions(dingTalkAlertService);
    }

    @Test
    void runWeeklyReport_doesNotAlertWhenGcIsDisabled() {
        when(redisLockUtil.tryLock(ImageGcJob.LOCK_KEY)).thenReturn(true);
        when(imageGcService.runOnce()).thenReturn(report(false, false, null));

        job.runWeeklyReport();

        verify(redisLockUtil).unlock(ImageGcJob.LOCK_KEY);
        verifyNoInteractions(dingTalkAlertService);
    }

    @Test
    void runWeeklyReport_alertsAndDoesNotThrowWhenServiceFails() {
        when(redisLockUtil.tryLock(ImageGcJob.LOCK_KEY)).thenReturn(true);
        when(imageGcService.runOnce()).thenThrow(new RuntimeException("bucket unreachable"));

        assertThatCode(() -> job.runWeeklyReport()).doesNotThrowAnyException();

        verify(dingTalkAlertService).alert(eq(ImageGcConstants.Alert.FAILED), contains("bucket unreachable"));
        verify(redisLockUtil).unlock(ImageGcJob.LOCK_KEY);
    }

    @Test
    void runWeeklyReport_alertsWhenReportAborts() {
        when(redisLockUtil.tryLock(ImageGcJob.LOCK_KEY)).thenReturn(true);
        when(imageGcService.runOnce()).thenReturn(report(true, true, "列目录失败，已中止"));

        assertThatCode(() -> job.runWeeklyReport()).doesNotThrowAnyException();

        verify(dingTalkAlertService).alert(eq(ImageGcConstants.Alert.ABORTED), contains("列目录失败"));
        verify(redisLockUtil).unlock(ImageGcJob.LOCK_KEY);
    }

    @Test
    void runWeeklyReport_alertsWhenTrashedBytesExceedThreshold() {
        properties.setAlertMaxBytes(1_000);
        properties.setAlertMaxCount(100);
        when(redisLockUtil.tryLock(ImageGcJob.LOCK_KEY)).thenReturn(true);
        when(imageGcService.runOnce()).thenReturn(trashReport(2, 2_000));

        job.runWeeklyReport();

        verify(dingTalkAlertService).alert(eq(ImageGcConstants.Alert.THRESHOLD), contains("2000"));
        verify(redisLockUtil).unlock(ImageGcJob.LOCK_KEY);
    }

    @Test
    void runWeeklyReport_alertsWhenTrashedObjectCountExceedsThreshold() {
        properties.setAlertMaxBytes(1_000_000);
        properties.setAlertMaxCount(2);
        when(redisLockUtil.tryLock(ImageGcJob.LOCK_KEY)).thenReturn(true);
        when(imageGcService.runOnce()).thenReturn(trashReport(3, 500));

        job.runWeeklyReport();

        verify(dingTalkAlertService).alert(eq(ImageGcConstants.Alert.THRESHOLD), contains("3"));
        verify(redisLockUtil).unlock(ImageGcJob.LOCK_KEY);
    }

    @Test
    void runWeeklyReport_doesNotAlertWhenTrashVolumeIsBelowThreshold() {
        properties.setAlertMaxBytes(1_000_000);
        properties.setAlertMaxCount(100);
        when(redisLockUtil.tryLock(ImageGcJob.LOCK_KEY)).thenReturn(true);
        when(imageGcService.runOnce()).thenReturn(trashReport(3, 500));

        job.runWeeklyReport();

        verifyNoInteractions(dingTalkAlertService);
        verify(redisLockUtil).unlock(ImageGcJob.LOCK_KEY);
    }

    @Test
    void runWeeklyReport_treatsNonPositiveThresholdAsDisabled() {
        properties.setAlertMaxBytes(0);
        properties.setAlertMaxCount(0);
        when(redisLockUtil.tryLock(ImageGcJob.LOCK_KEY)).thenReturn(true);
        when(imageGcService.runOnce()).thenReturn(trashReport(999, 999_999_999));

        job.runWeeklyReport();

        verifyNoInteractions(dingTalkAlertService);
        verify(redisLockUtil).unlock(ImageGcJob.LOCK_KEY);
    }

    private static ImageGcReport report(boolean executed, boolean aborted, String abortReason) {
        return ImageGcReport.builder()
                .executed(executed)
                .mode(ImageGcConstants.Mode.REPORT)
                .aborted(aborted)
                .abortReason(abortReason)
                .scannedObjectCount(5)
                .mappedReferenceCount(4)
                .referencedObjectCount(4)
                .unreferencedObjectCount(1)
                .candidateCount(1)
                .reclaimableBytes(1234)
                .sampleObjectKeys(List.of("products/orphan.jpg"))
                .build();
    }

    private static ImageGcReport trashReport(int trashedCount, long trashedBytes) {
        return ImageGcReport.builder()
                .executed(true)
                .mode(ImageGcConstants.Mode.TRASH)
                .aborted(false)
                .scannedObjectCount(10)
                .mappedReferenceCount(4)
                .referencedObjectCount(4)
                .unreferencedObjectCount(6)
                .candidateCount(trashedCount)
                .reclaimableBytes(trashedBytes)
                .sampleObjectKeys(List.of())
                .trashedObjectCount(trashedCount)
                .trashedBytes(trashedBytes)
                .build();
    }
}
