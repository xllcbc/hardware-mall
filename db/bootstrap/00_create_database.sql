-- 仅用于首次建库；Flyway 迁移不得包含 CREATE DATABASE/USE。
CREATE DATABASE IF NOT EXISTS `hardware_mall`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;
