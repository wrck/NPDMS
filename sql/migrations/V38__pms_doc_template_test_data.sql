-- ============================================================
-- V38: 工程文档模板测试数据（需求分析 / 实施方案）
-- 依赖：V36（表结构）、V19（项目/需求/方案测试数据）
-- 用途：为模板管理、模板继承、章节裁剪、版本发布提供验收数据
-- ID 范围：40001 ~ 40010（模板主表 40001-40007，版本表 40001-40007 一一对应）
-- 幂等：DELETE WHERE id BETWEEN 40001 AND 40010 + INSERT IGNORE
-- 约定：tenant_id=1，creator/updater='admin'，deleted=b'0'
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 0. 清理已有测试数据（保证可重复执行）
-- ============================================================
DELETE FROM `pms_eng_doc_template_version` WHERE id BETWEEN 40001 AND 40010;
DELETE FROM `pms_eng_doc_template`        WHERE id BETWEEN 40001 AND 40010;

-- ============================================================
-- 1. 文档模板主表（7 条：3 需求已发布 + 1 需求草稿 + 3 方案已发布）
--    status: 0 草稿 / 1 已发布 / 2 已停用
--    current_version_id 在第 3 节统一回填，此处先置 NULL
-- ============================================================
INSERT IGNORE INTO `pms_eng_doc_template`
(`id`, `code`, `name`, `doc_category`, `parent_template_id`, `applicability`, `description`,
 `current_version_id`, `status`, `version`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
-- 1.1 需求分析 - 基础模板（已发布）
(40001, 'DT-REQ-BASE-001', '需求分析基础模板', 'REQUIREMENT', NULL,
 '{"projectType":["NEW_BUILD","EXPANSION","UPGRADE","RELOCATION"],"networkType":["TRANSMISSION","DATA","WIRELESS","SECURITY"],"productType":[],"implementMode":["DIRECT","NON_DIRECT"],"priority":10,"isDefault":true}',
 '适用于所有项目类型的需求分析基础模板，包含全部通用章节',
 NULL, 1, 0,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- 1.2 需求分析 - 数据网新建项目模板（继承 40001，已发布）
(40002, 'DT-REQ-DATA-001', '数据网新建项目需求分析模板', 'REQUIREMENT', 40001,
 '{"projectType":["NEW_BUILD"],"networkType":["DATA"],"productType":["SWITCH","ROUTER"],"implementMode":["DIRECT","NON_DIRECT"],"priority":100,"isDefault":false}',
 '针对数据网新建项目的需求分析模板，强调VLAN/IP/路由规划',
 NULL, 1, 0,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- 1.3 需求分析 - 安全项目模板（继承 40001，已发布）
(40003, 'DT-REQ-SEC-001', '安全项目需求分析模板', 'REQUIREMENT', 40001,
 '{"projectType":["NEW_BUILD","UPGRADE"],"networkType":["SECURITY"],"productType":["FIREWALL","IPS","WAF"],"implementMode":["DIRECT","NON_DIRECT"],"priority":100,"isDefault":false}',
 '针对安全项目的需求分析模板，强调安全策略和日志留存',
 NULL, 1, 0,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- 1.4 需求分析 - 无线网模板（草稿，未发布）
(40004, 'DT-REQ-WIRELESS-001', '无线网需求分析模板（草稿）', 'REQUIREMENT', 40001,
 '{"projectType":["NEW_BUILD","EXPANSION"],"networkType":["WIRELESS"],"productType":["AP","AC"],"implementMode":["DIRECT","NON_DIRECT"],"priority":90,"isDefault":false}',
 '无线网项目需求分析模板，含覆盖规划章节',
 NULL, 0, 0,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- 2.1 实施方案 - 基础模板（已发布）
(40005, 'DT-SOL-BASE-001', '实施方案基础模板', 'SOLUTION', NULL,
 '{"projectType":["NEW_BUILD","EXPANSION","UPGRADE","RELOCATION"],"networkType":["TRANSMISSION","DATA","WIRELESS","SECURITY"],"productType":[],"implementMode":["DIRECT","NON_DIRECT"],"priority":10,"isDefault":true}',
 '适用于所有项目类型的实施方案基础模板',
 NULL, 1, 0,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- 2.2 实施方案 - 数据网模板（继承 40005，已发布）
(40006, 'DT-SOL-DATA-001', '数据网实施方案模板', 'SOLUTION', 40005,
 '{"projectType":["NEW_BUILD","EXPANSION"],"networkType":["DATA"],"productType":["SWITCH","ROUTER"],"implementMode":["DIRECT","NON_DIRECT"],"priority":100,"isDefault":false}',
 '针对数据网项目的实施方案模板，含IP规划与配置脚本章节',
 NULL, 1, 0,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- 2.3 实施方案 - 安全项目模板（继承 40005，已发布）
(40007, 'DT-SOL-SEC-001', '安全项目实施方案模板', 'SOLUTION', 40005,
 '{"projectType":["NEW_BUILD","UPGRADE"],"networkType":["SECURITY"],"productType":["FIREWALL","IPS","WAF"],"implementMode":["DIRECT","NON_DIRECT"],"priority":100,"isDefault":false}',
 '针对安全项目的实施方案模板，含安全策略脚本章节',
 NULL, 1, 0,
 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ============================================================
-- 2. 文档模板版本表（7 条，与模板一一对应）
--    published: 0 未发布 / 1 已发布
--    sections JSON 字段名对齐 pms_eng_requirement / pms_eng_solution 固定列：
--      background / topology / transmission / traffic / business / ip_plan /
--      redundancy / protection / o_and_m / log_retention / interface_content
-- ============================================================
INSERT IGNORE INTO `pms_eng_doc_template_version`
(`id`, `template_id`, `version_label`, `sections`, `section_overrides`, `excluded_sections`,
 `change_log`, `published`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES

-- ------------------------------------------------------------
-- 40001: DT-REQ-BASE-001 v1.0.0（需求基础模板，8 个通用章节）
-- ------------------------------------------------------------
(40001, 40001, '1.0.0',
 '[{"code":"background","title":"项目背景目标","order":1,"fields":[{"type":"input","field":"background","title":"项目背景","props":{"type":"textarea","rows":4}}]},{"code":"topology","title":"网络拓扑","order":2,"fields":[{"type":"uploadFile","field":"topology_file","title":"拓扑图"},{"type":"input","field":"topology_desc","title":"拓扑说明"}]},{"code":"traffic","title":"流量规划","order":3,"fields":[{"type":"input","field":"traffic","title":"流量分析","props":{"type":"textarea","rows":3}}]},{"code":"business","title":"业务需求","order":4,"fields":[{"type":"input","field":"business","title":"业务描述","props":{"type":"textarea","rows":4}}]},{"code":"ip_plan","title":"IP规划","order":5,"fields":[{"type":"input","field":"ip_plan","title":"IP地址规划","props":{"type":"textarea","rows":4}}]},{"code":"redundancy","title":"冗余设计","order":6,"fields":[{"type":"input","field":"redundancy","title":"冗余方案","props":{"type":"textarea","rows":3}}]},{"code":"o_and_m","title":"运维需求","order":7,"fields":[{"type":"input","field":"o_and_m","title":"运维要求","props":{"type":"textarea","rows":3}}]},{"code":"interface","title":"接口关系","order":8,"fields":[{"type":"input","field":"interface_content","title":"接口关系内容","props":{"type":"textarea","rows":3}}]}]',
 NULL, NULL,
 '初始版本，定义需求分析8个通用章节', 1,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- ------------------------------------------------------------
-- 40002: DT-REQ-DATA-001 v1.0.0（数据网，继承基础 + transmission(可选) + protection）
--   excluded_sections: 显式排除 log_retention（安全专用）
--   section_overrides: transmission 标记为可选
-- ------------------------------------------------------------
(40002, 40002, '1.0.0',
 '[{"code":"background","title":"项目背景目标","order":1,"fields":[{"type":"input","field":"background","title":"项目背景","props":{"type":"textarea","rows":4}}]},{"code":"topology","title":"网络拓扑","order":2,"fields":[{"type":"uploadFile","field":"topology_file","title":"拓扑图"},{"type":"input","field":"topology_desc","title":"拓扑说明"}]},{"code":"transmission","title":"传输规划","order":3,"fields":[{"type":"input","field":"transmission","title":"传输链路","props":{"type":"textarea","rows":3}}]},{"code":"traffic","title":"流量规划","order":4,"fields":[{"type":"input","field":"traffic","title":"流量分析","props":{"type":"textarea","rows":3}}]},{"code":"business","title":"业务需求","order":5,"fields":[{"type":"input","field":"business","title":"业务描述","props":{"type":"textarea","rows":4}}]},{"code":"ip_plan","title":"IP规划","order":6,"fields":[{"type":"input","field":"ip_plan","title":"IP地址规划","props":{"type":"textarea","rows":4}}]},{"code":"redundancy","title":"冗余设计","order":7,"fields":[{"type":"input","field":"redundancy","title":"冗余方案","props":{"type":"textarea","rows":3}}]},{"code":"protection","title":"保护规划","order":8,"fields":[{"type":"input","field":"protection","title":"保护策略","props":{"type":"textarea","rows":3}}]},{"code":"o_and_m","title":"运维需求","order":9,"fields":[{"type":"input","field":"o_and_m","title":"运维要求","props":{"type":"textarea","rows":3}}]},{"code":"interface","title":"接口关系","order":10,"fields":[{"type":"input","field":"interface_content","title":"接口关系内容","props":{"type":"textarea","rows":3}}]}]',
 '{"transmission":{"required":false,"remark":"数据网项目传输链路可选"}}',
 '["log_retention"]',
 '继承基础模板，新增transmission(可选)与protection章节，排除log_retention', 1,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- ------------------------------------------------------------
-- 40003: DT-REQ-SEC-001 v1.0.0（安全，10 章节：含 protection + log_retention）
-- ------------------------------------------------------------
(40003, 40003, '1.0.0',
 '[{"code":"background","title":"项目背景目标","order":1,"fields":[{"type":"input","field":"background","title":"项目背景","props":{"type":"textarea","rows":4}}]},{"code":"topology","title":"网络拓扑","order":2,"fields":[{"type":"uploadFile","field":"topology_file","title":"拓扑图"},{"type":"input","field":"topology_desc","title":"拓扑说明"}]},{"code":"traffic","title":"流量规划","order":3,"fields":[{"type":"input","field":"traffic","title":"流量分析","props":{"type":"textarea","rows":3}}]},{"code":"business","title":"业务需求","order":4,"fields":[{"type":"input","field":"business","title":"业务描述","props":{"type":"textarea","rows":4}}]},{"code":"ip_plan","title":"IP规划","order":5,"fields":[{"type":"input","field":"ip_plan","title":"IP地址规划","props":{"type":"textarea","rows":4}}]},{"code":"redundancy","title":"冗余设计","order":6,"fields":[{"type":"input","field":"redundancy","title":"冗余方案","props":{"type":"textarea","rows":3}}]},{"code":"protection","title":"安全防护","order":7,"fields":[{"type":"input","field":"protection","title":"安全策略","props":{"type":"textarea","rows":4}}]},{"code":"o_and_m","title":"运维需求","order":8,"fields":[{"type":"input","field":"o_and_m","title":"运维要求","props":{"type":"textarea","rows":3}}]},{"code":"log_retention","title":"日志留存","order":9,"fields":[{"type":"input","field":"log_retention","title":"日志留存要求","props":{"type":"textarea","rows":3}}]},{"code":"interface","title":"接口关系","order":10,"fields":[{"type":"input","field":"interface_content","title":"接口关系内容","props":{"type":"textarea","rows":3}}]}]',
 NULL, NULL,
 '安全项目专用模板，含protection与log_retention章节', 1,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- ------------------------------------------------------------
-- 40004: DT-REQ-WIRELESS-001 v1.0.0（无线网，草稿，未发布）
--   含 8 个基础章节 + wireless 无线覆盖章节
-- ------------------------------------------------------------
(40004, 40004, '1.0.0',
 '[{"code":"background","title":"项目背景目标","order":1,"fields":[{"type":"input","field":"background","title":"项目背景","props":{"type":"textarea","rows":4}}]},{"code":"topology","title":"网络拓扑","order":2,"fields":[{"type":"uploadFile","field":"topology_file","title":"拓扑图"},{"type":"input","field":"topology_desc","title":"拓扑说明"}]},{"code":"traffic","title":"流量规划","order":3,"fields":[{"type":"input","field":"traffic","title":"流量分析","props":{"type":"textarea","rows":3}}]},{"code":"business","title":"业务需求","order":4,"fields":[{"type":"input","field":"business","title":"业务描述","props":{"type":"textarea","rows":4}}]},{"code":"ip_plan","title":"IP规划","order":5,"fields":[{"type":"input","field":"ip_plan","title":"IP地址规划","props":{"type":"textarea","rows":4}}]},{"code":"redundancy","title":"冗余设计","order":6,"fields":[{"type":"input","field":"redundancy","title":"冗余方案","props":{"type":"textarea","rows":3}}]},{"code":"wireless","title":"无线覆盖","order":7,"fields":[{"type":"input","field":"wireless_coverage","title":"覆盖规划","props":{"type":"textarea","rows":4}}]},{"code":"o_and_m","title":"运维需求","order":8,"fields":[{"type":"input","field":"o_and_m","title":"运维要求","props":{"type":"textarea","rows":3}}]},{"code":"interface","title":"接口关系","order":9,"fields":[{"type":"input","field":"interface_content","title":"接口关系内容","props":{"type":"textarea","rows":3}}]}]',
 NULL, NULL,
 '草稿版本，新增wireless无线覆盖章节', 0,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- ------------------------------------------------------------
-- 40005: DT-SOL-BASE-001 v1.0.0（方案基础模板，10 章节）
--   字段名对齐 pms_eng_solution 列：
--     background / target / team / inventory / plan / topology /
--     interface_plan / quality / risk / o_and_m
-- ------------------------------------------------------------
(40005, 40005, '1.0.0',
 '[{"code":"background","title":"项目背景","order":1,"fields":[{"type":"input","field":"background","title":"背景说明","props":{"type":"textarea","rows":4}}]},{"code":"target","title":"实施目标","order":2,"fields":[{"type":"input","field":"target","title":"目标描述","props":{"type":"textarea","rows":3}}]},{"code":"team","title":"实施团队","order":3,"fields":[{"type":"input","field":"team","title":"团队组成","props":{"type":"textarea","rows":3}}]},{"code":"inventory","title":"设备清单","order":4,"fields":[{"type":"input","field":"inventory","title":"设备清单","props":{"type":"textarea","rows":4}}]},{"code":"plan","title":"实施计划","order":5,"fields":[{"type":"input","field":"plan","title":"实施计划","props":{"type":"textarea","rows":4}}]},{"code":"topology","title":"网络拓扑","order":6,"fields":[{"type":"uploadFile","field":"topology_file","title":"拓扑图"},{"type":"input","field":"topology","title":"拓扑说明"}]},{"code":"interface_plan","title":"接口规划","order":7,"fields":[{"type":"input","field":"interface_plan","title":"接口规划","props":{"type":"textarea","rows":3}}]},{"code":"quality","title":"质量保障","order":8,"fields":[{"type":"input","field":"quality","title":"质量保障","props":{"type":"textarea","rows":3}}]},{"code":"risk","title":"风险控制","order":9,"fields":[{"type":"input","field":"risk","title":"风险控制","props":{"type":"textarea","rows":3}}]},{"code":"o_and_m","title":"运维交接","order":10,"fields":[{"type":"input","field":"o_and_m","title":"运维交接","props":{"type":"textarea","rows":3}}]}]',
 NULL, NULL,
 '初始版本，定义实施方案10个通用章节', 1,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- ------------------------------------------------------------
-- 40006: DT-SOL-DATA-001 v1.0.0（数据网方案，继承基础 + ip_plan + script）
-- ------------------------------------------------------------
(40006, 40006, '1.0.0',
 '[{"code":"background","title":"项目背景","order":1,"fields":[{"type":"input","field":"background","title":"背景说明","props":{"type":"textarea","rows":4}}]},{"code":"target","title":"实施目标","order":2,"fields":[{"type":"input","field":"target","title":"目标描述","props":{"type":"textarea","rows":3}}]},{"code":"team","title":"实施团队","order":3,"fields":[{"type":"input","field":"team","title":"团队组成","props":{"type":"textarea","rows":3}}]},{"code":"inventory","title":"设备清单","order":4,"fields":[{"type":"input","field":"inventory","title":"设备清单","props":{"type":"textarea","rows":4}}]},{"code":"plan","title":"实施计划","order":5,"fields":[{"type":"input","field":"plan","title":"实施计划","props":{"type":"textarea","rows":4}}]},{"code":"topology","title":"网络拓扑","order":6,"fields":[{"type":"uploadFile","field":"topology_file","title":"拓扑图"},{"type":"input","field":"topology","title":"拓扑说明"}]},{"code":"interface_plan","title":"接口规划","order":7,"fields":[{"type":"input","field":"interface_plan","title":"接口规划","props":{"type":"textarea","rows":3}}]},{"code":"ip_plan","title":"IP规划","order":8,"fields":[{"type":"input","field":"ip_plan","title":"IP地址规划","props":{"type":"textarea","rows":4}}]},{"code":"script","title":"配置脚本","order":9,"fields":[{"type":"input","field":"script","title":"自动化配置脚本","props":{"type":"textarea","rows":6}}]},{"code":"quality","title":"质量保障","order":10,"fields":[{"type":"input","field":"quality","title":"质量保障","props":{"type":"textarea","rows":3}}]},{"code":"risk","title":"风险控制","order":11,"fields":[{"type":"input","field":"risk","title":"风险控制","props":{"type":"textarea","rows":3}}]},{"code":"o_and_m","title":"运维交接","order":12,"fields":[{"type":"input","field":"o_and_m","title":"运维交接","props":{"type":"textarea","rows":3}}]}]',
 '{"ip_plan":{"required":true},"script":{"required":true}}',
 NULL,
 '继承方案基础模板，新增ip_plan与script章节', 1,
 'admin', NOW(), 'admin', NOW(), b'0', 1),

-- ------------------------------------------------------------
-- 40007: DT-SOL-SEC-001 v1.0.0（安全方案，继承基础 + ip_plan + script）
-- ------------------------------------------------------------
(40007, 40007, '1.0.0',
 '[{"code":"background","title":"项目背景","order":1,"fields":[{"type":"input","field":"background","title":"背景说明","props":{"type":"textarea","rows":4}}]},{"code":"target","title":"实施目标","order":2,"fields":[{"type":"input","field":"target","title":"目标描述","props":{"type":"textarea","rows":3}}]},{"code":"team","title":"实施团队","order":3,"fields":[{"type":"input","field":"team","title":"团队组成","props":{"type":"textarea","rows":3}}]},{"code":"inventory","title":"设备清单","order":4,"fields":[{"type":"input","field":"inventory","title":"设备清单","props":{"type":"textarea","rows":4}}]},{"code":"plan","title":"实施计划","order":5,"fields":[{"type":"input","field":"plan","title":"实施计划","props":{"type":"textarea","rows":4}}]},{"code":"topology","title":"网络拓扑","order":6,"fields":[{"type":"uploadFile","field":"topology_file","title":"拓扑图"},{"type":"input","field":"topology","title":"拓扑说明"}]},{"code":"interface_plan","title":"接口规划","order":7,"fields":[{"type":"input","field":"interface_plan","title":"接口规划","props":{"type":"textarea","rows":3}}]},{"code":"ip_plan","title":"IP规划","order":8,"fields":[{"type":"input","field":"ip_plan","title":"安全区域IP规划","props":{"type":"textarea","rows":4}}]},{"code":"script","title":"安全策略脚本","order":9,"fields":[{"type":"input","field":"script","title":"安全策略配置脚本","props":{"type":"textarea","rows":6}}]},{"code":"quality","title":"质量保障","order":10,"fields":[{"type":"input","field":"quality","title":"质量保障","props":{"type":"textarea","rows":3}}]},{"code":"risk","title":"风险控制","order":11,"fields":[{"type":"input","field":"risk","title":"风险控制","props":{"type":"textarea","rows":3}}]},{"code":"o_and_m","title":"运维交接","order":12,"fields":[{"type":"input","field":"o_and_m","title":"运维交接","props":{"type":"textarea","rows":3}}]}]',
 '{"ip_plan":{"required":true},"script":{"required":true}}',
 NULL,
 '继承方案基础模板，新增ip_plan与script(安全策略)章节', 1,
 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ============================================================
-- 3. 回填模板主表 current_version_id（草稿 40004 不回填）
-- ============================================================
UPDATE `pms_eng_doc_template` SET `current_version_id` = 40001 WHERE `id` = 40001 AND `deleted` = b'0';
UPDATE `pms_eng_doc_template` SET `current_version_id` = 40002 WHERE `id` = 40002 AND `deleted` = b'0';
UPDATE `pms_eng_doc_template` SET `current_version_id` = 40003 WHERE `id` = 40003 AND `deleted` = b'0';
UPDATE `pms_eng_doc_template` SET `current_version_id` = 40005 WHERE `id` = 40005 AND `deleted` = b'0';
UPDATE `pms_eng_doc_template` SET `current_version_id` = 40006 WHERE `id` = 40006 AND `deleted` = b'0';
UPDATE `pms_eng_doc_template` SET `current_version_id` = 40007 WHERE `id` = 40007 AND `deleted` = b'0';

-- ============================================================
-- 4. 关联现有项目需求/方案数据（项目 1001 数据网，关联数据网模板）
--    仅更新一条，避免影响其他测试用例
-- ============================================================
-- 项目 1001（北京华盛金融核心网络集成）需求 -> 数据网需求模板 40002
UPDATE `pms_eng_requirement`
   SET `template_id` = 40002, `template_version_id` = 40002
 WHERE `project_id` = 1001
   AND `id` = 1001
   AND `deleted` = b'0';

-- 项目 1001（北京华盛金融核心网络集成）方案 -> 数据网方案模板 40006
UPDATE `pms_eng_solution`
   SET `template_id` = 40006, `template_version_id` = 40006
 WHERE `project_id` = 1001
   AND `id` = 1001
   AND `deleted` = b'0';

SET FOREIGN_KEY_CHECKS = 1;
