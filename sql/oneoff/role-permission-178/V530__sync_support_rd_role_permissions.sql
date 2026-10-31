-- 目标环境专用 Flyway V530，仅供审查，尚未执行；不得并入通用主迁移链。
-- 来源：本地 npdms_domain_test；目标：172.17.90.178 的 npdms；均为 tenant_id=1。
-- 2026-10-31 快照：probSupporter 23 项、probRD 10 项；目标核查时均无菜单关联。
-- 仅新增缺失的有效角色菜单关系；不修改角色、菜单、用户、数据范围或历史记录。
-- 菜单 992602050001：目标现行“割接任务工作台”与本地菜单权限码相同，保留目标菜单定义。
-- 执行前停止并发权限维护，确认目标库、Flyway 历史和快照；仅使用本目录作为独立 location。
SET NAMES utf8mb4;
CREATE TEMPORARY TABLE tmp_support_rd_expected (
 role_code VARCHAR(100) NOT NULL, menu_id BIGINT NOT NULL, menu_name VARCHAR(255) NOT NULL,
 permission VARCHAR(255) NOT NULL, menu_type INT NOT NULL, parent_id BIGINT NOT NULL,
 menu_path VARCHAR(255), component VARCHAR(255), PRIMARY KEY (role_code,menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO tmp_support_rd_expected VALUES
  ('probRD', 1185, '工作流程', '', 1, 0, '/bpm', NULL),
  ('probRD', 1200, '审批中心', '', 2, 1185, 'task', NULL),
  ('probRD', 1202, '流程实例的查询', 'bpm:process-instance:query', 3, 1201, '', ''),
  ('probRD', 1207, '待办任务', '', 2, 1200, 'todo', 'bpm/task/todo/index'),
  ('probRD', 1221, '流程任务的查询', 'bpm:task:query', 3, 1207, '', ''),
  ('probRD', 198780, '业务文件查询', 'pms:file:query', 3, 1243, '', NULL),
  ('probRD', 198782, '业务文件下载', 'pms:file:download', 3, 1243, '', NULL),
  ('probRD', 198783, '业务文件预览', 'pms:file:preview', 3, 1243, '', NULL),
  ('probRD', 992602072001, '查询P5审批', 'pms:cutover-task:query-approval', 3, 992602050001, '', NULL),
  ('probRD', 992602072002, '处理P5审批', 'pms:cutover-task:approve', 3, 992602050001, '', NULL),
  ('probSupporter', 2, '基础设施', '', 1, 0, '/infra', NULL),
  ('probSupporter', 1185, '工作流程', '', 1, 0, '/bpm', NULL),
  ('probSupporter', 1200, '审批中心', '', 2, 1185, 'task', NULL),
  ('probSupporter', 1201, '我的流程', '', 2, 1200, 'my', 'bpm/processInstance/index'),
  ('probSupporter', 1202, '流程实例的查询', 'bpm:process-instance:query', 3, 1201, '', ''),
  ('probSupporter', 1207, '待办任务', '', 2, 1200, 'todo', 'bpm/task/todo/index'),
  ('probSupporter', 1221, '流程任务的查询', 'bpm:task:query', 3, 1207, '', ''),
  ('probSupporter', 1243, '文件管理', '', 2, 2, 'file', NULL),
  ('probSupporter', 18000, '项目交付', '', 1, 0, '/pms', NULL),
  ('probSupporter', 19263, '割接交付', '', 1, 18000, 'cutover', NULL),
  ('probSupporter', 198780, '业务文件查询', 'pms:file:query', 3, 1243, '', NULL),
  ('probSupporter', 198782, '业务文件下载', 'pms:file:download', 3, 1243, '', NULL),
  ('probSupporter', 198783, '业务文件预览', 'pms:file:preview', 3, 1243, '', NULL),
  ('probSupporter', 970445001, '割接工作台', 'pms:cutover-task:query', 2, 19263, 'workbench', 'pms/cutover/workbench/index'),
  ('probSupporter', 970462001, '查询割接闭环', 'pms:cutover-task:query-closure', 3, 970445001, '', NULL),
  ('probSupporter', 970484000, '巡检服务', '', 1, 18000, 'inspection', NULL),
  ('probSupporter', 970484001, '巡检工作台', '', 2, 970484000, 'workbench', 'pms/inspection/workbench/index'),
  ('probSupporter', 970484002, '巡检任务', 'pms:inspection-task:query', 2, 970484000, 'tasks', 'pms/inspection/tasks/index'),
  ('probSupporter', 970484010, '巡检任务查询', 'pms:inspection-task:query', 3, 970484002, '', NULL),
  ('probSupporter', 992602050001, '割接任务工作台', 'pms:cutover-task:query', 2, 19263, 'cutover-task', 'pms/cutover/cutover-task/index'),
  ('probSupporter', 992602050002, '割接任务查询', 'pms:cutover-task:query', 3, 992602050001, '', NULL),
  ('probSupporter', 992602072001, '查询P5审批', 'pms:cutover-task:query-approval', 3, 992602050001, '', NULL),
  ('probSupporter', 992602072002, '处理P5审批', 'pms:cutover-task:approve', 3, 992602050001, '', NULL);
-- MySQL 8.4 强制 CHECK；前置条件不满足时，在权限写入前报错。
CREATE TEMPORARY TABLE tmp_support_rd_guard (ok TINYINT NOT NULL CHECK (ok=1)) ENGINE=InnoDB;
SELECT id,code,name FROM system_role WHERE tenant_id=1 AND deleted=0 AND code IN ('probSupporter','probRD') FOR UPDATE;
INSERT INTO tmp_support_rd_guard (ok)
SELECT IF(DATABASE()='npdms'
 AND (SELECT COUNT(*) FROM system_role WHERE tenant_id=1 AND deleted=0 AND code IN ('probSupporter','probRD'))=2
 AND (SELECT COUNT(*) FROM system_role WHERE tenant_id=1 AND deleted=0 AND status=0
      AND ((code='probSupporter' AND id=2112914672936136727 AND name='技术支持人员')
        OR (code='probRD' AND id=2112914672936136728 AND name='研发人员')))=2
 AND (SELECT COUNT(*) FROM tmp_support_rd_expected e JOIN system_menu m ON m.id=e.menu_id
      WHERE m.deleted=0 AND m.status=0 AND BINARY m.name=BINARY e.menu_name
      AND BINARY COALESCE(m.permission,'')=BINARY COALESCE(e.permission,'')
      AND m.type=e.menu_type AND m.parent_id=e.parent_id
      AND BINARY COALESCE(m.path,'')=BINARY COALESCE(e.menu_path,'')
      AND BINARY COALESCE(m.component,'')=BINARY COALESCE(e.component,''))=33
,1,0);
INSERT INTO tmp_support_rd_guard (ok)
SELECT IF(NOT EXISTS (SELECT 1 FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
      JOIN tmp_support_rd_expected e ON e.role_code=r.code AND e.menu_id=rm.menu_id
      WHERE r.tenant_id=1 AND r.deleted=0 AND rm.deleted=0 GROUP BY rm.role_id,rm.menu_id HAVING COUNT(*)>1),1,0);
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,tenant_id,deleted)
SELECT r.id,e.menu_id,'role_permission_migration_20261031','role_permission_migration_20261031',1,b'0'
FROM tmp_support_rd_expected e JOIN system_role r ON r.code=e.role_code AND r.tenant_id=1 AND r.deleted=0 AND r.status=0
WHERE NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=e.menu_id AND rm.tenant_id=1 AND rm.deleted=0);
SELECT ROW_COUNT() AS inserted_relations;
SELECT r.code,COUNT(*) AS matched_active_relations FROM tmp_support_rd_expected e
 JOIN system_role r ON r.code=e.role_code AND r.tenant_id=1 AND r.deleted=0
 JOIN system_role_menu rm ON rm.role_id=r.id AND rm.menu_id=e.menu_id AND rm.tenant_id=1 AND rm.deleted=0
 GROUP BY r.code ORDER BY r.code;
-- 预期 probRD=10、probSupporter=23；失败时 Flyway 报错。
INSERT INTO tmp_support_rd_guard (ok)
SELECT IF((SELECT COUNT(*) FROM tmp_support_rd_expected e JOIN system_role r ON r.code=e.role_code AND r.tenant_id=1 AND r.deleted=0
 JOIN system_role_menu rm ON rm.role_id=r.id AND rm.menu_id=e.menu_id AND rm.tenant_id=1 AND rm.deleted=0)=33,1,0);
DROP TEMPORARY TABLE tmp_support_rd_guard;
DROP TEMPORARY TABLE tmp_support_rd_expected;
