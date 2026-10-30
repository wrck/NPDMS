-- V260: 旧项目主档初始化数据直接前向导入（AI-MIG-000 / 08a 第3节 Project STRUCTURED+CURRENT_FORWARD）
-- 数据源：pms_project 40 行初始化数据（冻结只读，本迁移只读不改不删）；一次性幂等插入三张新表。
-- 幂等守卫：主档按 (tenant_id, project_code) NOT EXISTS；阶段行按 (project_id, stage_code)；
--           树版本按 (tenant_id, root_project_id, tree_version)；闭包按唯一键列 NOT EXISTS，重放安全。
-- 字段口径（09-database-design 修订017 / ADR-0022）：
--   1. project_code = trim+upper；project_name 空名称不写默认（本数据源无空名称）。
--   2. customer 经 cus_customer_master 解析快照（未解析写 NULL）；manager 经 system_users
--      解析（未解析写 NULL，不落显示名）；company/department 不造（组织关系表无解析源）。
--   3. implementation_mode / major_project_level 未知值不写默认（NULL）；industry 仅名称
--      （industry_code NULL）；contract_code -> contract_no；project_type/category 原值携带
--      （MIGRATION_UPGRADE 不在字典，登记待映射，不冒充字典值）。
--   4. coarse status 兼容映射：0->S0/ACTIVE，1->S1/ACTIVE，2,3->S6/NORMAL_CLOSED；
--      closure_type/closed_at/project_close_time 不补造（NULL）；阶段行按创建语义写
--      （当前阶段 ACTIVE，其余 PENDING）。
--   5. 树结构按 ProjectTreeRules 规范化：根 tree_path=''，子 tree_path=path 去前导'/'；
--      root_id/tree_depth/tree_sort 原值（root_id 指向同迁移集内根行）。
--   6. 编码命名空间：code_root_id=自身、project_sequence=0、code_rule_version='LEGACY_IMPORT'
--      （不冒充 ADR-0020 生成）；source_type='MIGRATION'；progress=0；审计时间用迁移时点，
--      业务时间 project_start_time/project_refresh_time 取旧行 create_time/update_time。
--   7. 树闭包 REBUILD：每根一行 ACTIVE 树版本（tree_version=1）+ 自对(distance=0)与
--      根->子(distance=1)闭包行；预生成 ID 取 2600000000000+root_id / 2600000010000+
--      root_id*10000+descendant*10+distance 确定值段（与现存/雪花段不相交）。

-- 1/4: 主档 40 行（仅本迁移集：creator='migration' 幂等守卫辅助）
INSERT INTO `proj_project`
(`id`, `project_code`, `code_root_id`, `project_sequence`, `code_rule_version`,
 `project_name`, `parent_id`, `root_id`, `tree_path`, `tree_depth`, `tree_sort`,
 `customer_id`, `customer_code`, `customer_name`,
 `manager_id`, `manager_name`,
 `project_type`, `project_category`, `industry_name`, `contract_no`,
 `location_resolution_status`, `source_type`, `status`, `progress`,
 `lifecycle_status`, `current_stage`, `assignment_status`,
 `task_tree_version`, `task_progress_version`, `version`,
 `project_start_time`, `project_refresh_time`,
 `creator`, `updater`, `deleted`, `tenant_id`)
SELECT p.`id`,
       TRIM(UPPER(p.`code`)),
       p.`id`, 0, 'LEGACY_IMPORT',
       p.`name`,
       p.`parent_id`,
       p.`root_id`,
       CASE WHEN p.`parent_id` IS NULL OR p.`parent_id` = 0 THEN ''
            ELSE TRIM(LEADING '/' FROM p.`path`) END,
       COALESCE(p.`depth`, 0),
       COALESCE(p.`sort`, 0),
       cm.`id`, cm.`code`, cm.`name`,
       u.`id`, u.`nickname`,
       p.`project_type`, p.`category`, p.`industry`, p.`contract_code`,
       'UNRESOLVED', 'MIGRATION',
       CASE p.`status` WHEN 1 THEN 'S1' WHEN 2 THEN 'S6' WHEN 3 THEN 'S6' ELSE 'S0' END,
       0,
       CASE WHEN p.`status` >= 2 THEN 'NORMAL_CLOSED' ELSE 'ACTIVE' END,
       CASE p.`status` WHEN 1 THEN 'S1' WHEN 2 THEN 'S6' WHEN 3 THEN 'S6' ELSE 'S0' END,
       'UNASSIGNED',
       0, 0, 0,
       p.`create_time`, p.`update_time`,
       'migration', 'migration', b'0', p.`tenant_id`
FROM `pms_project` p
LEFT JOIN `cus_customer_master` cm
  ON cm.`id` = p.`customer_id` AND cm.`tenant_id` = p.`tenant_id` AND cm.`deleted` = b'0'
LEFT JOIN `system_users` u
  ON u.`id` = p.`manager_user_id` AND u.`tenant_id` = p.`tenant_id` AND u.`deleted` = b'0'
