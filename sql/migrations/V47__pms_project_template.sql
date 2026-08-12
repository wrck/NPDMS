-- 1. 项目模板主表
CREATE TABLE IF NOT EXISTS `pms_project_template` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板编号',
    `code`           VARCHAR(64)  NOT NULL COMMENT '模板编码（全局唯一）',
    `name`           VARCHAR(128) NOT NULL COMMENT '模板名称',
    `project_type`   VARCHAR(64)  NULL COMMENT '适用项目类型（字典 pms_project_type）',
    `description`    VARCHAR(500) NULL COMMENT '描述',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0=启用 1=停用',
    `sort`           INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `snapshot_json`  JSON         NULL COMMENT '模板内容快照（phases+tasks+teamRoles）',
    `creator`        VARCHAR(64)  NULL DEFAULT '',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`        VARCHAR(64)  NULL DEFAULT '',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        BIT(1)       NOT NULL DEFAULT b'0',
    `deleted_time`   DATETIME     NULL,
    `tenant_id`      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`, `tenant_id`),
    KEY `idx_status_type` (`status`, `project_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PMS 项目模板';

-- 2. 现有表字段扩展
ALTER TABLE `pms_project` ADD COLUMN `template_id` BIGINT NULL COMMENT '来源项目模板编号' AFTER `manager_user_id`;
ALTER TABLE `pms_project_phase_template` ADD COLUMN `project_template_id` BIGINT NULL COMMENT '所属项目模板编号（NULL=独立阶段模板）' AFTER `project_type`;

-- 3. 字典类型：项目类型
-- 注：不硬编码 id，避免与已存在的记录冲突（id 2115 已被 mes_md_auto_code_padded_method 占用）
INSERT IGNORE INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`) VALUES
('PMS-项目类型', 'pms_project_type', 0, '项目类型（售前测试/标准交付/复杂工程/割接/巡检/维保）', 'admin', NOW(), 'admin', NOW(), b'0', NULL);

-- 4. 字典数据：项目类型
-- 注：system_dict_data 表无 deleted_time 列，参考 V46 写法仅使用 deleted
INSERT IGNORE INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '网络集成', 'NETWORK_INTEGRATION', 'pms_project_type', 0, 'primary', '', '网络集成类项目', 'admin', NOW(), 'admin', NOW(), b'0'),
(2, '安全部署', 'SECURITY_DEPLOYMENT', 'pms_project_type', 0, 'success', '', '安全部署类项目', 'admin', NOW(), 'admin', NOW(), b'0'),
(3, '运维服务', 'MAINTENANCE_SERVICE', 'pms_project_type', 0, 'info', '', '运维服务类项目', 'admin', NOW(), 'admin', NOW(), b'0'),
(4, '售前测试/POC', 'PRE_SALES_TEST', 'pms_project_type', 0, 'warning', '', '售前测试或POC', 'admin', NOW(), 'admin', NOW(), b'0'),
(5, '独立割接服务', 'CUTOVER_SERVICE', 'pms_project_type', 0, 'danger', '', '独立割接服务', 'admin', NOW(), 'admin', NOW(), b'0'),
(6, '主动巡检服务', 'INSPECTION_SERVICE', 'pms_project_type', 0, '', '', '主动巡检服务', 'admin', NOW(), 'admin', NOW(), b'0');

