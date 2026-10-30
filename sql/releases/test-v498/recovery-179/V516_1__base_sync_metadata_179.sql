-- 179-only forward migration; source: 25 previously reviewed and applied A statements (2026-10-21).
-- Executes only against the 179 recovery lineage; no legacy trigger definitions or task runs.
-- The 179 V506 failure and receipt are retained separately; P22's deferred foreign key is unchanged.
SET @sync_history_ok = (
 SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND
 ((version='506.1' AND checksum=-703476136 AND script='V506_1__reconcile_partial_cutover_support.sql') OR
  (version='516' AND checksum=-2064355135 AND script='V516__stage_plan_management_menu_roles.sql'))
);
SET @sync_guard_sql = IF(@sync_history_ok=2 AND ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('int_base_sync_task', 'int_base_sync_run', 'int_base_sync_binding'))=0),
 'SELECT 1 AS sync_179_preconditions_verified','SYNC_179_PRECONDITIONS_NOT_MET');
PREPARE sync_179_guard FROM @sync_guard_sql;
EXECUTE sync_179_guard;
DEALLOCATE PREPARE sync_179_guard;

-- A01 int_base_sync_task; reviewed SHA-256 88c68c6c27424fbedc9650ac47b1fe35fef7b631ad48819cc16d94a8f3c4619e
CREATE TABLE `int_base_sync_task` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `task_code` varchar(16) NOT NULL,
  `source_system` varchar(32) NOT NULL,
  `connection_id` bigint NOT NULL,
  `batch_size` int NOT NULL,
  `max_rows` int NOT NULL,
  `active_run_id` bigint NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_base_sync_task` (`tenant_id`,`task_code`,`connection_id`),
  CONSTRAINT `ck_int_base_sync_batch_size` CHECK (`batch_size` BETWEEN 1 AND 2000),
  CONSTRAINT `ck_int_base_sync_max_rows` CHECK (`max_rows` BETWEEN 1 AND 5000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A02 int_base_sync_run; reviewed SHA-256 25292e712ad62363997fcfd3379dcf50fb1d85742d11c635faad6b5ee02e5cd0
CREATE TABLE `int_base_sync_run` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `request_key` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `preview` bit(1) NOT NULL,
  `task_code` varchar(16) NOT NULL,
  `connection_id` bigint NOT NULL,
  `batch_size` int NOT NULL,
  `max_rows` int NOT NULL,
  `read_count` bigint NOT NULL DEFAULT 0,
  `inserted_count` bigint NOT NULL DEFAULT 0,
  `updated_count` bigint NOT NULL DEFAULT 0,
  `unchanged_count` bigint NOT NULL DEFAULT 0,
  `deleted_count` bigint NOT NULL DEFAULT 0,
  `error_message` varchar(1000) NULL,
  `started_at` datetime(3) NOT NULL,
  `finished_at` datetime(3) NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_base_sync_run_request` (`tenant_id`,`task_id`,`request_key`),
  KEY `idx_int_base_sync_run_task` (`tenant_id`,`task_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A03 int_base_sync_binding; reviewed SHA-256 bd284d98625d7f20f95a69c458e3afc8f5f148e1295353b8ab6caf0f5570e786
CREATE TABLE `int_base_sync_binding` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `object_key` varchar(64) NOT NULL,
  `source_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `target_id` bigint NOT NULL,
  `fingerprint` char(64) NOT NULL,
  `last_run_id` bigint NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_int_base_sync_binding_source` (`tenant_id`,`task_id`,`object_key`,`source_key`),
  KEY `idx_int_base_sync_binding_target` (`tenant_id`,`task_id`,`object_key`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
