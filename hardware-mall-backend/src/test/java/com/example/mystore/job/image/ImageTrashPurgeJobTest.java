package com.example.mystore.job.image;

import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.config.ImageGcProperties;
import com.example.mystore.entity.vo.ImageTrashPurgeReport;
import com.example.mystore.service.ImageTrashPurgeService;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.util.RedisLockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageTrashPurgeJobTest {

    @Mock
    private ImageTrashPurgeService purgeService;
    @Mock
    private RedisLockUtil redisLockUtil;
    @Mock
    private DingTalkAlertService dingTalkAlertService;

    private ImageGcProperties properties;
    private ImageTrashPurgeJob job;

    @BeforeEach
    void setUp() {
        properties = new ImageGcProperties();
        job = new ImageTrashPurgeJob(purgeService, redisLockUtil, dingTalkAlertService, properties);
    }

    @Test
    void purge_skipsWhenAnotherInstanceHoldsTheLock() {
        when(redisLockUtil.tryLock(ImageTrashPurgeJob.LOCK_KEY)).thenReturn(false);

        job.purgeExpiredTrash();

        verifyNoInteractions(purgeService);
        verify(redisLockUtil, never()).unlock(ImageTrashPurgeJob.LOCK_KEY);
    }

    @Test
    void purge_runsOnceAndReleasesTheLock() {
        when(redisLockUtil.tryLock(ImageTrashPurgeJob.LOCK_KEY)).thenReturn(true);
        when(purgeService.purgeExpiredTrash()).thenReturn(report(3, 300, 0));

        job.purgeExpiredTrash();

        verify(purgeService).purgeExpiredTrash();
        verify(redisLockUtil).unlock(ImageTrashPurgeJob.LOCK_KEY);
        verifyNoInteractions(dingTalkAlertService);
    }

    @Test
    void purge_doesNotAlertWhenGcIsOff() {
        when(redisLockUtil.tryLock(ImageTrashPurgeJob.LOCK_KEY)).thenReturn(true);
        when(purgeService.purgeExpiredTrash())
                .thenReturn(ImageTrashPurgeReport.notExecuted(ImageGcConstants.Mode.OFF));

        job.purgeExpiredTrash();

        verify(redisLockUtil).unlock(ImageTrashPurgeJob.LOCK_KEY);
        verifyNoInteractions(dingTalkAlertService);
    }

    @Test
    void purge_alertsWhenReportAborts() {
        when(redisLockUtil.tryLock(ImageTrashPurgeJob.LOCK_KEY)).thenReturn(true);
        when(purgeService.purgeExpiredTrash()).thenReturn(ImageTrashPurgeReport.builder()
                .executed(true)
                .mode(ImageGcConstants.Mode.TRASH)
                .aborted(true)
                .abortReason("列目录失败，已中止")
                .build());

        assertThatCode(() -> job.purgeExpiredTrash()).doesNotThrowAnyException();

        verify(dingTalkAlertService).alert(eq(ImageGcConstants.Alert.PURGE_FAILED), contains("列目录失败"));
        verify(redisLockUtil).unlock(ImageTrashPurgeJob.LOCK_KEY);
    }

    @Test
    void purge_alertsWhenSomeDeletesFailSoTheyCanBeRetried() {
        when(redisLockUtil.tryLock(ImageTrashPurgeJob.LOCK_KEY)).thenReturn(true);
        when(purgeService.purgeExpiredTrash()).thenReturn(report(2, 200, 1));

        job.purgeExpiredTrash();

        verify(dingTalkAlertService).alert(eq(ImageGcConstants.Alert.PURGE_FAILED), contains("1"));
        verify(redisLockUtil).unlock(ImageTrashPurgeJob.LOCK_KEY);
    }

    @Test
    void purge_alertsAndDoesNotThrowWhenServiceFails() {
        when(redisLockUtil.tryLock(ImageTrashPurgeJob.LOCK_KEY)).thenReturn(true);
        when(purgeService.purgeExpiredTrash()).thenThrow(new RuntimeException("bucket unreachable"));

        assertThatCode(() -> job.purgeExpiredTrash()).doesNotThrowAnyException();

        verify(dingTalkAlertService).alert(eq(ImageGcConstants.Alert.PURGE_FAILED), contains("bucket unreachable"));
        verify(redisLockUtil).unlock(ImageTrashPurgeJob.LOCK_KEY);
    }

    @Test
    void purge_alertsWhenDeletedVolumeExceedsThreshold() {
        properties.setAlertMaxBytes(1_000);
        properties.setAlertMaxCount(100);
        when(redisLockUtil.tryLock(ImageTrashPurgeJob.LOCK_KEY)).thenReturn(true);
        when(purgeService.purgeExpiredTrash()).thenReturn(report(2, 2_000, 0));

        job.purgeExpiredTrash();

        verify(dingTalkAlertService).alert(eq(ImageGcConstants.Alert.PURGE_THRESHOLD), contains("2000"));
    }

    @Test
    void purge_doesNotAlertWhenDeletedVolumeIsBelowThreshold() {
        properties.setAlertMaxBytes(1_000_000);
        properties.setAlertMaxCount(100);
        when(redisLockUtil.tryLock(ImageTrashPurgeJob.LOCK_KEY)).thenReturn(true);
        when(purgeService.purgeExpiredTrash()).thenReturn(report(2, 2_000, 0));

        job.purgeExpiredTrash();

        verifyNoInteractions(dingTalkAlertService);
    }

    private static ImageTrashPurgeReport report(int deletedCount, long deletedBytes, int failedCount) {
        return ImageTrashPurgeReport.builder()
                .executed(true)
                .mode(ImageGcConstants.Mode.TRASH)
                .aborted(false)
                .listedObjectCount(deletedCount + failedCount)
                .expiredObjectCount(deletedCount + failedCount)
                .deletedObjectCount(deletedCount)
                .deletedBytes(deletedBytes)
                .failedObjectCount(failedCount)
                .skippedObjectCount(0)
                .build();
    }
}
