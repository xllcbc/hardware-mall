package com.example.mystore.runner;

import com.example.mystore.service.ImageGcService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 启动器只负责把配置的回收站对象键交给图片回收服务，恢复规则由服务承担。
 */
@ExtendWith(MockitoExtension.class)
class ImageTrashRestoreRunnerTest {

    private static final String TRASH_KEY = "trash/20260101/products/a.jpg";

    @Mock
    private ImageGcService imageGcService;

    @Test
    void run_restoresConfiguredTrashObject() {
        when(imageGcService.restoreFromTrash(TRASH_KEY)).thenReturn(true);

        new ImageTrashRestoreRunner(imageGcService, TRASH_KEY).run();

        verify(imageGcService).restoreFromTrash(TRASH_KEY);
    }

    @Test
    void run_doesNothingWhenNoRestoreKeyConfigured() {
        new ImageTrashRestoreRunner(imageGcService, "  ").run();

        verifyNoInteractions(imageGcService);
    }

    @Test
    void run_doesNotBreakStartupWhenRestoreFails() {
        when(imageGcService.restoreFromTrash(TRASH_KEY)).thenThrow(new RuntimeException("oss is down"));

        assertThatNoException().isThrownBy(() -> new ImageTrashRestoreRunner(imageGcService, TRASH_KEY).run());
    }
}
