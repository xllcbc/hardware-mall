package com.example.mystore.job.image;

import com.example.mystore.common.constant.ImageGcConstants;
import com.example.mystore.entity.vo.ImageGcReport;
import com.example.mystore.service.ImageGcService;
import com.example.mystore.service.impl.DingTalkAlertService;
import com.example.mystore.util.RedisLockUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private ImageGcJob job;

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
}
