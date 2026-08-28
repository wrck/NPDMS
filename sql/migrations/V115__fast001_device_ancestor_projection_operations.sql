CREATE TABLE `ast_device_ancestor_projection_operation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '投影操作编号',
  `operation_id` varchar(128) NOT NULL COMMENT '归属操作编号',
  `device_sn` varchar(100) NOT NULL COMMENT '设备序列号',
  `project_id` bigint NOT NULL COMMENT '直接归属项目编号',
  `tree_version` int unsigned NOT NULL COMMENT '项目树版本',
  `assignment_version` int unsigned NOT NULL COMMENT '设备归属版本',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建人',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新人',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ast_device_ancestor_projection_operation` (`tenant_id`, `operation_id`),
  KEY `idx_ast_device_ancestor_projection_operation_device` (`tenant_id`, `device_sn`, `assignment_version`),
  CONSTRAINT `fk_ast_device_ancestor_projection_operation_device`
    FOREIGN KEY (`tenant_id`, `device_sn`) REFERENCES `ast_device` (`tenant_id`, `sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AST设备祖先投影幂等操作';
