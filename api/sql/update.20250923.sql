ALTER TABLE `queue_head`
    ADD COLUMN `total_put_cnt` BIGINT NOT NULL DEFAULT 0 AFTER `last_scanned_id`,
    ADD COLUMN `total_loaded_cnt` BIGINT NOT NULL DEFAULT 0 AFTER `total_put_cnt`,
	ADD COLUMN `total_completed_cnt` BIGINT NOT NULL DEFAULT 0 AFTER `total_loaded_cnt`;
