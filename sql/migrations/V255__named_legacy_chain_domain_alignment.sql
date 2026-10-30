-- V255: 冻结判定标准修订后的点名旧链领域对齐（表存在不等于实现，须功能完整承接才可冻结）
-- 从原始表名一步 RENAME：撞名实体加 _record 区分；与IMP实施风险为不同实体的项目风险归PROJ。

RENAME TABLE
  -- ACC 域点名旧链（新表骨架不足以证明功能承接，原地领域化）
  `pms_acc_acceptance` TO `acc_acceptance_record`,
  `pms_acc_deliverable_checklist` TO `acc_deliverable_checklist`,
  `pms_acc_project_closure` TO `acc_project_closure_record`,
  -- PROJ 域项目风险（与 IMP 实施风险为不同实体，无承接关系）
  `pms_project_risk` TO `proj_project_risk`,
  -- IMP 域实施风险（eng 非领域，保留 eng_ 段对齐工程实施领域命名）
  `pms_eng_risk` TO `imp_eng_risk`;
