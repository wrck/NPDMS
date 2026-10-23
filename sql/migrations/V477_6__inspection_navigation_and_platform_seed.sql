-- 巡检基础入口与平台配置。既有规则菜单沿用ID/角色授权迁移到新路由。
-- 旧六表入口须在新业务验收后另行退出；本迁移不删除旧数据、不新增角色/人员授权。
DROP PROCEDURE IF EXISTS inspection_special_seed;
DELIMITER $$
CREATE PROCEDURE inspection_special_seed()
BEGIN
  CREATE TEMPORARY TABLE inspection_expected_menu (
    id bigint PRIMARY KEY, name varchar(50), permission varchar(100), type tinyint, sort int, parent_id bigint,
    path varchar(200), icon varchar(100), component varchar(255), component_name varchar(255)
  );
  INSERT INTO inspection_expected_menu VALUES
  (970484000,'巡检服务','',1,5,18000,'inspection','ep:monitor',NULL,'PmsInspection'),
  (970484001,'巡检工作台','',2,1,970484000,'workbench','ep:monitor','pms/inspection/workbench/index','PmsInspectionWorkbench'),
  (970484002,'巡检任务','pms:inspection-task:query',2,2,970484000,'tasks','ep:list','pms/inspection/tasks/index','PmsInspectionTaskList'),
  (970484010,'巡检任务查询','pms:inspection-task:query',3,10,970484002,'','',NULL,NULL),
  (970484011,'手工创建巡检任务','pms:inspection-task:create',3,20,970484002,'','',NULL,NULL),
  (970484012,'巡检准备','pms:inspection-task:prepare',3,30,970484002,'','',NULL,NULL),
  (970484013,'巡检执行','pms:inspection-task:execute',3,40,970484002,'','',NULL,NULL),
  (970484014,'分配巡检负责人','pms:inspection-task:assign',3,50,970484002,'','',NULL,NULL),
  (970484015,'取消巡检任务','pms:inspection-task:cancel',3,60,970484002,'','',NULL,NULL),
  (970484016,'生成与上传巡检报告','pms:inspection-report:generate',3,70,970484002,'','',NULL,NULL),
  (970484017,'巡检问题标注','pms:inspection-task:annotate',3,80,970484002,'','',NULL,NULL),
  (970484018,'申请巡检归档','pms:inspection-task:request-archive',3,90,970484002,'','',NULL,NULL),
  (970484019,'确认巡检归档','pms:inspection-task:archive',3,100,970484002,'','',NULL,NULL),
  (970484020,'巡检待办查询','pms:service-issue:query',3,110,970484002,'','',NULL,NULL),
  (970484021,'巡检待办处理','pms:service-issue:remediate',3,120,970484002,'','',NULL,NULL),
  (970484022,'巡检待办确认闭环','pms:service-issue:confirm',3,130,970484002,'','',NULL,NULL),
  (970484023,'误报反馈查询','pms:inspection-feedback:query',3,140,199510,'','',NULL,NULL),
  (970484024,'误报反馈分析','pms:inspection-feedback:analyze',3,150,199510,'','',NULL,NULL),
  (970484025,'误报反馈明细','pms:inspection-feedback:details',3,160,199510,'','',NULL,NULL),
  (970484026,'确认误报反馈','pms:inspection-feedback:confirm',3,170,199510,'','',NULL,NULL),
  (970484027,'导出误报反馈','pms:inspection-feedback:export',3,180,199510,'','',NULL,NULL);
  IF EXISTS (SELECT 1 FROM inspection_expected_menu e JOIN system_menu a ON a.id=e.id
    WHERE a.deleted<>b'0' OR a.type<>e.type OR a.parent_id<>e.parent_id OR NOT(a.permission<=>e.permission)
      OR NOT(a.path<=>e.path) OR NOT(a.component<=>e.component)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_MENU_IDENTITY_CONFLICT';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM system_menu WHERE id=199510) OR EXISTS (SELECT 1 FROM system_menu WHERE id=199510 AND (deleted<>b'0' OR permission<>'pms:inspection-rule:query'
    OR component NOT IN ('pms/service/inspection-rule/index','pms/inspection/rules/index'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_RULE_MENU_IDENTITY_CONFLICT';
  END IF;
  INSERT INTO system_menu
    (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
  SELECT e.id,e.name,e.permission,e.type,e.sort,e.parent_id,e.path,e.icon,e.component,e.component_name,0,b'1',b'0',b'1',
    'inspection-special-20261015','inspection-special-20261015',b'0'
  FROM inspection_expected_menu e WHERE NOT EXISTS (SELECT 1 FROM system_menu a WHERE a.id=e.id);
  UPDATE system_menu SET name='巡检规则',parent_id=970484000,path='rules',component='pms/inspection/rules/index',
    component_name='PmsInspectionRules',sort=3,keep_alive=b'0',updater='inspection-special-20261015',update_time=NOW()
  WHERE id=199510 AND deleted=b'0' AND permission='pms:inspection-rule:query';
  DROP TEMPORARY TABLE inspection_expected_menu;
END$$
DELIMITER ;
CALL inspection_special_seed();
DROP PROCEDURE inspection_special_seed;

-- 同一规则功能移动后的目录导航，不扩展任何业务权限或项目范围。
INSERT INTO system_role_menu (role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT DISTINCT rm.role_id,970484000,'inspection-special-20261015','inspection-special-20261015',b'0',rm.tenant_id
FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0'
WHERE rm.deleted=b'0' AND (m.id=199510 OR m.parent_id=199510)
  AND NOT EXISTS (SELECT 1 FROM system_role_menu p WHERE p.role_id=rm.role_id AND p.menu_id=970484000 AND p.tenant_id=rm.tenant_id AND p.deleted=b'0');

INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater,deleted)
SELECT 970484100,100,'巡检报告','INSPECTION_REPORT','pms_file_category',0,'info','','巡检专项受控文件类别','inspection-special-20261015','inspection-special-20261015',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_file_category' AND value='INSPECTION_REPORT' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater,deleted)
SELECT 970484101,110,'巡检脱敏日志','INSPECTION_LOG','pms_file_category',0,'info','','巡检专项受控文件类别','inspection-special-20261015','inspection-special-20261015',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_file_category' AND value='INSPECTION_LOG' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater,deleted)
SELECT 970484102,120,'巡检处理证据','INSPECTION_EVIDENCE','pms_file_category',0,'info','','巡检专项受控文件类别','inspection-special-20261015','inspection-special-20261015',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_file_category' AND value='INSPECTION_EVIDENCE' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater,deleted)
SELECT 970484103,130,'巡检业务附件','INSPECTION_ATTACHMENT','pms_file_category',0,'info','','巡检专项受控文件类别','inspection-special-20261015','inspection-special-20261015',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_file_category' AND value='INSPECTION_ATTACHMENT' AND deleted=b'0');
INSERT INTO system_dict_data (id,sort,label,value,dict_type,status,color_type,css_class,remark,creator,updater,deleted)
SELECT 970484104,140,'巡检误报反馈导出','INSPECTION_FEEDBACK_EXPORT','pms_file_category',0,'info','','巡检专项受控文件类别','inspection-special-20261015','inspection-special-20261015',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='pms_file_category' AND value='INSPECTION_FEEDBACK_EXPORT' AND deleted=b'0');

INSERT INTO infra_config (id,category,type,name,config_key,value,visible,remark,creator,updater,deleted)
SELECT 970484200,'pms-inspection',1,'巡检独立预检有效期（秒）','pms.inspection.precheck-valid-seconds','900',b'1',
  '默认15分钟；每次预检冻结当时配置，以实际完成时间计算有效期；连接或认证上下文变化重新预检',
  'inspection-special-20261015','inspection-special-20261015',b'0'
WHERE NOT EXISTS (SELECT 1 FROM infra_config WHERE config_key='pms.inspection.precheck-valid-seconds' AND deleted=b'0');

-- 文件授权及用途限制由现有FileBusinessObjectPolicyProvider返回；不新增无消费者的策略配置。

INSERT INTO system_notify_template (name,code,nickname,content,type,params,status,remark,creator,updater,deleted)
SELECT '巡检跟踪事项分配','pms_inspection_issue_assigned','项目交付平台',
  '巡检任务 {taskCode} 的跟踪事项 {issueId} 已分配给你，计划期限 {dueAt}，办理入口 {url}',2,
  '["taskCode","issueId","dueAt","url"]',0,'巡检确认跟踪后发送；通知送达不代表事项处理完成',
  'inspection-special-20261015','inspection-special-20261015',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='pms_inspection_issue_assigned' AND deleted=b'0');
