DROP TABLE IF EXISTS `batch`;
CREATE TABLE `batch`
(
    `id`                       bigint       NOT NULL AUTO_INCREMENT,
    `batch_id`                 varchar(64)  NOT NULL DEFAULT '',
    `endpoint`                 varchar(64)  NOT NULL DEFAULT '',
    `ak`                       varchar(128) NOT NULL DEFAULT '',
    `input_file_id`            varchar(64)  NOT NULL DEFAULT '',
    `completion_window`        varchar(8)   NOT NULL DEFAULT '',
    `status`                   varchar(32)  NOT NULL DEFAULT 'validating',
    `output_file_id`           varchar(64)  NOT NULL DEFAULT '',
    `error_file_id`            varchar(64)  NOT NULL DEFAULT '',
    `in_progress_at`           datetime     NOT NULL DEFAULT '2000-01-01 00:00:00',
    `expired_at`               datetime     NOT NULL DEFAULT '2000-01-01 00:00:00',
    `finalizing_at`            datetime     NOT NULL DEFAULT '2000-01-01 00:00:00',
    `completed_at`             datetime     NOT NULL DEFAULT '2000-01-01 00:00:00',
    `failed_at`                datetime     NOT NULL DEFAULT '2000-01-01 00:00:00',
    `cancelling_at`            datetime     NOT NULL DEFAULT '2000-01-01 00:00:00',
    `cancelled_at`             datetime     NOT NULL DEFAULT '2000-01-01 00:00:00',
    `request_counts_total`     bigint       NOT NULL DEFAULT '0',
    `request_counts_completed` bigint       NOT NULL DEFAULT '0',
    `request_counts_failed`    bigint       NOT NULL DEFAULT '0',
    `import_counts_failed`     bigint       NOT NULL DEFAULT '0',
    `matadata`                 text         NOT NULL,
    `cuid`                     bigint       NOT NULL DEFAULT '0',
    `muid`                     bigint       NOT NULL DEFAULT '0',
    `cu_name`                  varchar(256) NOT NULL DEFAULT '',
    `mu_name`                  varchar(256) NOT NULL DEFAULT '',
    `ctime`                    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`                    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX idx_batch_id ON batch (batch_id);
CREATE INDEX idx_endpoint_cuid ON batch (endpoint, cuid);

DROP TABLE IF EXISTS `queue_metadata`;
CREATE TABLE `queue_metadata`
(
    `id`       bigint unsigned NOT NULL AUTO_INCREMENT,
    `queue`    varchar(256) NOT NULL DEFAULT '',
    `endpoint` varchar(256) NOT NULL DEFAULT '',
    `cuid`     bigint       NOT NULL DEFAULT '0',
    `muid`     bigint       NOT NULL DEFAULT '0',
    `cu_name`  varchar(256) NOT NULL DEFAULT '',
    `mu_name`  varchar(256) NOT NULL DEFAULT '',
    `ctime`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX uk_queue ON queue_metadata (queue);

DROP TABLE IF EXISTS `queue`;
CREATE TABLE `queue`
(
    `id`             bigint       NOT NULL AUTO_INCREMENT,
    `task_id`        varchar(64)  NOT NULL DEFAULT '',
    `custom_id`      varchar(128) NOT NULL DEFAULT '',
    `queue`          varchar(256) NOT NULL DEFAULT '',
    `endpoint`       varchar(64)  NOT NULL DEFAULT '',
    `ak`             varchar(128) NOT NULL DEFAULT '',
    `batch_id`       varchar(64)  NOT NULL DEFAULT '',
    `input_data`     text         NOT NULL,
    `input_file_id`  varchar(256) NOT NULL DEFAULT '',
    `output_data`    text         NOT NULL,
    `output_file_id` varchar(256) NOT NULL DEFAULT '',
    `status`         varchar(32)  NOT NULL DEFAULT 'waiting' COMMENT 'waiting: 等待中, queued: 已入队,  succeeded: 成功, timeout: 超时, cancelled: 已取消',
    `callback_url`   varchar(256) NOT NULL DEFAULT '',
    `expired_at`     datetime     NOT NULL DEFAULT '2000-01-01 00:00:00',
    `cuid`           bigint       NOT NULL DEFAULT '0',
    `muid`           bigint       NOT NULL DEFAULT '0',
    `cu_name`        varchar(256) NOT NULL DEFAULT '',
    `mu_name`        varchar(256) NOT NULL DEFAULT '',
    `ctime`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX uk_task_id ON queue (task_id);
CREATE INDEX idx_batch_id ON queue (batch_id);

DROP TABLE IF EXISTS `queue_head`;
CREATE TABLE `queue_head`
(
    `id`                        bigint unsigned NOT NULL AUTO_INCREMENT,
    `queue`                     varchar(256) NOT NULL DEFAULT '',
    `level`                     int          NOT NULL DEFAULT '0',
    `last_wrote_sharding_key`   varchar(256) NOT NULL DEFAULT '',
    `last_wrote_id`             bigint       NOT NULL DEFAULT '0',
    `last_scanned_sharding_key` varchar(256) NOT NULL DEFAULT '',
    `last_scanned_id`           bigint       NOT NULL DEFAULT '0',
    `cuid`                      bigint       NOT NULL DEFAULT '0',
    `muid`                      bigint       NOT NULL DEFAULT '0',
    `cu_name`                   varchar(256) NOT NULL DEFAULT '',
    `mu_name`                   varchar(256) NOT NULL DEFAULT '',
    `ctime`                     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`                     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE INDEX idx_queue ON queue_head (queue);
CREATE UNIQUE INDEX uk_queue_level ON queue_head (queue, level);
CREATE UNIQUE INDEX uk_scanned_key ON queue_head (last_scanned_sharding_key);
CREATE UNIQUE INDEX uk_wrote_key ON queue_head (last_wrote_sharding_key);

DROP TABLE IF EXISTS `queue_sharding`;
CREATE TABLE `queue_sharding`
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
    PRIMARY KEY (`id`)
);

CREATE INDEX idx_last_key ON queue_sharding (last_key);
CREATE INDEX idx_key ON queue_sharding (`key`);
CREATE UNIQUE INDEX uk_queue_table_key ON queue_sharding (queue_table, `key`);


DROP TABLE IF EXISTS `instance`;
CREATE TABLE `instance`
(
    `id`     bigint unsigned NOT NULL AUTO_INCREMENT,
    `ip`     varchar(64) NOT NULL DEFAULT '',
    `port`   int         NOT NULL DEFAULT '0',
    `status` int         NOT NULL DEFAULT '0',
    `ctime`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE INDEX idx_ip_port ON instance (ip, port);
