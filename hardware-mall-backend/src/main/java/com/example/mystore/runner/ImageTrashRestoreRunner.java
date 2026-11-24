package com.example.mystore.runner;

import com.example.mystore.service.ImageGcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 一次性从回收站恢复单张图片的运维入口。配置 {@code image.gc.restore-object-key}
 * （或启动参数 {@code --image.gc.restore-object-key=trash/<yyyyMMdd>/<原对象键>}）时触发；
 * 未配置时不做任何事。恢复失败只记录日志，不影响应用启动。
 */
@Slf4j
@Component
public class ImageTrashRestoreRunner implements CommandLineRunner {

    private final ImageGcService imageGcService;
    private final String restoreObjectKey;

    public ImageTrashRestoreRunner(ImageGcService imageGcService,
                                   @Value("${image.gc.restore-object-key:}") String restoreObjectKey) {
        this.imageGcService = imageGcService;
        this.restoreObjectKey = restoreObjectKey;
    }

    @Override
    public void run(String... args) {
        if (restoreObjectKey == null || restoreObjectKey.isBlank()) {
            return;
        }
        String key = restoreObjectKey.trim();
        try {
            if (imageGcService.restoreFromTrash(key)) {
                log.info("回收站恢复完成: {}", key);
            } else {
                log.warn("回收站恢复未完成（可能已恢复过或对象键不正确），请检查服务日志: {}", key);
            }
        } catch (Exception e) {
            log.error("回收站恢复异常: {}", key, e);
        }
    }
}
