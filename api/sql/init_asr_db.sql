-- ============================================================================
-- Bella ASR - 数据库初始化脚本
-- ============================================================================
--
-- 说明:
-- 1. 创建 queue_sharding 表（与 bella-queue 同结构，用于分片元数据）
-- 2. 创建 asr_tasks 模板表（物理分片表通过 CREATE TABLE ... LIKE 创建）
--
-- 应用启动时 AsrShardingRepo.initSharding() 会自动：
-- - 检查 queue_sharding 中是否有 queue_table='asr' 的记录
-- - 若无，创建第一条分片记录并建第一个物理分片表
--
-- 执行方式:
-- mysql -u root -p bella_asr < migrations/init_sharding.sql
--
-- ============================================================================

USE bella_asr;

-- 步骤1: 创建 queue_sharding 表
CREATE TABLE IF NOT EXISTS `queue_sharding`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
    `key`         varchar(256) NOT NULL DEFAULT '' COMMENT '分表的标识',
    `key_time`    datetime     NOT NULL DEFAULT '2000-01-01 00:00:00',
    `queue_table` varchar(256) NOT NULL DEFAULT '',
    `last_key`    varchar(256) NOT NULL DEFAULT '',
    `count`       bigint unsigned NOT NULL DEFAULT '0' COMMENT '分表的记录数量',
    `max_count`   bigint unsigned NOT NULL DEFAULT '20000000' COMMENT '分表的最大记录数,如果 count>max_count,创建新表',
    `cuid`        bigint       NOT NULL DEFAULT '0',
    `muid`        bigint       NOT NULL DEFAULT '0',
    `cu_name`     varchar(256) NOT NULL DEFAULT '',
    `mu_name`     varchar(256) NOT NULL DEFAULT '',
    `ctime`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX idx_last_key (last_key),
    INDEX idx_key (`key`),
    UNIQUE INDEX uk_queue_table_key (queue_table, `key`)
);

-- 步骤2: 创建 asr_tasks 模板表（物理分片表由应用层 CREATE TABLE ... LIKE 创建）
CREATE TABLE IF NOT EXISTS `asr_tasks` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id` VARCHAR(128) NOT NULL COMMENT '任务ID（bella-queue taskId）',
    `ak_code` VARCHAR(256) DEFAULT NULL COMMENT 'API Key Code',
    `user` VARCHAR(128) DEFAULT '' COMMENT '用户标识',
    `model` VARCHAR(64) NOT NULL COMMENT 'ASR模型名称',
    `status` VARCHAR(32) DEFAULT 'pending' COMMENT '任务状态',
    `input_data` TEXT COMMENT '输入参数JSON',
    `input_file_id` VARCHAR(128) DEFAULT NULL COMMENT '输入参数文件ID',
    `output_data` TEXT COMMENT '输出结果JSON',
    `output_file_id` VARCHAR(128) DEFAULT NULL COMMENT '输出结果文件ID',
    `callback_url` VARCHAR(512) DEFAULT NULL COMMENT '回调URL',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `completed_at` TIMESTAMP NULL DEFAULT NULL COMMENT '完成时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_user` (`user`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ASR任务模板表';

-- 步骤3: 创建 instance 表（应用启动注册实例，IDGenerator 依赖）
CREATE TABLE IF NOT EXISTS `instance`
(
    `id`     bigint unsigned NOT NULL AUTO_INCREMENT,
    `ip`     varchar(64) NOT NULL DEFAULT '',
    `port`   int         NOT NULL DEFAULT 0,
    `status` int         NOT NULL DEFAULT 0,
    `ctime`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX idx_ip_port (ip, port)
);
