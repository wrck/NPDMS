-- =============================================================================
-- V29: 测试数据补充（覆盖空表 + 扩展项目覆盖）
-- 目标：
--   1) 填充空表 pms_customer_service_level / pms_project_portfolio / portfolio_member / portfolio_rule
--   2) 扩充 pms_eng_form_template（5→10，覆盖更多产品类型）
--   3) 为项目 1011-1015 补充关联实体数据（每实体≥3条，覆盖各种状态）
-- 满足"不少于10个项目，不同阶段不同层级，每项目每关联≥3条"要求
-- =============================================================================

-- ========== 1. 客户服务等级（pms_customer_service_level） ==========
-- 8 个客户 × 3 个等级（STRATEGIC/IMPORTANT/STANDARD）= 24 条，覆盖不同状态
INSERT INTO `pms_customer_service_level` (`customer_id`, `level`, `valid_from`, `valid_to`, `status`, `response_time_hours`, `proactive_service`, `remark`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
-- 客户1
(1, 'STRATEGIC', '2026-01-01', '2026-12-31', 1, 2, b'1', '战略客户：2小时响应，主动服务', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1, 'IMPORTANT', '2025-06-01', '2025-12-31', 3, 4, b'0', '历史等级：重要客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1, 'STANDARD', '2024-01-01', '2024-12-31', 2, 8, b'0', '历史等级：标准客户（已停用）', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 客户2
(2, 'IMPORTANT', '2026-01-01', '2026-12-31', 1, 4, b'1', '重要客户：4小时响应，主动服务', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(2, 'STANDARD', '2025-01-01', '2025-12-31', 3, 8, b'0', '历史等级：标准客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(2, 'GENERAL', '2024-01-01', '2024-12-31', 2, 24, b'0', '历史等级：一般客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 客户3
(3, 'STANDARD', '2026-01-01', '2026-12-31', 1, 8, b'0', '标准客户：8小时响应', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(3, 'GENERAL', '2025-06-01', '2025-12-31', 3, 24, b'0', '历史等级：一般客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(3, 'IMPORTANT', '2024-01-01', '2024-12-31', 2, 4, b'0', '历史等级：重要客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 客户1001
(1001, 'STRATEGIC', '2026-01-01', '2026-12-31', 1, 2, b'1', '战略客户：2小时响应，主动服务', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1001, 'IMPORTANT', '2025-01-01', '2025-12-31', 3, 4, b'0', '历史等级：重要客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1001, 'STANDARD', '2024-01-01', '2024-12-31', 2, 8, b'0', '历史等级：标准客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 客户1002
(1002, 'IMPORTANT', '2026-01-01', '2026-12-31', 1, 4, b'1', '重要客户：4小时响应，主动服务', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1002, 'STANDARD', '2025-01-01', '2025-12-31', 3, 8, b'0', '历史等级：标准客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1002, 'GENERAL', '2024-01-01', '2024-12-31', 2, 24, b'0', '历史等级：一般客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 客户1003
(1003, 'STANDARD', '2026-01-01', '2026-12-31', 1, 8, b'0', '标准客户：8小时响应', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1003, 'IMPORTANT', '2025-01-01', '2025-12-31', 3, 4, b'0', '历史等级：重要客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1003, 'GENERAL', '2024-01-01', '2024-12-31', 2, 24, b'0', '历史等级：一般客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 客户1004
(1004, 'STRATEGIC', '2026-01-01', '2026-12-31', 1, 2, b'1', '战略客户：2小时响应，主动服务', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1004, 'IMPORTANT', '2025-01-01', '2025-12-31', 3, 4, b'0', '历史等级：重要客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1004, 'STANDARD', '2024-01-01', '2024-12-31', 2, 8, b'0', '历史等级：标准客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 客户1005
(1005, 'IMPORTANT', '2026-01-01', '2026-12-31', 1, 4, b'1', '重要客户：4小时响应，主动服务', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1005, 'STANDARD', '2025-01-01', '2025-12-31', 3, 8, b'0', '历史等级：标准客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1005, 'GENERAL', '2024-01-01', '2024-12-31', 2, 24, b'0', '历史等级：一般客户', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ========== 2. 项目组合 pms_project_portfolio（4个组合，覆盖不同状态/类型） ==========
INSERT INTO `pms_project_portfolio` (`code`, `name`, `purpose`, `owner_user_id`, `valid_from`, `valid_to`, `status`, `target_metrics`, `member_type`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
('PF-2026-001', '2026年度战略项目组合', '战略', 1, '2026-01-01', '2026-12-31', 1, '{"revenue":100000000,"projects":15}', 'STATIC', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
('PF-2026-002', '金融行业客户项目组合', '客户', 1, '2026-01-01', '2026-12-31', 1, '{"revenue":50000000,"customers":8}', 'DYNAMIC', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
('PF-2026-003', '华北区域项目组合', '区域', 1, '2026-01-01', '2026-12-31', 0, '{"revenue":30000000,"projects":10}', 'DYNAMIC', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
('PF-2025-001', '2025年度已完成项目组合', '计划', 1, '2025-01-01', '2025-12-31', 2, '{"revenue":80000000,"projects":20}', 'STATIC', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ========== 3. 项目组合成员 pms_project_portfolio_member（每组合≥3项目，覆盖纳入/排除） ==========
INSERT INTO `pms_project_portfolio_member` (`portfolio_id`, `project_id`, `inclusion_type`, `inclusion_reason`, `exclusion_reason`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
-- 组合1（战略）：纳入1001-1005
(1, 1001, 'STATIC', '战略客户重点项目', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1, 1002, 'STATIC', '战略客户重点项目', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1, 1003, 'STATIC', '战略客户重点项目', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1, 1004, 'STATIC', '战略客户重点项目', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(1, 1005, 'STATIC', '战略客户重点项目', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 组合2（金融客户）：动态纳入+排除
(2, 1006, 'DYNAMIC', '匹配金融客户规则', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(2, 1007, 'DYNAMIC', '匹配金融客户规则', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(2, 1008, 'DYNAMIC', '匹配金融客户规则', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(2, 1009, 'DYNAMIC', NULL, '非金融客户', 2, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(2, 1010, 'DYNAMIC', NULL, '非金融客户', 2, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 组合3（华北区域）：动态
(3, 1001, 'DYNAMIC', '匹配华北区域规则', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(3, 1004, 'DYNAMIC', '匹配华北区域规则', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(3, 1007, 'DYNAMIC', '匹配华北区域规则', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(3, 1010, 'DYNAMIC', '匹配华北区域规则', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 组合4（2025已完成）：静态
(4, 1001, 'STATIC', '2025年完成项目', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(4, 1002, 'STATIC', '2025年完成项目', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(4, 1003, 'STATIC', '2025年完成项目', NULL, 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ========== 4. 项目组合动态规则 pms_project_portfolio_rule（动态组合规则） ==========
INSERT INTO `pms_project_portfolio_rule` (`portfolio_id`, `rule_field`, `rule_operator`, `rule_value`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
-- 组合2（金融客户）：客户ID IN (1001,1002,1003)
(2, 'CUSTOMER', 'IN', '1001,1002,1003', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(2, 'TYPE', 'EQ', 'FINANCE', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
-- 组合3（华北区域）：区域=华北
(3, 'REGION', 'EQ', 'HUABEI', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
(3, 'STATUS', 'IN', '1,2,3', 1, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ========== 5. 扩充表单模板 pms_eng_form_template（5→10，新增5个不同产品类型） ==========
INSERT INTO `pms_eng_form_template` (`code`, `name`, `product_type`, `conf`, `fields`, `description`, `status`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
('FT-2026-006', 'WAF Web应用防火墙采集表单', 'WAF', '{"form":{"labelPosition":"top","size":"default"}}', '[{"type":"input","field":"deviceModel","title":"设备型号"},{"type":"select","field":"deployMode","title":"部署方式","options":[{"label":"反向代理","value":"reverse-proxy"},{"label":"透明","value":"transparent"}]},{"type":"inputNumber","field":"throughput","title":"吞吐量"},{"type":"uploadFile","field":"policyFile","title":"策略文件"},{"type":"editor","field":"remark","title":"备注"}]', 'WAF产品采集模板', 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
('FT-2026-007', 'VPN虚拟专网采集表单', 'VPN', '{"form":{"labelPosition":"top","size":"default"}}', '[{"type":"input","field":"deviceModel","title":"设备型号"},{"type":"select","field":"vpnType","title":"VPN类型","options":[{"label":"IPSec","value":"ipsec"},{"label":"SSL","value":"ssl"},{"label":"L2TP","value":"l2tp"}]},{"type":"inputNumber","field":"concurrentUsers","title":"并发用户数"},{"type":"uploadFile","field":"certFile","title":"证书文件"}]', 'VPN产品采集模板', 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
('FT-2026-008', '数据库审计采集表单', 'DB_AUDIT', '{"form":{"labelPosition":"top","size":"default"}}', '[{"type":"input","field":"deviceModel","title":"设备型号"},{"type":"select","field":"dbType","title":"数据库类型","options":[{"label":"MySQL","value":"mysql"},{"label":"Oracle","value":"oracle"},{"label":"PostgreSQL","value":"postgresql"}]},{"type":"inputNumber","field":"instanceCount","title":"实例数"},{"type":"editor","field":"auditRule","title":"审计规则"}]', '数据库审计产品采集模板', 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
('FT-2026-009', '日志审计采集表单', 'LOG_AUDIT', '{"form":{"labelPosition":"top","size":"default"}}', '[{"type":"input","field":"deviceModel","title":"设备型号"},{"type":"inputNumber","field":"eps","title":"日志EPS"},{"type":"select","field":"storageType","title":"存储类型","options":[{"label":"本地","value":"local"},{"label":"云存储","value":"cloud"}]},{"type":"uploadFile","field":"logConfig","title":"日志配置"}]', '日志审计产品采集模板', 0, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1),
('FT-2026-010', '态势感知采集表单', 'SITUATION', '{"form":{"labelPosition":"top","size":"default"}}', '[{"type":"input","field":"deviceModel","title":"设备型号"},{"type":"select","field":"deployMode","title":"部署方式","options":[{"label":"分布式","value":"distributed"},{"label":"集中式","value":"centralized"}]},{"type":"inputNumber","field":"nodeCount","title":"节点数"},{"type":"editor","field":"scope","title":"感知范围"},{"type":"uploadFile","field":"topology","title":"拓扑图"}]', '态势感知产品采集模板', 1, 1, 'admin', NOW(), 'admin', NOW(), b'0', 1);

-- ========== 6. 为项目 1011-1015 补充关联数据（每实体3条） ==========

-- 6.1 工程交底书（项目1011-1015，每项目3条，覆盖各状态）
INSERT INTO `pms_eng_briefing` (`code`, `project_id`, `name`, `briefing_type`, `template_id`, `template_snapshot`, `source_snapshot`, `content`, `file_url`, `file_name`, `file_size`, `file_checksum`, `status`, `version`, `generate_time`, `publish_time`, `approver_user_id`, `approve_opinion`, `approve_time`, `creator_user_id`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
('BR-2026-031', 1011, 'WAF部署交底书', 'STANDARD', 6, '{"templateName":"WAF Web应用防火墙采集表单","version":1}', '{"requirement":"REQ-031 WAF需求","solution":"SOL-011 WAF方案"}', '<h1>WAF部署交底书</h1><p>WAF部署和策略配置。</p>', '/api/file/briefing-031.pdf', 'briefing-031.pdf', 102400, 'waf001hash', 3, 1, '2026-03-05 10:00:00', '2026-03-06 09:00:00', 1, '通过', '2026-03-06 08:00:00', 1, NULL, 'admin', '2026-03-05 09:00:00', 'admin', '2026-03-06 09:00:00', b'0', 1),
('BR-2026-032', 1011, 'WAF策略调优交底书', 'STANDARD', 6, '{"templateName":"WAF Web应用防火墙采集表单","version":1}', '{"requirement":"REQ-032 调优需求"}', '<h1>WAF策略调优交底书</h1><p>策略调优步骤。</p>', NULL, NULL, NULL, NULL, 2, 1, '2026-03-15 10:00:00', NULL, 1, NULL, NULL, 1, '待发布', 'admin', '2026-03-15 09:00:00', 'admin', '2026-03-15 10:00:00', b'0', 1),
('BR-2026-033', 1011, 'WAF紧急处置交底书', 'EMERGENCY', NULL, NULL, '{"requirement":"REQ-033 紧急处置"}', '<h1>WAF紧急处置交底书</h1><p>紧急处置流程。</p>', NULL, NULL, NULL, NULL, 0, 1, NULL, NULL, NULL, NULL, NULL, 1, '草稿', 'admin', '2026-03-25 09:00:00', 'admin', '2026-03-25 09:00:00', b'0', 1),
('BR-2026-034', 1012, 'VPN部署交底书', 'STANDARD', 7, '{"templateName":"VPN虚拟专网采集表单","version":1}', '{"requirement":"REQ-034 VPN需求","solution":"SOL-012 VPN方案"}', '<h1>VPN部署交底书</h1><p>VPN部署和证书配置。</p>', '/api/file/briefing-034.pdf', 'briefing-034.pdf', 112640, 'vpn001hash', 3, 1, '2026-03-08 10:00:00', '2026-03-09 09:00:00', 1, '通过', '2026-03-09 08:00:00', 1, NULL, 'admin', '2026-03-08 09:00:00', 'admin', '2026-03-09 09:00:00', b'0', 1),
('BR-2026-035', 1012, 'VPN升级交底书', 'STANDARD', 7, '{"templateName":"VPN虚拟专网采集表单","version":1}', '{"requirement":"REQ-035 升级需求"}', '<h1>VPN升级交底书</h1><p>升级流程和回退方案。</p>', NULL, NULL, NULL, NULL, 1, 1, '2026-03-18 10:00:00', NULL, NULL, NULL, NULL, 1, '已生成', 'admin', '2026-03-18 09:00:00', 'admin', '2026-03-18 10:00:00', b'0', 1),
('BR-2026-036', 1012, 'VPN故障处理交底书', 'EMERGENCY', NULL, NULL, '{"requirement":"REQ-036 故障处理"}', '<h1>VPN故障处理交底书</h1><p>故障诊断和处理。</p>', NULL, NULL, NULL, NULL, 4, 1, '2026-03-22 10:00:00', NULL, 1, '作废：方案变更', '2026-03-22 12:00:00', 1, '已作废', 'admin', '2026-03-22 09:00:00', 'admin', '2026-03-22 12:00:00', b'0', 1),
('BR-2026-037', 1013, '数据库审计部署交底书', 'STANDARD', 8, '{"templateName":"数据库审计采集表单","version":1}', '{"requirement":"REQ-037 DB审计需求","solution":"SOL-013 DB审计方案"}', '<h1>数据库审计部署交底书</h1><p>数据库审计部署。</p>', '/api/file/briefing-037.pdf', 'briefing-037.pdf', 102400, 'dba001hash', 3, 1, '2026-03-10 10:00:00', '2026-03-11 09:00:00', 1, '通过', '2026-03-11 08:00:00', 1, NULL, 'admin', '2026-03-10 09:00:00', 'admin', '2026-03-11 09:00:00', b'0', 1),
('BR-2026-038', 1013, '数据库审计规则配置交底书', 'STANDARD', 8, '{"templateName":"数据库审计采集表单","version":1}', '{"requirement":"REQ-038 规则配置"}', '<h1>数据库审计规则配置交底书</h1><p>规则配置步骤。</p>', NULL, NULL, NULL, NULL, 2, 1, '2026-03-20 10:00:00', NULL, 1, NULL, NULL, 1, '待发布', 'admin', '2026-03-20 09:00:00', 'admin', '2026-03-20 10:00:00', b'0', 1),
('BR-2026-039', 1013, '数据库审计紧急变更交底书', 'EMERGENCY', NULL, NULL, '{"requirement":"REQ-039 紧急变更"}', '<h1>数据库审计紧急变更交底书</h1><p>紧急变更流程。</p>', NULL, NULL, NULL, NULL, 0, 1, NULL, NULL, NULL, NULL, NULL, 1, '草稿', 'admin', '2026-03-28 09:00:00', 'admin', '2026-03-28 09:00:00', b'0', 1),
('BR-2026-040', 1014, '日志审计部署交底书', 'STANDARD', 9, '{"templateName":"日志审计采集表单","version":1}', '{"requirement":"REQ-040 日志审计需求","solution":"SOL-014 日志审计方案"}', '<h1>日志审计部署交底书</h1><p>日志审计部署。</p>', '/api/file/briefing-040.pdf', 'briefing-040.pdf', 90112, 'log001hash', 3, 1, '2026-03-12 10:00:00', '2026-03-13 09:00:00', 1, '通过', '2026-03-13 08:00:00', 1, NULL, 'admin', '2026-03-12 09:00:00', 'admin', '2026-03-13 09:00:00', b'0', 1),
('BR-2026-041', 1014, '日志审计扩容交底书', 'STANDARD', NULL, NULL, '{"requirement":"REQ-041 扩容需求"}', '<h1>日志审计扩容交底书</h1><p>扩容步骤。</p>', NULL, NULL, NULL, NULL, 1, 1, '2026-03-22 10:00:00', NULL, NULL, NULL, NULL, 1, '已生成', 'admin', '2026-03-22 09:00:00', 'admin', '2026-03-22 10:00:00', b'0', 1),
('BR-2026-042', 1014, '日志审计故障处理交底书', 'EMERGENCY', NULL, NULL, '{"requirement":"REQ-042 故障处理"}', '<h1>日志审计故障处理交底书</h1><p>故障处理流程。</p>', NULL, NULL, NULL, NULL, 0, 1, NULL, NULL, NULL, NULL, NULL, 1, '草稿', 'admin', '2026-03-30 09:00:00', 'admin', '2026-03-30 09:00:00', b'0', 1),
('BR-2026-043', 1015, '态势感知部署交底书', 'STANDARD', 10, '{"templateName":"态势感知采集表单","version":1}', '{"requirement":"REQ-043 态势感知需求","solution":"SOL-015 态势感知方案"}', '<h1>态势感知部署交底书</h1><p>态势感知平台部署。</p>', '/api/file/briefing-043.pdf', 'briefing-043.pdf', 122880, 'sit001hash', 3, 1, '2026-03-15 10:00:00', '2026-03-16 09:00:00', 1, '通过', '2026-03-16 08:00:00', 1, NULL, 'admin', '2026-03-15 09:00:00', 'admin', '2026-03-16 09:00:00', b'0', 1),
('BR-2026-044', 1015, '态势感知集成交底书', 'STANDARD', 10, '{"templateName":"态势感知采集表单","version":1}', '{"requirement":"REQ-044 集成需求"}', '<h1>态势感知集成交底书</h1><p>与其他系统集成。</p>', NULL, NULL, NULL, NULL, 2, 1, '2026-03-25 10:00:00', NULL, 1, NULL, NULL, 1, '待发布', 'admin', '2026-03-25 09:00:00', 'admin', '2026-03-25 10:00:00', b'0', 1),
('BR-2026-045', 1015, '态势感知紧急扩容交底书', 'EMERGENCY', NULL, NULL, '{"requirement":"REQ-045 紧急扩容"}', '<h1>态势感知紧急扩容交底书</h1><p>紧急扩容方案。</p>', NULL, NULL, NULL, NULL, 0, 1, NULL, NULL, NULL, NULL, NULL, 1, '草稿', 'admin', '2026-04-01 09:00:00', 'admin', '2026-04-01 09:00:00', b'0', 1);

-- 6.2 表单实例（项目1011-1015，每项目3条，覆盖各状态）
-- 表字段：code, project_id, template_id, template_snapshot, form_data, name, status, version, submit_time, approver_user_id, approve_opinion, approve_time, filler_user_id, remark
INSERT INTO `pms_eng_form_instance` (`code`, `project_id`, `template_id`, `template_snapshot`, `form_data`, `name`, `status`, `version`, `submit_time`, `approver_user_id`, `approve_opinion`, `approve_time`, `filler_user_id`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
('FI-2026-031', 1011, 6, '{"templateName":"WAF Web应用防火墙采集表单","version":1}', '{"deviceModel":"WAF-2000","deployMode":"reverse-proxy","throughput":2000}', 'WAF设备采集', 3, 1, '2026-03-07 10:00:00', 1, '通过', '2026-03-08 09:00:00', 1, NULL, 'admin', '2026-03-06 09:00:00', 'admin', '2026-03-08 09:00:00', b'0', 1),
('FI-2026-032', 1011, 6, '{"templateName":"WAF Web应用防火墙采集表单","version":1}', '{"deviceModel":"WAF-2000","policyFile":"policy.json"}', 'WAF策略采集', 1, 1, NULL, NULL, NULL, NULL, 1, '已填未提交', 'admin', '2026-03-10 09:00:00', 'admin', '2026-03-10 10:00:00', b'0', 1),
('FI-2026-033', 1011, 5, '{"templateName":"通用准备数据表单","version":1}', '{"projectName":"WAF部署项目","customerName":"客户1001"}', 'WAF项目基础数据', 0, 1, NULL, NULL, NULL, NULL, 1, '待填', 'admin', '2026-03-12 09:00:00', 'admin', '2026-03-12 09:00:00', b'0', 1),
('FI-2026-034', 1012, 7, '{"templateName":"VPN虚拟专网采集表单","version":1}', '{"deviceModel":"VPN-3000","vpnType":"ipsec","concurrentUsers":500}', 'VPN设备采集', 3, 1, '2026-03-10 10:00:00', 1, '通过', '2026-03-11 09:00:00', 1, NULL, 'admin', '2026-03-09 09:00:00', 'admin', '2026-03-11 09:00:00', b'0', 1),
('FI-2026-035', 1012, 7, '{"templateName":"VPN虚拟专网采集表单","version":1}', '{"certFile":"cert.p12"}', 'VPN证书采集', 4, 1, '2026-03-14 10:00:00', 1, '驳回：证书格式错误', '2026-03-15 09:00:00', 1, '已驳回', 'admin', '2026-03-13 09:00:00', 'admin', '2026-03-15 09:00:00', b'0', 1),
('FI-2026-036', 1012, 5, '{"templateName":"通用准备数据表单","version":1}', '{"projectName":"VPN部署项目","priority":"high"}', 'VPN项目基础数据', 0, 1, NULL, NULL, NULL, NULL, 1, '待填', 'admin', '2026-03-16 09:00:00', 'admin', '2026-03-16 09:00:00', b'0', 1),
('FI-2026-037', 1013, 8, '{"templateName":"数据库审计采集表单","version":1}', '{"deviceModel":"DBA-1000","dbType":"mysql","instanceCount":10}', 'DB审计设备采集', 3, 1, '2026-03-12 10:00:00', 1, '通过', '2026-03-13 09:00:00', 1, NULL, 'admin', '2026-03-11 09:00:00', 'admin', '2026-03-13 09:00:00', b'0', 1),
('FI-2026-038', 1013, 8, '{"templateName":"数据库审计采集表单","version":1}', '{"auditRule":"规则1"}', 'DB审计规则采集', 2, 1, '2026-03-16 10:00:00', NULL, NULL, NULL, 1, '已提交待审', 'admin', '2026-03-15 09:00:00', 'admin', '2026-03-16 10:00:00', b'0', 1),
('FI-2026-039', 1013, 5, '{"templateName":"通用准备数据表单","version":1}', '{"projectName":"DB审计项目","priority":"medium"}', 'DB审计项目基础数据', 0, 1, NULL, NULL, NULL, NULL, 1, '待填', 'admin', '2026-03-18 09:00:00', 'admin', '2026-03-18 09:00:00', b'0', 1),
('FI-2026-040', 1014, 9, '{"templateName":"日志审计采集表单","version":1}', '{"deviceModel":"LOG-2000","eps":5000,"storageType":"local"}', '日志审计设备采集', 3, 1, '2026-03-14 10:00:00', 1, '通过', '2026-03-15 09:00:00', 1, NULL, 'admin', '2026-03-13 09:00:00', 'admin', '2026-03-15 09:00:00', b'0', 1),
('FI-2026-041', 1014, 9, '{"templateName":"日志审计采集表单","version":1}', '{"logConfig":"log_config.json"}', '日志配置采集', 1, 1, NULL, NULL, NULL, NULL, 1, '已填未提交', 'admin', '2026-03-17 09:00:00', 'admin', '2026-03-17 10:00:00', b'0', 1),
('FI-2026-042', 1014, 5, '{"templateName":"通用准备数据表单","version":1}', '{"projectName":"日志审计项目","priority":"low"}', '日志审计项目基础数据', 0, 1, NULL, NULL, NULL, NULL, 1, '待填', 'admin', '2026-03-19 09:00:00', 'admin', '2026-03-19 09:00:00', b'0', 1),
('FI-2026-043', 1015, 10, '{"templateName":"态势感知采集表单","version":1}', '{"deviceModel":"SIT-5000","deployMode":"distributed","nodeCount":5}', '态势感知设备采集', 3, 1, '2026-03-17 10:00:00', 1, '通过', '2026-03-18 09:00:00', 1, NULL, 'admin', '2026-03-16 09:00:00', 'admin', '2026-03-18 09:00:00', b'0', 1),
('FI-2026-044', 1015, 10, '{"templateName":"态势感知采集表单","version":1}', '{"topology":"topology.png"}', '态势感知拓扑采集', 4, 1, '2026-03-21 10:00:00', 1, '驳回：拓扑图不清晰', '2026-03-22 09:00:00', 1, '已驳回', 'admin', '2026-03-20 09:00:00', 'admin', '2026-03-22 09:00:00', b'0', 1),
('FI-2026-045', 1015, 5, '{"templateName":"通用准备数据表单","version":1}', '{"projectName":"态势感知项目","priority":"high"}', '态势感知项目基础数据', 0, 1, NULL, NULL, NULL, NULL, 1, '待填', 'admin', '2026-03-23 09:00:00', 'admin', '2026-03-23 09:00:00', b'0', 1);
