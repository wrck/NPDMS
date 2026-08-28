ALTER TABLE `ast_device_ancestor_projection_operation`
  ADD COLUMN `event_id` varchar(128) NULL COMMENT 'Outbox事件编号' AFTER `id`;

UPDATE `ast_device_ancestor_projection_operation`
SET `event_id` = `operation_id`
WHERE `event_id` IS NULL;

ALTER TABLE `ast_device_ancestor_projection_operation`
  DROP INDEX `uk_ast_device_ancestor_projection_operation`,
  MODIFY COLUMN `event_id` varchar(128) NOT NULL COMMENT 'Outbox事件编号',
  ADD UNIQUE KEY `uk_ast_device_ancestor_projection_event` (`tenant_id`, `event_id`),
  ADD KEY `idx_ast_device_ancestor_projection_operation` (`tenant_id`, `operation_id`);
