-- =============================================================================
-- V40: PMS 菜单按领域顶级重分类
-- 背景：原菜单结构中「项目交付」下平铺 49 个可见菜单，无领域分组，查找困难。
--      本迁移按业务领域重分类，合并客户与资产为独立顶级，去掉无意义的中间层级。
--
-- 新菜单树：
-- 1. 项目交付 (18000)
--    ├── 项目管理 (19261) — 项目总览/项目团队/项目风险(直接) + 计划进度/组合与治理(分组)
--    ├── 工程实施 (19262) — 工程准备/工程执行/资源采供/文档表单/工程保障(分组)
--    ├── 割接交付 (19263) — 割接任务/方案/执行/观察(平铺)
--    ├── 验收闭环 (19264) — 电子完工证明/验收管理/交付件检查/项目闭环/归档文档/转维保(平铺)
--    └── 巡检维保 (19265) — 巡检任务/规则/报告/问题/维保状态(平铺)
-- 2. 客户与资产 (19260) — 客户管理/客户联系人/设备档案管理(平铺)
--
-- 从属界面（不单独建菜单，从主入口打开）：
--   项目详情、项目全景、项目树、阶段模板、公告预检查、割接风险、表单填报、设备配置日志
-- =============================================================================

-- ============================================================
-- 1. 新建目录菜单（13 个：1 顶级 + 5 二级 + 7 三级分组）
-- ============================================================
INSERT IGNORE INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
 `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
-- ========== 顶级：客户与资产 ==========
(19260, '客户与资产', '', 1, 30, 0, '/pms/customer-asset', 'ep:briefcase', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 项目交付下 2 级目录 ==========
(19261, '项目管理', '', 1, 1, 18000, 'project-management', 'ep:folder', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19262, '工程实施', '', 1, 2, 18000, 'engineering', 'ep:tools', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19263, '割接交付', '', 1, 3, 18000, 'cutover', 'ep:switch', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19264, '验收闭环', '', 1, 4, 18000, 'acceptance', 'ep:circle-check', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19265, '巡检维保', '', 1, 5, 18000, 'service', 'ep:shield', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 项目管理下 3 级分组 ==========
(19266, '计划进度', '', 1, 3, 19261, 'schedule', 'ep:calendar', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19267, '组合与治理', '', 1, 5, 19261, 'governance', 'ep:set-up', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
-- ========== 工程实施下 3 级分组 ==========
(19268, '工程准备', '', 1, 1, 19262, 'preparation', 'ep:document', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19269, '工程执行', '', 1, 2, 19262, 'execution', 'ep:cpu', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19270, '资源采供', '', 1, 3, 19262, 'procurement', 'ep:shopping-cart', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19271, '文档表单', '', 1, 4, 19262, 'document', 'ep:folder-opened', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(19272, '工程保障', '', 1, 5, 19262, 'safeguard', 'ep:warning', NULL, NULL,
 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0');

-- ============================================================
-- 2. 改名：项目主数据 → 项目总览
-- ============================================================
UPDATE `system_menu` SET `name` = '项目总览', `update_time` = NOW()
WHERE `id` = 18011 AND `deleted` = b'0';

-- ============================================================
-- 3. 迁移可见菜单到新分组（更新 parent_id + sort）
-- ============================================================

-- 3.1 项目管理（19261）下直接挂载
UPDATE `system_menu` SET `parent_id` = 19261, `sort` = 1, `update_time` = NOW() WHERE `id` = 18011; -- 项目总览
UPDATE `system_menu` SET `parent_id` = 19261, `sort` = 2, `update_time` = NOW() WHERE `id` = 18013; -- 项目团队
UPDATE `system_menu` SET `parent_id` = 19261, `sort` = 4, `update_time` = NOW() WHERE `id` = 18017; -- 项目风险

-- 3.2 计划进度（19266）下
UPDATE `system_menu` SET `parent_id` = 19266, `sort` = 1, `update_time` = NOW() WHERE `id` = 18014; -- 任务WBS
UPDATE `system_menu` SET `parent_id` = 19266, `sort` = 2, `update_time` = NOW() WHERE `id` = 18016; -- 项目阶段
UPDATE `system_menu` SET `parent_id` = 19266, `sort` = 3, `update_time` = NOW() WHERE `id` = 19145; -- 工期倒排
UPDATE `system_menu` SET `parent_id` = 19266, `sort` = 4, `update_time` = NOW() WHERE `id` = 19151; -- 计划变更审批

-- 3.3 组合与治理（19267）下
UPDATE `system_menu` SET `parent_id` = 19267, `sort` = 1, `update_time` = NOW() WHERE `id` = 19130; -- 项目组合
UPDATE `system_menu` SET `parent_id` = 19267, `sort` = 2, `update_time` = NOW() WHERE `id` = 19135; -- 服务等级
UPDATE `system_menu` SET `parent_id` = 19267, `sort` = 3, `update_time` = NOW() WHERE `id` = 19140; -- 批量变更
UPDATE `system_menu` SET `parent_id` = 19267, `sort` = 4, `update_time` = NOW() WHERE `id` = 19157; -- 项目治理

-- 3.4 工程准备（19268）下
UPDATE `system_menu` SET `parent_id` = 19268, `sort` = 1, `update_time` = NOW() WHERE `id` = 19009; -- 现场工勘
UPDATE `system_menu` SET `parent_id` = 19268, `sort` = 2, `update_time` = NOW() WHERE `id` = 19010; -- 需求与接口
UPDATE `system_menu` SET `parent_id` = 19268, `sort` = 3, `update_time` = NOW() WHERE `id` = 19011; -- 实施方案
UPDATE `system_menu` SET `parent_id` = 19268, `sort` = 4, `update_time` = NOW() WHERE `id` = 19196; -- 工程交底

-- 3.5 工程执行（19269）下
UPDATE `system_menu` SET `parent_id` = 19269, `sort` = 1, `update_time` = NOW() WHERE `id` = 19012; -- 资源就绪
UPDATE `system_menu` SET `parent_id` = 19269, `sort` = 2, `update_time` = NOW() WHERE `id` = 19013; -- 到货签收
UPDATE `system_menu` SET `parent_id` = 19269, `sort` = 3, `update_time` = NOW() WHERE `id` = 19014; -- 硬件安装
UPDATE `system_menu` SET `parent_id` = 19269, `sort` = 4, `update_time` = NOW() WHERE `id` = 19015; -- 配置调试
UPDATE `system_menu` SET `parent_id` = 19269, `sort` = 5, `update_time` = NOW() WHERE `id` = 19016; -- 业务联调
UPDATE `system_menu` SET `parent_id` = 19269, `sort` = 6, `update_time` = NOW() WHERE `id` = 19017; -- 实施问题
UPDATE `system_menu` SET `parent_id` = 19269, `sort` = 7, `update_time` = NOW() WHERE `id` = 19018; -- 交付件归集

-- 3.6 资源采供（19270）下
UPDATE `system_menu` SET `parent_id` = 19270, `sort` = 1, `update_time` = NOW() WHERE `id` = 19171; -- 外包申请
UPDATE `system_menu` SET `parent_id` = 19270, `sort` = 2, `update_time` = NOW() WHERE `id` = 19177; -- OA领料
UPDATE `system_menu` SET `parent_id` = 19270, `sort` = 3, `update_time` = NOW() WHERE `id` = 19183; -- 外采申请
UPDATE `system_menu` SET `parent_id` = 19270, `sort` = 4, `update_time` = NOW() WHERE `id` = 19189; -- 换货协同

-- 3.7 文档表单（19271）下
UPDATE `system_menu` SET `parent_id` = 19271, `sort` = 1, `update_time` = NOW() WHERE `id` = 19250; -- 文档模板
UPDATE `system_menu` SET `parent_id` = 19271, `sort` = 2, `update_time` = NOW() WHERE `id` = 19203; -- 表单模板

-- 3.8 工程保障（19272）下
UPDATE `system_menu` SET `parent_id` = 19272, `sort` = 1, `update_time` = NOW() WHERE `id` = 19221; -- 单机风险
UPDATE `system_menu` SET `parent_id` = 19272, `sort` = 2, `update_time` = NOW() WHERE `id` = 19228; -- 技术公告
UPDATE `system_menu` SET `parent_id` = 19272, `sort` = 3, `update_time` = NOW() WHERE `id` = 19239; -- 授权管理

-- 3.9 割接交付（19263）下
UPDATE `system_menu` SET `parent_id` = 19263, `sort` = 1, `update_time` = NOW() WHERE `id` = 19019; -- 割接任务
UPDATE `system_menu` SET `parent_id` = 19263, `sort` = 2, `update_time` = NOW() WHERE `id` = 19021; -- 割接方案
UPDATE `system_menu` SET `parent_id` = 19263, `sort` = 3, `update_time` = NOW() WHERE `id` = 19022; -- 割接执行
UPDATE `system_menu` SET `parent_id` = 19263, `sort` = 4, `update_time` = NOW() WHERE `id` = 19023; -- 割接观察

-- 3.10 验收闭环（19264）下
UPDATE `system_menu` SET `parent_id` = 19264, `sort` = 1, `update_time` = NOW() WHERE `id` = 19094; -- 电子完工证明
UPDATE `system_menu` SET `parent_id` = 19264, `sort` = 2, `update_time` = NOW() WHERE `id` = 19095; -- 验收管理
UPDATE `system_menu` SET `parent_id` = 19264, `sort` = 3, `update_time` = NOW() WHERE `id` = 19096; -- 交付件检查
UPDATE `system_menu` SET `parent_id` = 19264, `sort` = 4, `update_time` = NOW() WHERE `id` = 19097; -- 项目闭环
UPDATE `system_menu` SET `parent_id` = 19264, `sort` = 5, `update_time` = NOW() WHERE `id` = 19098; -- 归档文档
UPDATE `system_menu` SET `parent_id` = 19264, `sort` = 6, `update_time` = NOW() WHERE `id` = 19099; -- 转维保

-- 3.11 巡检维保（19265）下
UPDATE `system_menu` SET `parent_id` = 19265, `sort` = 1, `update_time` = NOW() WHERE `id` = 19024; -- 巡检任务
UPDATE `system_menu` SET `parent_id` = 19265, `sort` = 2, `update_time` = NOW() WHERE `id` = 19025; -- 巡检规则
UPDATE `system_menu` SET `parent_id` = 19265, `sort` = 3, `update_time` = NOW() WHERE `id` = 19026; -- 巡检报告
UPDATE `system_menu` SET `parent_id` = 19265, `sort` = 4, `update_time` = NOW() WHERE `id` = 19027; -- 巡检问题
UPDATE `system_menu` SET `parent_id` = 19265, `sort` = 5, `update_time` = NOW() WHERE `id` = 19028; -- 维保状态

-- 3.12 客户与资产（19260）下
UPDATE `system_menu` SET `parent_id` = 19260, `sort` = 1, `update_time` = NOW() WHERE `id` = 18001; -- 客户管理
UPDATE `system_menu` SET `parent_id` = 19260, `sort` = 2, `update_time` = NOW() WHERE `id` = 18010; -- 客户联系人
UPDATE `system_menu` SET `parent_id` = 19260, `sort` = 3, `update_time` = NOW() WHERE `id` = 19001; -- 设备档案管理

-- ============================================================
-- 4. 迁移旧按钮权限（type=3）从 18000 到对应可见菜单
--    这些按钮原本直接挂在顶级 18000 下，现归入各自业务菜单
-- ============================================================

-- 4.1 项目总览（18011）相关按钮
UPDATE `system_menu` SET `parent_id` = 18011, `update_time` = NOW()
WHERE `id` IN (18009, 18021, 18024, 18025, 18041) AND `type` = 3;
-- 18009 项目主数据同步 / 18021 项目指派 / 18024 项目树管理 / 18025 项目移动 / 18041 项目全景

-- 4.2 项目团队（18013）相关按钮
UPDATE `system_menu` SET `parent_id` = 18013, `update_time` = NOW()
WHERE `id` IN (18022, 18023) AND `type` = 3;
-- 18022 项目团队管理 / 18023 团队成员维护

-- 4.3 任务WBS（18014）相关按钮
UPDATE `system_menu` SET `parent_id` = 18014, `update_time` = NOW()
WHERE `id` IN (18030, 18031, 18032, 18033) AND `type` = 3;
-- 18030 任务WBS管理 / 18031 任务创建 / 18032 任务更新 / 18033 任务删除

-- 4.4 项目阶段（18016）相关按钮（含原阶段模板按钮，因 18015 已隐藏）
UPDATE `system_menu` SET `parent_id` = 18016, `update_time` = NOW()
WHERE `id` IN (18034, 18035, 18036, 18037, 18038) AND `type` = 3;
-- 18034 阶段模板管理 / 18035 阶段模板维护 / 18036 项目阶段管理 / 18037 项目阶段维护 / 18038 阶段门禁校验

-- 4.5 项目风险（18017）相关按钮
UPDATE `system_menu` SET `parent_id` = 18017, `update_time` = NOW()
WHERE `id` IN (18039, 18040) AND `type` = 3;
-- 18039 项目风险管理 / 18040 风险维护

-- ============================================================
-- 5. 隐藏从属界面菜单（不单独建菜单，从主入口打开）
--    设置 visible=b'0'，按钮权限保留可用
-- ============================================================
UPDATE `system_menu` SET `visible` = b'0', `update_time` = NOW()
WHERE `id` IN (
  18020,  -- 项目详情（从项目总览列表行打开）
  18018,  -- 项目全景（从项目详情页打开）
  18012,  -- 项目树（从项目总览列表切换视图）
  18015,  -- 阶段模板（从项目阶段配置入口打开）
  19234,  -- 公告预检查（从技术公告列表行打开）
  19020,  -- 割接风险（从割接任务详情内打开）
  19208,  -- 表单填报（从表单模板实例化打开）
  19008,  -- 设备配置日志（从设备档案列表行打开）
  19000   -- 资产管理（已合并到客户与资产顶级）
) AND `deleted` = b'0';

-- ============================================================
-- 6. 将新增菜单分配给超级管理员角色（role_id=1）
-- ============================================================
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, m.id, 'admin', NOW(), 'admin', NOW(), b'0'
FROM `system_menu` m
WHERE m.deleted = b'0'
  AND m.id BETWEEN 19260 AND 19272
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
    WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = b'0'
  );
