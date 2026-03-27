ALTER TABLE `queue_metadata`
    ADD COLUMN `enable_take_log` TINYINT NOT NULL DEFAULT 0 AFTER `endpoint` COMMENT '是否开启打印take日志';
