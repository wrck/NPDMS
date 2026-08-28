CREATE TABLE `ast_device_download_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '下载授权编号',
  `token_digest` char(64) NOT NULL COMMENT '一次性令牌SHA-256摘要',
  `user_id` bigint NOT NULL COMMENT '授权用户编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `configuration_log_id` bigint NOT NULL COMMENT '配置Log编号',
  `expires_at` datetime(3) NOT NULL COMMENT '过期时间',
  `consumed_at` datetime(3) DEFAULT NULL COMMENT '消费时间',
  `version` int unsigned NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_download_grant_token` (`token_digest`),
  KEY `idx_ast_device_download_grant_subject` (`tenant_id`, `user_id`, `device_sn`, `configuration_log_id`),
  KEY `idx_ast_device_download_grant_expiry` (`expires_at`, `consumed_at`),
  CONSTRAINT `fk_ast_device_download_grant_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备配置Log一次性下载授权';
