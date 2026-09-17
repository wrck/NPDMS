-- V255: 冻结判定标准修订后的点名旧链领域对齐（表存在不等于实现，须功能完整承接才可冻结）
-- 以及 eng 非领域修正：engineering 物理模块内表按 SOL/IMP 真领域前缀。

RENAME TABLE
  -- ACC 域点名旧链（新表骨架不足以证明功能承接，原地领域化；撞名实体加 _record 区分）
  `pms_acc_acceptance` TO `acc_acceptance_record`,
  `pms_acc_deliverable_checklist` TO `acc_deliverable_checklist`,
  `pms_acc_project_closure` TO `acc_project_closure_record`,
  -- PROJ 域项目风险（与 IMP 实施风险为不同实体，无承接关系）
  `pms_project_risk` TO `proj_project_risk`,
  -- IMP 域实施风险（eng 前缀非法，对齐 09 分册目标表名 imp_risk）
  `pms_eng_risk` TO `imp_risk`,
  -- SOL 域工期倒排（eng 前缀非法，按 ConstructionPlan 所属 SOL 域改 sol_）
  `pms_eng_schedule_backward` TO `sol_schedule_backward`,
  `pms_eng_schedule_backward_item` TO `sol_schedule_backward_item`;

-- 工期倒排菜单权限随 URL 领域前缀调整（/pms/eng-schedule-backward -> /pms/sol-schedule-backward 由代码承载）
UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:query', `update_time` = NOW()
WHERE `permission` = 'pms:eng-schedule-backward:query' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:create', `update_time` = NOW()
WHERE `permission` = 'pms:eng-schedule-backward:create' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:update', `update_time` = NOW()
WHERE `permission` = 'pms:eng-schedule-backward:update' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:delete', `update_time` = NOW()
WHERE `permission` = 'pms:eng-schedule-backward:delete' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:calculate', `update_time` = NOW()
WHERE `permission` = 'pms:eng-schedule-backward:calculate' AND `deleted` = b'0';

UPDATE `system_menu`
SET `permission` = 'pms:sol-schedule-backward:apply', `update_time` = NOW()
WHERE `permission` = 'pms:eng-schedule-backward:apply' AND `deleted` = b'0';
