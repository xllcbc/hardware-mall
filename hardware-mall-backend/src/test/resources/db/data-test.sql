-- H2 兼容的测试种子数据
-- 用于 JUnit 集成测试（多个测试类共享同一 H2 内存库，需幂等）

MERGE INTO `user` (`id`, `openid`, `nickname`, `role`, `status`, `delete_time`)
KEY (`id`)
VALUES (1, 'admin', '管理员', 2, 1, 0);