WHERE p.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project` q
                  WHERE q.`tenant_id` = p.`tenant_id`
                    AND q.`project_code` = TRIM(UPPER(p.`code`)) COLLATE utf8mb4_unicode_ci);

-- 2/4: 生命周期阶段行（创建语义：当前阶段 ACTIVE，其余 PENDING）
INSERT INTO `proj_project_stage`
(`project_id`, `stage_code`, `name`, `sort_order`, `status`, `version`,
 `creator`, `updater`, `deleted`, `tenant_id`)
SELECT mp.`id`, s.`stage_code`, s.`name`, s.`sort_order`,
       CASE WHEN s.`stage_code` COLLATE utf8mb4_unicode_ci = mp.`current_stage` THEN 'ACTIVE' ELSE 'PENDING' END,
       0, 'migration', 'migration', b'0', mp.`tenant_id`
FROM `proj_project` mp
JOIN (SELECT 'S0' stage_code, '项目启动' name, 0 sort_order
      UNION ALL SELECT 'S1', '工前准备', 1
      UNION ALL SELECT 'S2', '施工计划', 2
      UNION ALL SELECT 'S3', '方案编审', 3
      UNION ALL SELECT 'S4', '实施部署', 4
      UNION ALL SELECT 'S5', '验收交维', 5
      UNION ALL SELECT 'S6', '项目闭环', 6) s
WHERE mp.`creator` = 'migration' AND mp.`deleted` = b'0' AND mp.`source_type` = 'MIGRATION'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_stage` ps
                  WHERE ps.`project_id` = mp.`id` AND ps.`stage_code` = s.`stage_code`
                    AND ps.`deleted` = b'0');

-- 3/4: 树版本（每根一行，ACTIVE，tree_version=1）
INSERT INTO `proj_project_tree_version`
(`id`, `root_project_id`, `tree_version`, `status`, `change_batch_id`,
 `node_count`, `path_count`, `activated_at`, `version`,
 `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 2600000000000 + mp.`root_id`, mp.`root_id`, 1, 'ACTIVE',
       CONCAT('PMS-PROJECT-LEGACY-IMPORT-TREE-', mp.`root_id`),
       COUNT(*),
       COUNT(*) + SUM(CASE WHEN mp.`tree_depth` > 0 THEN 1 ELSE 0 END),
       NOW(), 0,
       'migration', 'migration', b'0', mp.`tenant_id`
FROM `proj_project` mp
WHERE mp.`creator` = 'migration' AND mp.`deleted` = b'0' AND mp.`source_type` = 'MIGRATION'
GROUP BY mp.`tenant_id`, mp.`root_id`
HAVING NOT EXISTS (SELECT 1 FROM `proj_project_tree_version` tv
                   WHERE tv.`tenant_id` = mp.`tenant_id`
                     AND tv.`root_project_id` = mp.`root_id` AND tv.`tree_version` = 1);

-- 4/4: 树闭包（自对 distance=0 + 根->子 distance=1；确定值段预生成 ID）
INSERT INTO `proj_project_tree_path`
(`id`, `tree_version`, `root_project_id`, `ancestor_project_id`, `descendant_project_id`,
 `distance`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 2600000010000 + mp.`root_id` * 10000 + mp.`id` * 10 + 0,
       1, mp.`root_id`, mp.`id`, mp.`id`, 0,
       0, 'migration', 'migration', b'0', mp.`tenant_id`
FROM `proj_project` mp
WHERE mp.`creator` = 'migration' AND mp.`deleted` = b'0' AND mp.`source_type` = 'MIGRATION'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_tree_path` tp
                  WHERE tp.`tenant_id` = mp.`tenant_id`
                    AND tp.`root_project_id` = mp.`root_id` AND tp.`tree_version` = 1
                    AND tp.`ancestor_project_id` = mp.`id`
                    AND tp.`descendant_project_id` = mp.`id`);

INSERT INTO `proj_project_tree_path`
(`id`, `tree_version`, `root_project_id`, `ancestor_project_id`, `descendant_project_id`,
 `distance`, `version`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 2600000010000 + mp.`root_id` * 10000 + mp.`id` * 10 + 1,
       1, mp.`root_id`, mp.`root_id`, mp.`id`, 1,
       0, 'migration', 'migration', b'0', mp.`tenant_id`
FROM `proj_project` mp
WHERE mp.`creator` = 'migration' AND mp.`deleted` = b'0' AND mp.`source_type` = 'MIGRATION'
  AND mp.`tree_depth` > 0
  AND NOT EXISTS (SELECT 1 FROM `proj_project_tree_path` tp
                  WHERE tp.`tenant_id` = mp.`tenant_id`
                    AND tp.`root_project_id` = mp.`root_id` AND tp.`tree_version` = 1
                    AND tp.`ancestor_project_id` = mp.`root_id`
                    AND tp.`descendant_project_id` = mp.`id`);
