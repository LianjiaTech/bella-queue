alter table `queue`
    add column `trace_id` varchar(128) NOT NULL DEFAULT '' after `expired_at`;

create index idx_trace_id on `queue` (`trace_id`);

CREATE TABLE `trace_task_sharding_mapping`
(
    `id`           bigint unsigned NOT NULL AUTO_INCREMENT,
    `trace_id`     varchar(128) NOT NULL DEFAULT '' COMMENT 'trace_id',
    `sharding_key` varchar(128) NOT NULL DEFAULT '' COMMENT '分片',
    `ctime`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `mtime`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_traceId_shardingKey` (`trace_id`,`sharding_key`)
);
