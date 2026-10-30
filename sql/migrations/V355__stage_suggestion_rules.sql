-- ============================================================================
-- V355: 3.1 工期建议计划时间规则配置化（「取消项目工期占比」配套；Demo 页面9 / Excel 3.1）
--
-- 两份需求均给出租粒度规则：工期建议计划时间 = 参照时间 - 偏移，按签约方式分档：
--   割接-上线（实施部署）：非直签=工期要求-2周；直签=初验时间-3个月-2周（初验带入）
--   工前准备 = 硬件实施时间-2周；硬件实施 = 设备配置时间-2周；设备配置 = 割接上线时间-2个月
--   到货签收/初验/终验 = PMS/财务带入
-- 仓库 SOL 模板为粗粒度 6 阶段（S0~S5），Demo 细阶段（硬件实施/设备配置/割接-上线）并入
-- S4 实施部署、初验/终验并入 S5 验收交维；粗粒度映射属示例，偏移经维护界面调整。
-- 签约方式值域恰为字典双值：DIRECT_SIGN(直签) / CHANNEL_SIGN(非直签)。
-- 菜单挂在项目交付目录（18000）下，紧邻阶段施工计划（3.1，993109180007）。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `proj_stage_suggestion_rule` (
    `id`                   bigint       NOT NULL COMMENT '主键',
    `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    `stage_code`           varchar(64)  NOT NULL COMMENT '参与计划的阶段编码',
    `signing_method`       varchar(32)  DEFAULT NULL COMMENT '签约方式（字典 pms_signing_method）；空=全部签约方式',
    `source_type`          varchar(32)  NOT NULL COMMENT '建议来源：PMS_IMPORTED 带入自身计划验收时间 / DURATION_REQUIRE 锚工期要求 / STAGE_PLAN 参照阶段',
    `reference_stage_code` varchar(64)  DEFAULT NULL COMMENT '参照阶段编码（STAGE_PLAN 时必填，须晚于自身阶段）',
    `offset_months`        int          NOT NULL DEFAULT 0 COMMENT '参照时间偏移月数（负=提前）',
    `offset_days`          int          NOT NULL DEFAULT 0 COMMENT '参照时间偏移天数（负=提前，如 -14=2周）',
    `remark`               varchar(255) DEFAULT NULL COMMENT '备注（需求原文与映射说明）',
    `enabled`              bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否启用',
    `creator`              varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_stage_variant` (`tenant_id`, `stage_code`, `signing_method`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
    COMMENT = '3.1 工期建议计划时间规则（Demo 页面9 / Excel 3.1；建议最迟完成 = 参照时间 - 偏移）';

-- 种子：覆盖精确变体命中、全部变体、带入、工期锚、阶段参照链、无匹配（S0/S2 未配行）与停用不参与组合
INSERT INTO `proj_stage_suggestion_rule`
(`id`, `tenant_id`, `stage_code`, `signing_method`, `source_type`, `reference_stage_code`, `offset_months`, `offset_days`, `remark`, `enabled`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 993109180101, 1, 'S5', 'DIRECT_SIGN', 'PMS_IMPORTED', NULL, 0, 0,
       '验收交维=合同验收时间带入（Demo 初验/终验：直签按合同规定试运行）；非直签 Demo 无行即无建议', b'1',
       'p006_v355', NOW(), 'p006_v355', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM proj_stage_suggestion_rule WHERE id = 993109180101);

INSERT INTO `proj_stage_suggestion_rule`
(`id`, `tenant_id`, `stage_code`, `signing_method`, `source_type`, `reference_stage_code`, `offset_months`, `offset_days`, `remark`, `enabled`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 993109180102, 1, 'S4', 'DIRECT_SIGN', 'STAGE_PLAN', 'S5', 3, -14,
       'Demo 割接-上线=初验时间-3个月-2周；粗粒度映射：初验∈S5验收交维（按合同规定试运行）', b'1',
       'p006_v355', NOW(), 'p006_v355', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM proj_stage_suggestion_rule WHERE id = 993109180102);

INSERT INTO `proj_stage_suggestion_rule`
(`id`, `tenant_id`, `stage_code`, `signing_method`, `source_type`, `reference_stage_code`, `offset_months`, `offset_days`, `remark`, `enabled`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 993109180103, 1, 'S4', 'CHANNEL_SIGN', 'DURATION_REQUIRE', NULL, 0, -14,
       'Demo 割接-上线=工期时间-2周；粗粒度映射：实施部署末段割接-上线锚工期要求', b'1',
       'p006_v355', NOW(), 'p006_v355', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM proj_stage_suggestion_rule WHERE id = 993109180103);

INSERT INTO `proj_stage_suggestion_rule`
(`id`, `tenant_id`, `stage_code`, `signing_method`, `source_type`, `reference_stage_code`, `offset_months`, `offset_days`, `remark`, `enabled`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 993109180104, 1, 'S1', NULL, 'STAGE_PLAN', 'S4', 0, -14,
       'Demo 工前准备=硬件实施时间-2周；粗粒度映射示例：硬件实施∈S4实施部署，偏移经维护界面调整（Demo 另载设备配置=割接上线-2个月）', b'1',
       'p006_v355', NOW(), 'p006_v355', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM proj_stage_suggestion_rule WHERE id = 993109180104);

INSERT INTO `proj_stage_suggestion_rule`
(`id`, `tenant_id`, `stage_code`, `signing_method`, `source_type`, `reference_stage_code`, `offset_months`, `offset_days`, `remark`, `enabled`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 993109180105, 1, 'S3', NULL, 'DURATION_REQUIRE', NULL, 0, -14,
       '示例停用行：停用不参与推算（Demo 未载该阶段规则）', b'0',
       'p006_v355', NOW(), 'p006_v355', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM proj_stage_suggestion_rule WHERE id = 993109180105);

-- 菜单：页面 + 操作按钮（挂在项目交付目录 18000 下，紧邻阶段施工计划 3.1）
INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(993109180111, '施工计划建议规则', 'pms:stage-plan-suggestion-rule:query', 2, 64, 18000, 'stage-plan-suggestion-rule', 'ep:alarm-clock',
 'pms/engineering/stage-plan-rule/index', 'PmsStagePlanSuggestionRule', 0, b'1', b'1', b'1',
 'p006_v355', NOW(), 'p006_v355', NOW(), b'0'),
(993109180112, '建议规则保存', 'pms:stage-plan-suggestion-rule:save', 3, 1, 993109180111, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p006_v355', NOW(), 'p006_v355', NOW(), b'0'),
(993109180113, '建议规则修改', 'pms:stage-plan-suggestion-rule:update', 3, 2, 993109180111, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p006_v355', NOW(), 'p006_v355', NOW(), b'0'),
(993109180114, '建议规则删除', 'pms:stage-plan-suggestion-rule:delete', 3, 3, 993109180111, '', '',
 NULL, NULL, 0, b'1', b'1', b'1', 'p006_v355', NOW(), 'p006_v355', NOW(), b'0');

-- 已可见阶段施工计划菜单（993109180007）的角色同步可见建议规则页与按钮，幂等
INSERT INTO `system_role_menu`
(`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT source.role_id, m.menu_id, 'p006_v355', NOW(), 'p006_v355', NOW(), b'0', source.tenant_id
FROM `system_role_menu` source
JOIN (SELECT 993109180111 AS menu_id
      UNION SELECT 993109180112 UNION SELECT 993109180113
      UNION SELECT 993109180114) m
WHERE source.`menu_id` = 993109180007 AND source.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                  WHERE existing.`tenant_id` = source.`tenant_id`
                    AND existing.`role_id` = source.`role_id`
                    AND existing.`menu_id` = m.menu_id AND existing.`deleted` = b'0');