-- 5. 菜单：项目模板管理
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(18042, '项目模板管理', 'pms:project-template:query', 2, 42, 18000, 'project-template', 'ep:document-copy', 'pms/project/project-template/index', 'PmsProjectTemplate', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(18043, '项目模板维护', 'pms:project-template:create', 3, 43, 18000, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `update_time`=NOW(), `deleted`=b'0';

-- 6. 种子数据：3 个项目模板
INSERT INTO `pms_project_template` (`id`, `code`, `name`, `project_type`, `description`, `status`, `sort`, `snapshot_json`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, 'TPL-NET-01', '网络集成标准模板', 'NETWORK_INTEGRATION', '网络集成类项目标准阶段与任务模板', 0, 1,
'{"schemaVersion":1,"phases":[{"phaseCode":"STARTUP","phaseName":"启动阶段","sortOrder":1,"entryCriteria":"项目已立项","exitCriteria":"项目启动会已召开"},{"phaseCode":"IMPLEMENT","phaseName":"实施阶段","sortOrder":2,"entryCriteria":"启动会已召开","exitCriteria":"设备安装调试完成"},{"phaseCode":"ACCEPTANCE","phaseName":"验收阶段","sortOrder":3,"entryCriteria":"实施完成","exitCriteria":"客户签署验收报告"}],"tasks":[{"taskCode":"T-STARTUP-01","taskName":"项目启动会","parentTaskCode":null,"phaseCode":"STARTUP","priority":1,"sortOrder":1,"estimatedHours":4,"description":"召开项目启动会，明确范围与职责"},{"taskCode":"T-IMPL-01","taskName":"设备到货确认","parentTaskCode":null,"phaseCode":"IMPLEMENT","priority":1,"sortOrder":1,"estimatedHours":2,"description":"确认设备到货情况"},{"taskCode":"T-IMPL-02","taskName":"设备安装","parentTaskCode":"T-IMPL-01","phaseCode":"IMPLEMENT","priority":2,"sortOrder":2,"estimatedHours":16,"description":"按方案安装设备"},{"taskCode":"T-IMPL-03","taskName":"设备调试","parentTaskCode":"T-IMPL-02","phaseCode":"IMPLEMENT","priority":2,"sortOrder":3,"estimatedHours":24,"description":"设备配置与联调"},{"taskCode":"T-ACC-01","taskName":"验收测试","parentTaskCode":null,"phaseCode":"ACCEPTANCE","priority":1,"sortOrder":1,"estimatedHours":8,"description":"执行验收测试用例"},{"taskCode":"T-ACC-02","taskName":"验收报告签署","parentTaskCode":"T-ACC-01","phaseCode":"ACCEPTANCE","priority":1,"sortOrder":2,"estimatedHours":2,"description":"客户签署验收报告"}],"teamRoles":[{"roleCode":"PROJECT_MANAGER","roleName":"项目经理","requiredCount":1},{"roleCode":"TECH_LEAD","roleName":"技术负责人","requiredCount":1},{"roleCode":"ENGINEER","roleName":"工程师","requiredCount":2}]}',
'admin', NOW(), 'admin', NOW(), b'0', 1),
(2, 'TPL-SEC-01', '安全部署标准模板', 'SECURITY_DEPLOYMENT', '安全部署类项目标准阶段与任务模板', 0, 2,
'{"schemaVersion":1,"phases":[{"phaseCode":"STARTUP","phaseName":"启动阶段","sortOrder":1,"entryCriteria":"项目已立项","exitCriteria":"项目启动会已召开"},{"phaseCode":"IMPLEMENT","phaseName":"实施阶段","sortOrder":2,"entryCriteria":"启动会已召开","exitCriteria":"安全设备部署完成"},{"phaseCode":"ACCEPTANCE","phaseName":"验收阶段","sortOrder":3,"entryCriteria":"实施完成","exitCriteria":"客户签署验收报告"}],"tasks":[{"taskCode":"T-STARTUP-01","taskName":"项目启动会","parentTaskCode":null,"phaseCode":"STARTUP","priority":1,"sortOrder":1,"estimatedHours":4,"description":"召开项目启动会"},{"taskCode":"T-IMPL-01","taskName":"安全设备部署","parentTaskCode":null,"phaseCode":"IMPLEMENT","priority":1,"sortOrder":1,"estimatedHours":20,"description":"部署安全设备"},{"taskCode":"T-IMPL-02","taskName":"安全策略配置","parentTaskCode":"T-IMPL-01","phaseCode":"IMPLEMENT","priority":2,"sortOrder":2,"estimatedHours":16,"description":"配置安全策略"},{"taskCode":"T-ACC-01","taskName":"安全验收","parentTaskCode":null,"phaseCode":"ACCEPTANCE","priority":1,"sortOrder":1,"estimatedHours":6,"description":"执行安全验收"}],"teamRoles":[{"roleCode":"PROJECT_MANAGER","roleName":"项目经理","requiredCount":1},{"roleCode":"SECURITY_LEAD","roleName":"安全负责人","requiredCount":1}]}',
'admin', NOW(), 'admin', NOW(), b'0', 1),
(3, 'TPL-MAIN-01', '运维服务标准模板', 'MAINTENANCE_SERVICE', '运维服务类项目标准阶段与任务模板', 0, 3,
'{"schemaVersion":1,"phases":[{"phaseCode":"SERVICE_STARTUP","phaseName":"服务启动阶段","sortOrder":1,"entryCriteria":"合同已签订","exitCriteria":"服务启动会已召开"},{"phaseCode":"STABLE_RUN","phaseName":"稳定运行阶段","sortOrder":2,"entryCriteria":"服务启动完成","exitCriteria":"服务交付完成"},{"phaseCode":"SERVICE_CLOSURE","phaseName":"服务收尾阶段","sortOrder":3,"entryCriteria":"服务期结束","exitCriteria":"服务总结报告已提交"}],"tasks":[{"taskCode":"T-SUP-01","taskName":"服务启动会","parentTaskCode":null,"phaseCode":"SERVICE_STARTUP","priority":1,"sortOrder":1,"estimatedHours":4,"description":"召开服务启动会"},{"taskCode":"T-RUN-01","taskName":"日常巡检","parentTaskCode":null,"phaseCode":"STABLE_RUN","priority":1,"sortOrder":1,"estimatedHours":8,"description":"按计划执行日常巡检"},{"taskCode":"T-RUN-02","taskName":"故障处理","parentTaskCode":null,"phaseCode":"STABLE_RUN","priority":2,"sortOrder":2,"estimatedHours":16,"description":"处理客户报障"},{"taskCode":"T-CLS-01","taskName":"服务总结","parentTaskCode":null,"phaseCode":"SERVICE_CLOSURE","priority":1,"sortOrder":1,"estimatedHours":4,"description":"编写服务总结报告"}],"teamRoles":[{"roleCode":"SERVICE_MANAGER","roleName":"服务经理","requiredCount":1},{"roleCode":"ENGINEER","roleName":"工程师","requiredCount":2}]}',
'admin', NOW(), 'admin', NOW(), b'0', 1);
