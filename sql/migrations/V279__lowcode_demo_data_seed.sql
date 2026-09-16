-- =====================================================================
-- V279__lowcode_demo_data_seed.sql
-- 用途：为低代码平台各功能模块补齐演示数据，确保每个功能模块 ≥10 条
--
-- 数据范围：覆盖所有当前演示数据不足的 lowcode 表（共 28 张），
--   包括核心配置类、空白配置类、流程协作类、运行时日志类。
--
-- 设计原则：
--   1) 数据真实可信——审批人姓名、合同金额、公司名等均符合业务逻辑
--   2) 覆盖多种业务场景——设备巡检/合同/客户/工单/知识库/供应商/维保等
--   3) 符合表结构约束——字段类型/NOT NULL/唯一键/JSON 格式均对齐 V27-V58 定义
--   4) 幂等可重复执行——有 code 唯一键的表用 INSERT IGNORE；
--      无唯一键的日志表先按标记 (create_by/actor/operator='demo-v59') 清理再插入
--   5) JSON 字段统一使用 MySQL 原生 JSON_OBJECT/JSON_ARRAY 构造
--   6) 时间字段使用 NOW() - INTERVAL N 构造合理的时间分布
--
-- 依赖：V27-V58 已建表并完成 V58 员工入职演示数据初始化
-- =====================================================================

-- ---------------------------------------------------------------------
-- 第一部分：核心配置类表（补齐 entity/relation/form/list/microflow/
--           rule/trigger/connector 各 ≥10 条）
-- ---------------------------------------------------------------------

-- 1.1 实体定义（在 V58 现有 3 个基础上补充 8 个新实体，合计 ≥10）
INSERT IGNORE INTO `low_entity`
    (`code`, `name`, `table_name`, `description`, `biz_type`, `status`, `version`, `create_time`, `update_time`, `create_by`, `deleted`)
VALUES
('demo_device',            '设备',     'demo_device',            '演示-设备台账实体（设备资产管理）',     'DEVICE',      'DRAFT', 1, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY, 'demo', 0),
('demo_customer',          '客户',     'demo_customer',          '演示-客户实体（CRM 客户主数据）',       'CRM',         'DRAFT', 1, NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 28 DAY, 'demo', 0),
('demo_supplier',          '供应商',   'demo_supplier',          '演示-供应商实体（采购供应商主数据）',   'SUPPLIER',    'DRAFT', 1, NOW() - INTERVAL 27 DAY, NOW() - INTERVAL 27 DAY, 'demo', 0),
('demo_contract',          '合同',     'demo_contract',          '演示-合同实体（含金额/签约方/到期日）', 'CONTRACT',    'DRAFT', 1, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY, 'demo', 0),
('demo_work_order',        '工单',     'demo_work_order',        '演示-工单实体（售后维修工单）',         'WORK_ORDER',  'DRAFT', 1, NOW() - INTERVAL 24 DAY, NOW() - INTERVAL 24 DAY, 'demo', 0),
('demo_inspection_record', '巡检记录', 'demo_inspection_record', '演示-设备巡检记录实体',                 'INSPECTION',  'DRAFT', 1, NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 22 DAY, 'demo', 0),
('demo_maintenance_plan',  '维保计划', 'demo_maintenance_plan',  '演示-设备维保计划实体',                 'MAINTENANCE', 'DRAFT', 1, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY, 'demo', 0),
('demo_kb_article',        '知识库',   'demo_kb_article',        '演示-知识库文章实体（含分类/标签/正文）','KB',          'DRAFT', 1, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY, 'demo', 0);

-- 实体 ID 变量（含 V58 已有实体 + V59 新增实体，供关联/版本/绑定引用）
SET @deptEntityId      = (SELECT id FROM low_entity WHERE code = 'demo_department'      ORDER BY id DESC LIMIT 1);
SET @empEntityId       = (SELECT id FROM low_entity WHERE code = 'demo_employee'        ORDER BY id DESC LIMIT 1);
SET @taskEntityId      = (SELECT id FROM low_entity WHERE code = 'demo_onboarding_task' ORDER BY id DESC LIMIT 1);
SET @deviceEntityId    = (SELECT id FROM low_entity WHERE code = 'demo_device'          ORDER BY id DESC LIMIT 1);
SET @customerEntityId  = (SELECT id FROM low_entity WHERE code = 'demo_customer'        ORDER BY id DESC LIMIT 1);
SET @supplierEntityId  = (SELECT id FROM low_entity WHERE code = 'demo_supplier'        ORDER BY id DESC LIMIT 1);
SET @contractEntityId  = (SELECT id FROM low_entity WHERE code = 'demo_contract'        ORDER BY id DESC LIMIT 1);
SET @workOrderEntityId = (SELECT id FROM low_entity WHERE code = 'demo_work_order'      ORDER BY id DESC LIMIT 1);
SET @inspectionEntityId= (SELECT id FROM low_entity WHERE code = 'demo_inspection_record' ORDER BY id DESC LIMIT 1);
SET @maintPlanEntityId = (SELECT id FROM low_entity WHERE code = 'demo_maintenance_plan' ORDER BY id DESC LIMIT 1);
SET @kbEntityId        = (SELECT id FROM low_entity WHERE code = 'demo_kb_article'      ORDER BY id DESC LIMIT 1);

-- 1.2 实体关联关系（在 V58 现有 3 个基础上补充 8 个，合计 11 个 ≥10）
INSERT IGNORE INTO `low_relation`
    (`from_entity_id`, `to_entity_id`, `relation_type`, `from_field_name`, `to_field_name`, `reverse_name`, `junction_table`, `on_delete`, `on_update`, `create_time`, `update_time`, `create_by`, `deleted`)
VALUES
(@contractEntityId,   @customerEntityId,  'MANY_TO_ONE', 'customer_id',       NULL, 'contracts',        NULL, 'RESTRICT', 'CASCADE', NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY, 'demo', 0),
(@workOrderEntityId,  @deviceEntityId,    'MANY_TO_ONE', 'device_id',         NULL, 'workOrders',       NULL, 'RESTRICT', 'CASCADE', NOW() - INTERVAL 24 DAY, NOW() - INTERVAL 24 DAY, 'demo', 0),
(@inspectionEntityId, @deviceEntityId,    'MANY_TO_ONE', 'device_id',         NULL, 'inspections',      NULL, 'RESTRICT', 'CASCADE', NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 22 DAY, 'demo', 0),
(@maintPlanEntityId,  @deviceEntityId,    'MANY_TO_ONE', 'device_id',         NULL, 'maintenancePlans', NULL, 'CASCADE',  'CASCADE', NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY, 'demo', 0),
(@deviceEntityId,     @supplierEntityId,  'MANY_TO_ONE', 'supplier_id',       NULL, 'devices',          NULL, 'SET_NULL', 'CASCADE', NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY, 'demo', 0),
(@deviceEntityId,     @customerEntityId,  'MANY_TO_ONE', 'owner_customer_id', NULL, 'ownedDevices',     NULL, 'SET_NULL', 'CASCADE', NOW() - INTERVAL 29 DAY, NOW() - INTERVAL 29 DAY, 'demo', 0),
(@workOrderEntityId,  @contractEntityId,  'MANY_TO_ONE', 'contract_id',       NULL, 'workOrders',       NULL, 'SET_NULL', 'CASCADE', NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY, 'demo', 0),
(@kbEntityId,         @empEntityId,       'MANY_TO_ONE', 'author_emp_id',     NULL, 'articles',         NULL, 'SET_NULL', 'CASCADE', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY, 'demo', 0),
(@customerEntityId,   @customerEntityId,  'MANY_TO_ONE', 'referrer_id',       NULL, 'referrals',        NULL, 'SET_NULL', 'CASCADE', NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 28 DAY, 'demo', 0),
(@inspectionEntityId, @empEntityId,       'MANY_TO_ONE', 'inspector_emp_id',  NULL, 'inspections',      NULL, 'SET_NULL', 'CASCADE', NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 21 DAY, 'demo', 0);

-- 1.3 表单配置（在 V58 现有 1 个基础上补充 10 个，合计 11 个 ≥10）
--     每个表单 form_config 含 title/fields(≥5)/layout，events 绑定微流/流程
INSERT IGNORE INTO `low_form`
    (`code`, `name`, `description`, `form_config`, `events`, `version`, `status`, `biz_type`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
VALUES
('form_demo_device',
 '设备档案表单',
 '演示-设备台账录入表单（含资产编号/型号/供应商选择）',
 JSON_OBJECT(
   'title', '设备档案', 'labelWidth', 110, 'labelPosition', 'right', 'size', 'default',
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_dev_code','type','input','label','设备编号','prop','device_code','required',true,'span',12,'props',JSON_OBJECT('maxlength',64)),
     JSON_OBJECT('id','f_dev_name','type','input','label','设备名称','prop','device_name','required',true,'span',12),
     JSON_OBJECT('id','f_model','type','input','label','型号','prop','model','required',false,'span',12),
     JSON_OBJECT('id','f_supplier','type','select','label','供应商','prop','supplier_id','required',true,'span',12,'props',JSON_OBJECT('filterable',true)),
     JSON_OBJECT('id','f_customer','type','select','label','归属客户','prop','owner_customer_id','required',false,'span',12),
     JSON_OBJECT('id','f_purchase_date','type','date','label','采购日期','prop','purchase_date','required',true,'span',12),
     JSON_OBJECT('id','f_status','type','select','label','状态','prop','status','required',true,'span',12,'defaultValue','IDLE','props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','闲置','value','IDLE'),JSON_OBJECT('label','在用','value','IN_USE'),JSON_OBJECT('label','维修','value','REPAIR'),JSON_OBJECT('label','报废','value','SCRAPPED'))))
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 JSON_OBJECT('onChange', JSON_OBJECT('type','MICROFLOW','code','microflow_demo_device_status_change')),
 1, 'DRAFT', 'DEVICE', 'demo', NOW() - INTERVAL 30 DAY, 'demo', NOW() - INTERVAL 30 DAY, 0),

('form_demo_customer',
 '客户档案表单',
 '演示-CRM 客户录入表单（含联系人/推荐人/行业）',
 JSON_OBJECT(
   'title', '客户档案', 'labelWidth', 110,
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_cust_code','type','input','label','客户编号','prop','customer_code','required',true,'span',12),
     JSON_OBJECT('id','f_cust_name','type','input','label','客户名称','prop','customer_name','required',true,'span',12),
     JSON_OBJECT('id','f_industry','type','select','label','行业','prop','industry','required',false,'span',12,'props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','制造业','value','MFG'),JSON_OBJECT('label','互联网','value','IT'),JSON_OBJECT('label','金融','value','FIN'),JSON_OBJECT('label','教育','value','EDU')))),
     JSON_OBJECT('id','f_contact','type','input','label','联系人','prop','contact_person','required',true,'span',12),
     JSON_OBJECT('id','f_phone','type','input','label','联系电话','prop','contact_phone','required',true,'span',12,'rules',JSON_ARRAY(JSON_OBJECT('pattern','^1[3-9]\\d{9}$','message','手机号格式不正确','trigger','blur'))),
     JSON_OBJECT('id','f_email','type','input','label','邮箱','prop','contact_email','required',false,'span',12),
     JSON_OBJECT('id','f_referrer','type','select','label','推荐人','prop','referrer_id','required',false,'span',12),
     JSON_OBJECT('id','f_level','type','select','label','客户等级','prop','level','required',true,'span',12,'defaultValue','B','props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','A级','value','A'),JSON_OBJECT('label','B级','value','B'),JSON_OBJECT('label','C级','value','C'))))
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 NULL,
 1, 'DRAFT', 'CRM', 'demo', NOW() - INTERVAL 28 DAY, 'demo', NOW() - INTERVAL 28 DAY, 0),

('form_demo_supplier',
 '供应商档案表单',
 '演示-供应商录入表单（含资质/评级）',
 JSON_OBJECT(
   'title', '供应商档案', 'labelWidth', 110,
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_sup_code','type','input','label','供应商编号','prop','supplier_code','required',true,'span',12),
     JSON_OBJECT('id','f_sup_name','type','input','label','供应商名称','prop','supplier_name','required',true,'span',12),
     JSON_OBJECT('id','f_contact','type','input','label','联系人','prop','contact_person','required',true,'span',12),
     JSON_OBJECT('id','f_phone','type','input','label','联系电话','prop','contact_phone','required',true,'span',12),
     JSON_OBJECT('id','f_bank_account','type','input','label','银行账号','prop','bank_account','required',false,'span',12),
     JSON_OBJECT('id','f_rating','type','select','label','评级','prop','rating','required',true,'span',12,'defaultValue','B','props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','优质','value','A'),JSON_OBJECT('label','合格','value','B'),JSON_OBJECT('label','待改进','value','C')))),
     JSON_OBJECT('id','f_qualification','type','textarea','label','资质说明','prop','qualification','required',false,'span',24,'props',JSON_OBJECT('rows',3))
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 NULL,
 1, 'DRAFT', 'SUPPLIER', 'demo', NOW() - INTERVAL 27 DAY, 'demo', NOW() - INTERVAL 27 DAY, 0),

('form_demo_contract',
 '合同录入表单',
 '演示-合同录入表单（含金额/客户/到期日/审批流程）',
 JSON_OBJECT(
   'title', '合同录入', 'labelWidth', 120,
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_contract_no','type','input','label','合同编号','prop','contract_no','required',true,'span',12),
     JSON_OBJECT('id','f_title','type','input','label','合同名称','prop','title','required',true,'span',12),
     JSON_OBJECT('id','f_customer','type','select','label','客户','prop','customer_id','required',true,'span',12,'props',JSON_OBJECT('filterable',true)),
     JSON_OBJECT('id','f_amount','type','number','label','合同金额(元)','prop','amount','required',true,'span',12,'props',JSON_OBJECT('min',0,'precision',2)),
     JSON_OBJECT('id','f_sign_date','type','date','label','签约日期','prop','sign_date','required',true,'span',12),
     JSON_OBJECT('id','f_expire_date','type','date','label','到期日期','prop','expire_date','required',true,'span',12),
     JSON_OBJECT('id','f_type','type','select','label','合同类型','prop','type','required',true,'span',12,'props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','销售合同','value','SALE'),JSON_OBJECT('label','采购合同','value','PURCHASE'),JSON_OBJECT('label','服务合同','value','SERVICE')))),
     JSON_OBJECT('id','f_remark','type','textarea','label','备注','prop','remark','required',false,'span',24,'props',JSON_OBJECT('rows',3))
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 JSON_OBJECT('onSubmit', JSON_OBJECT('type','PROCESS','code','demo_contract_approval')),
 1, 'DRAFT', 'CONTRACT', 'demo', NOW() - INTERVAL 25 DAY, 'demo', NOW() - INTERVAL 25 DAY, 0),

('form_demo_work_order',
 '工单录入表单',
 '演示-售后维修工单表单（含设备/优先级/故障描述）',
 JSON_OBJECT(
   'title', '工单录入', 'labelWidth', 110,
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_wo_no','type','input','label','工单编号','prop','work_order_no','required',true,'span',12),
     JSON_OBJECT('id','f_device','type','select','label','设备','prop','device_id','required',true,'span',12,'props',JSON_OBJECT('filterable',true)),
     JSON_OBJECT('id','f_contract','type','select','label','关联合同','prop','contract_id','required',false,'span',12),
     JSON_OBJECT('id','f_priority','type','select','label','优先级','prop','priority','required',true,'span',12,'defaultValue','MEDIUM','props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','紧急','value','URGENT'),JSON_OBJECT('label','高','value','HIGH'),JSON_OBJECT('label','中','value','MEDIUM'),JSON_OBJECT('label','低','value','LOW')))),
     JSON_OBJECT('id','f_fault_type','type','select','label','故障类型','prop','fault_type','required',true,'span',12,'props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','硬件故障','value','HARDWARE'),JSON_OBJECT('label','软件故障','value','SOFTWARE'),JSON_OBJECT('label','网络故障','value','NETWORK')))),
     JSON_OBJECT('id','f_description','type','textarea','label','故障描述','prop','description','required',true,'span',24,'props',JSON_OBJECT('rows',4)),
     JSON_OBJECT('id','f_assignee','type','input','label','指派工程师','prop','assignee','required',false,'span',12)
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 JSON_OBJECT('onChange', JSON_OBJECT('type','MICROFLOW','code','microflow_demo_work_order_assign')),
 1, 'DRAFT', 'WORK_ORDER', 'demo', NOW() - INTERVAL 24 DAY, 'demo', NOW() - INTERVAL 24 DAY, 0),

('form_demo_inspection',
 '巡检记录表单',
 '演示-设备巡检记录表单（含巡检项/结果/异常上报）',
 JSON_OBJECT(
   'title', '巡检记录', 'labelWidth', 110,
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_ins_no','type','input','label','巡检编号','prop','inspection_no','required',true,'span',12),
     JSON_OBJECT('id','f_device','type','select','label','设备','prop','device_id','required',true,'span',12,'props',JSON_OBJECT('filterable',true)),
     JSON_OBJECT('id','f_inspector','type','select','label','巡检人','prop','inspector_emp_id','required',true,'span',12),
     JSON_OBJECT('id','f_inspect_date','type','date','label','巡检日期','prop','inspect_date','required',true,'span',12),
     JSON_OBJECT('id','f_result','type','select','label','巡检结果','prop','result','required',true,'span',12,'props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','正常','value','NORMAL'),JSON_OBJECT('label','异常','value','ABNORMAL'),JSON_OBJECT('label','需维修','value','NEED_REPAIR')))),
     JSON_OBJECT('id','f_temperature','type','number','label','温度(℃)','prop','temperature','required',false,'span',12,'props',JSON_OBJECT('precision',1)),
     JSON_OBJECT('id','f_remark','type','textarea','label','异常说明','prop','remark','required',false,'span',24,'props',JSON_OBJECT('rows',3))
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 NULL,
 1, 'DRAFT', 'INSPECTION', 'demo', NOW() - INTERVAL 22 DAY, 'demo', NOW() - INTERVAL 22 DAY, 0),

('form_demo_maintenance_plan',
 '维保计划表单',
 '演示-设备维保计划表单（含周期/下次维保日/负责人）',
 JSON_OBJECT(
   'title', '维保计划', 'labelWidth', 110,
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_plan_no','type','input','label','计划编号','prop','plan_no','required',true,'span',12),
     JSON_OBJECT('id','f_device','type','select','label','设备','prop','device_id','required',true,'span',12,'props',JSON_OBJECT('filterable',true)),
     JSON_OBJECT('id','f_cycle','type','select','label','维保周期','prop','cycle','required',true,'span',12,'props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','月度','value','MONTHLY'),JSON_OBJECT('label','季度','value','QUARTERLY'),JSON_OBJECT('label','半年度','value','HALF_YEAR'),JSON_OBJECT('label','年度','value','YEARLY')))),
     JSON_OBJECT('id','f_next_date','type','date','label','下次维保日','prop','next_maintenance_date','required',true,'span',12),
     JSON_OBJECT('id','f_owner','type','input','label','负责人','prop','owner','required',true,'span',12),
     JSON_OBJECT('id','f_enabled','type','select','label','启用状态','prop','enabled','required',true,'span',12,'defaultValue',1,'props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','启用','value',1),JSON_OBJECT('label','停用','value',0)))),
     JSON_OBJECT('id','f_content','type','textarea','label','维保内容','prop','content','required',false,'span',24,'props',JSON_OBJECT('rows',3))
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 NULL,
 1, 'DRAFT', 'MAINTENANCE', 'demo', NOW() - INTERVAL 20 DAY, 'demo', NOW() - INTERVAL 20 DAY, 0),

('form_demo_kb_article',
 '知识库文章表单',
 '演示-知识库文章发布表单（含分类/标签/正文/富文本）',
 JSON_OBJECT(
   'title', '知识库文章', 'labelWidth', 100,
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_title','type','input','label','标题','prop','title','required',true,'span',24),
     JSON_OBJECT('id','f_category','type','select','label','分类','prop','category','required',true,'span',12,'props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','操作手册','value','MANUAL'),JSON_OBJECT('label','故障案例','value','CASE'),JSON_OBJECT('label','FAQ','value','FAQ'),JSON_OBJECT('label','规范','value','SPEC')))),
     JSON_OBJECT('id','f_tags','type','input','label','标签','prop','tags','required',false,'span',12,'placeholder','多个标签逗号分隔'),
     JSON_OBJECT('id','f_author','type','select','label','作者','prop','author_emp_id','required',true,'span',12),
     JSON_OBJECT('id','f_status','type','select','label','状态','prop','status','required',true,'span',12,'defaultValue','DRAFT','props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','草稿','value','DRAFT'),JSON_OBJECT('label','已发布','value','PUBLISHED'),JSON_OBJECT('label','已归档','value','ARCHIVED')))),
     JSON_OBJECT('id','f_content','type','textarea','label','正文','prop','content','required',true,'span',24,'props',JSON_OBJECT('rows',10)),
     JSON_OBJECT('id','f_view_count','type','number','label','阅读量','prop','view_count','required',false,'span',12,'disabled',true)
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 JSON_OBJECT('onSubmit', JSON_OBJECT('type','MICROFLOW','code','microflow_demo_kb_publish_notify')),
 1, 'DRAFT', 'KB', 'demo', NOW() - INTERVAL 18 DAY, 'demo', NOW() - INTERVAL 18 DAY, 0),

('form_demo_department',
 '部门档案表单',
 '演示-部门档案表单（含上级部门/负责人）',
 JSON_OBJECT(
   'title', '部门档案', 'labelWidth', 100,
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_dept_code','type','input','label','部门编码','prop','dept_code','required',true,'span',12),
     JSON_OBJECT('id','f_dept_name','type','input','label','部门名称','prop','dept_name','required',true,'span',12),
     JSON_OBJECT('id','f_parent','type','select','label','上级部门','prop','parent_id','required',false,'span',12),
     JSON_OBJECT('id','f_manager','type','input','label','负责人','prop','manager_id','required',false,'span',12),
     JSON_OBJECT('id','f_desc','type','textarea','label','描述','prop','description','required',false,'span',24,'props',JSON_OBJECT('rows',2))
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 NULL,
 1, 'DRAFT', 'EMP_ONBOARDING', 'demo', NOW() - INTERVAL 35 DAY, 'demo', NOW() - INTERVAL 35 DAY, 0),

('form_demo_onboarding_task',
 '入职任务表单',
 '演示-入职任务录入表单（含任务类型/负责人/截止日）',
 JSON_OBJECT(
   'title', '入职任务', 'labelWidth', 100,
   'fields', JSON_ARRAY(
     JSON_OBJECT('id','f_task_name','type','input','label','任务名称','prop','task_name','required',true,'span',12),
     JSON_OBJECT('id','f_task_type','type','select','label','任务类型','prop','task_type','required',true,'span',12,'props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','设备配备','value','EQUIPMENT'),JSON_OBJECT('label','培训','value','TRAINING'),JSON_OBJECT('label','入职引导','value','ORIENTATION')))),
     JSON_OBJECT('id','f_assignee','type','input','label','负责人','prop','assignee','required',true,'span',12),
     JSON_OBJECT('id','f_due_date','type','date','label','截止日期','prop','due_date','required',true,'span',12),
     JSON_OBJECT('id','f_status','type','select','label','状态','prop','status','required',true,'span',12,'defaultValue','PENDING','props',JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('label','待处理','value','PENDING'),JSON_OBJECT('label','进行中','value','IN_PROGRESS'),JSON_OBJECT('label','已完成','value','DONE')))),
     JSON_OBJECT('id','f_remark','type','textarea','label','备注','prop','remark','required',false,'span',24,'props',JSON_OBJECT('rows',2))
   ),
   'layout', JSON_OBJECT('type','grid','gutter',16)
 ),
 NULL,
 1, 'DRAFT', 'EMP_ONBOARDING', 'demo', NOW() - INTERVAL 35 DAY, 'demo', NOW() - INTERVAL 35 DAY, 0);

-- 1.4 列表配置（在 V58 现有 1 个基础上补充 10 个，合计 11 个 ≥10）
--     每个列表 list_config 含 columns(≥5)/filters/pagination/searchApi
INSERT IGNORE INTO `low_list`
    (`code`, `name`, `description`, `list_config`, `version`, `status`, `biz_type`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
VALUES
('list_demo_device',
 '设备列表',
 '演示-设备台账列表（含搜索/状态筛选/分页）',
 JSON_OBJECT(
   'title','设备列表','entityCode','demo_device',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','device_code','label','设备编号','width',140,'sortable',true,'breakpoint','xs'),
     JSON_OBJECT('prop','device_name','label','设备名称','width',160,'breakpoint','xs'),
     JSON_OBJECT('prop','model','label','型号','width',120,'breakpoint','md'),
     JSON_OBJECT('prop','supplier_id','label','供应商','width',140,'breakpoint','lg','formatter','supplierName'),
     JSON_OBJECT('prop','owner_customer_id','label','归属客户','width',140,'breakpoint','lg','formatter','customerName'),
     JSON_OBJECT('prop','purchase_date','label','采购日期','width',120,'breakpoint','xl'),
     JSON_OBJECT('prop','status','label','状态','width',100,'breakpoint','md','tag',true,'tagTypes',JSON_OBJECT('IDLE','info','IN_USE','success','REPAIR','warning','SCRAPPED','danger'))
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','device_code','label','设备编号','type','input','span',6,'breakpoint','xs'),
     JSON_OBJECT('prop','device_name','label','设备名称','type','input','span',6,'breakpoint','xs'),
     JSON_OBJECT('prop','status','label','状态','type','select','span',6,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','闲置','value','IDLE'),JSON_OBJECT('label','在用','value','IN_USE'),JSON_OBJECT('label','维修','value','REPAIR'),JSON_OBJECT('label','报废','value','SCRAPPED'))),
     JSON_OBJECT('prop','supplier_id','label','供应商','type','select','span',6,'breakpoint','sm')
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit'),JSON_OBJECT('type','delete','label','删除','icon','Delete','confirm','确认删除该设备？')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_device/list'
 ),
 1, 'DRAFT', 'DEVICE', 'demo', NOW() - INTERVAL 30 DAY, 'demo', NOW() - INTERVAL 30 DAY, 0),

('list_demo_customer',
 '客户列表',
 '演示-CRM 客户列表（含等级筛选/行业筛选）',
 JSON_OBJECT(
   'title','客户列表','entityCode','demo_customer',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','customer_code','label','客户编号','width',120,'breakpoint','xs'),
     JSON_OBJECT('prop','customer_name','label','客户名称','width',180,'breakpoint','xs'),
     JSON_OBJECT('prop','industry','label','行业','width',100,'breakpoint','md','formatter','industryText'),
     JSON_OBJECT('prop','contact_person','label','联系人','width',100,'breakpoint','md'),
     JSON_OBJECT('prop','contact_phone','label','联系电话','width',130,'breakpoint','lg'),
     JSON_OBJECT('prop','level','label','等级','width',80,'breakpoint','md','tag',true,'tagTypes',JSON_OBJECT('A','success','B','warning','C','info'))
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','customer_name','label','客户名称','type','input','span',8,'breakpoint','xs'),
     JSON_OBJECT('prop','level','label','等级','type','select','span',8,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','A级','value','A'),JSON_OBJECT('label','B级','value','B'),JSON_OBJECT('label','C级','value','C'))),
     JSON_OBJECT('prop','industry','label','行业','type','select','span',8,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','制造业','value','MFG'),JSON_OBJECT('label','互联网','value','IT'),JSON_OBJECT('label','金融','value','FIN')))
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_customer/list'
 ),
 1, 'DRAFT', 'CRM', 'demo', NOW() - INTERVAL 28 DAY, 'demo', NOW() - INTERVAL 28 DAY, 0),

('list_demo_supplier',
 '供应商列表',
 '演示-供应商列表（含评级筛选）',
 JSON_OBJECT(
   'title','供应商列表','entityCode','demo_supplier',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','supplier_code','label','供应商编号','width',120,'breakpoint','xs'),
     JSON_OBJECT('prop','supplier_name','label','供应商名称','width',180,'breakpoint','xs'),
     JSON_OBJECT('prop','contact_person','label','联系人','width',100,'breakpoint','md'),
     JSON_OBJECT('prop','contact_phone','label','联系电话','width',130,'breakpoint','md'),
     JSON_OBJECT('prop','rating','label','评级','width',80,'breakpoint','md','tag',true,'tagTypes',JSON_OBJECT('A','success','B','warning','C','danger')),
     JSON_OBJECT('prop','bank_account','label','银行账号','width',160,'breakpoint','lg')
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','supplier_name','label','供应商名称','type','input','span',12,'breakpoint','xs'),
     JSON_OBJECT('prop','rating','label','评级','type','select','span',12,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','优质','value','A'),JSON_OBJECT('label','合格','value','B'),JSON_OBJECT('label','待改进','value','C')))
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit'),JSON_OBJECT('type','delete','label','删除','icon','Delete','confirm','确认删除该供应商？')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_supplier/list'
 ),
 1, 'DRAFT', 'SUPPLIER', 'demo', NOW() - INTERVAL 27 DAY, 'demo', NOW() - INTERVAL 27 DAY, 0),

('list_demo_contract',
 '合同列表',
 '演示-合同列表（含金额/到期日筛选）',
 JSON_OBJECT(
   'title','合同列表','entityCode','demo_contract',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','contract_no','label','合同编号','width',140,'sortable',true,'breakpoint','xs'),
     JSON_OBJECT('prop','title','label','合同名称','width',200,'breakpoint','xs'),
     JSON_OBJECT('prop','customer_id','label','客户','width',160,'breakpoint','md','formatter','customerName'),
     JSON_OBJECT('prop','amount','label','金额(元)','width',130,'sortable',true,'breakpoint','md','formatter','money'),
     JSON_OBJECT('prop','sign_date','label','签约日期','width',120,'breakpoint','lg'),
     JSON_OBJECT('prop','expire_date','label','到期日期','width',120,'breakpoint','lg'),
     JSON_OBJECT('prop','type','label','类型','width',100,'breakpoint','md','formatter','contractTypeText')
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','contract_no','label','合同编号','type','input','span',6,'breakpoint','xs'),
     JSON_OBJECT('prop','title','label','合同名称','type','input','span',6,'breakpoint','xs'),
     JSON_OBJECT('prop','customer_id','label','客户','type','select','span',6,'breakpoint','sm'),
     JSON_OBJECT('prop','type','label','类型','type','select','span',6,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','销售','value','SALE'),JSON_OBJECT('label','采购','value','PURCHASE'),JSON_OBJECT('label','服务','value','SERVICE')))
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit'),JSON_OBJECT('type','submit','label','提交审批','icon','Promotion')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_contract/list'
 ),
 1, 'DRAFT', 'CONTRACT', 'demo', NOW() - INTERVAL 25 DAY, 'demo', NOW() - INTERVAL 25 DAY, 0),

('list_demo_work_order',
 '工单列表',
 '演示-售后工单列表（含优先级/状态筛选）',
 JSON_OBJECT(
   'title','工单列表','entityCode','demo_work_order',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','work_order_no','label','工单编号','width',140,'sortable',true,'breakpoint','xs'),
     JSON_OBJECT('prop','device_id','label','设备','width',140,'breakpoint','md','formatter','deviceName'),
     JSON_OBJECT('prop','priority','label','优先级','width',90,'breakpoint','md','tag',true,'tagTypes',JSON_OBJECT('URGENT','danger','HIGH','warning','MEDIUM','info','LOW','success')),
     JSON_OBJECT('prop','fault_type','label','故障类型','width',100,'breakpoint','lg'),
     JSON_OBJECT('prop','assignee','label','工程师','width',100,'breakpoint','lg'),
     JSON_OBJECT('prop','status','label','状态','width',100,'breakpoint','md','tag',true,'tagTypes',JSON_OBJECT('OPEN','warning','IN_PROGRESS','primary','RESOLVED','success','CLOSED','info')),
     JSON_OBJECT('prop','create_time','label','创建时间','width',160,'breakpoint','xl')
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','work_order_no','label','工单编号','type','input','span',6,'breakpoint','xs'),
     JSON_OBJECT('prop','priority','label','优先级','type','select','span',6,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','紧急','value','URGENT'),JSON_OBJECT('label','高','value','HIGH'),JSON_OBJECT('label','中','value','MEDIUM'),JSON_OBJECT('label','低','value','LOW'))),
     JSON_OBJECT('prop','status','label','状态','type','select','span',6,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','待处理','value','OPEN'),JSON_OBJECT('label','处理中','value','IN_PROGRESS'),JSON_OBJECT('label','已解决','value','RESOLVED'),JSON_OBJECT('label','已关闭','value','CLOSED'))),
     JSON_OBJECT('prop','assignee','label','工程师','type','input','span',6,'breakpoint','sm')
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit'),JSON_OBJECT('type','assign','label','指派','icon','User')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_work_order/list'
 ),
 1, 'DRAFT', 'WORK_ORDER', 'demo', NOW() - INTERVAL 24 DAY, 'demo', NOW() - INTERVAL 24 DAY, 0),

('list_demo_inspection',
 '巡检记录列表',
 '演示-设备巡检记录列表',
 JSON_OBJECT(
   'title','巡检记录列表','entityCode','demo_inspection_record',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','inspection_no','label','巡检编号','width',140,'breakpoint','xs'),
     JSON_OBJECT('prop','device_id','label','设备','width',140,'breakpoint','md','formatter','deviceName'),
     JSON_OBJECT('prop','inspector_emp_id','label','巡检人','width',100,'breakpoint','md','formatter','empName'),
     JSON_OBJECT('prop','inspect_date','label','巡检日期','width',120,'breakpoint','md'),
     JSON_OBJECT('prop','result','label','结果','width',100,'breakpoint','md','tag',true,'tagTypes',JSON_OBJECT('NORMAL','success','ABNORMAL','warning','NEED_REPAIR','danger')),
     JSON_OBJECT('prop','temperature','label','温度(℃)','width',100,'breakpoint','lg')
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','inspection_no','label','巡检编号','type','input','span',8,'breakpoint','xs'),
     JSON_OBJECT('prop','result','label','结果','type','select','span',8,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','正常','value','NORMAL'),JSON_OBJECT('label','异常','value','ABNORMAL'),JSON_OBJECT('label','需维修','value','NEED_REPAIR'))),
     JSON_OBJECT('prop','inspect_date','label','巡检日期','type','date','span',8,'breakpoint','sm')
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_inspection_record/list'
 ),
 1, 'DRAFT', 'INSPECTION', 'demo', NOW() - INTERVAL 22 DAY, 'demo', NOW() - INTERVAL 22 DAY, 0),

('list_demo_maintenance_plan',
 '维保计划列表',
 '演示-设备维保计划列表',
 JSON_OBJECT(
   'title','维保计划列表','entityCode','demo_maintenance_plan',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','plan_no','label','计划编号','width',140,'breakpoint','xs'),
     JSON_OBJECT('prop','device_id','label','设备','width',140,'breakpoint','md','formatter','deviceName'),
     JSON_OBJECT('prop','cycle','label','周期','width',100,'breakpoint','md'),
     JSON_OBJECT('prop','next_maintenance_date','label','下次维保日','width',130,'breakpoint','md'),
     JSON_OBJECT('prop','owner','label','负责人','width',100,'breakpoint','lg'),
     JSON_OBJECT('prop','enabled','label','启用','width',80,'breakpoint','md','formatter','enabledText')
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','plan_no','label','计划编号','type','input','span',8,'breakpoint','xs'),
     JSON_OBJECT('prop','cycle','label','周期','type','select','span',8,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','月度','value','MONTHLY'),JSON_OBJECT('label','季度','value','QUARTERLY'),JSON_OBJECT('label','半年度','value','HALF_YEAR'),JSON_OBJECT('label','年度','value','YEARLY'))),
     JSON_OBJECT('prop','enabled','label','启用','type','select','span',8,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','启用','value',1),JSON_OBJECT('label','停用','value',0)))
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit'),JSON_OBJECT('type','execute','label','执行','icon','VideoPlay')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_maintenance_plan/list'
 ),
 1, 'DRAFT', 'MAINTENANCE', 'demo', NOW() - INTERVAL 20 DAY, 'demo', NOW() - INTERVAL 20 DAY, 0),

('list_demo_kb_article',
 '知识库列表',
 '演示-知识库文章列表（含分类/标签筛选）',
 JSON_OBJECT(
   'title','知识库列表','entityCode','demo_kb_article',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','title','label','标题','width',280,'breakpoint','xs'),
     JSON_OBJECT('prop','category','label','分类','width',100,'breakpoint','md','formatter','categoryText'),
     JSON_OBJECT('prop','tags','label','标签','width',160,'breakpoint','lg'),
     JSON_OBJECT('prop','author_emp_id','label','作者','width',100,'breakpoint','md','formatter','empName'),
     JSON_OBJECT('prop','view_count','label','阅读量','width',90,'sortable',true,'breakpoint','lg'),
     JSON_OBJECT('prop','status','label','状态','width',90,'breakpoint','md','tag',true,'tagTypes',JSON_OBJECT('DRAFT','info','PUBLISHED','success','ARCHIVED','danger'))
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','title','label','标题','type','input','span',8,'breakpoint','xs'),
     JSON_OBJECT('prop','category','label','分类','type','select','span',8,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','操作手册','value','MANUAL'),JSON_OBJECT('label','故障案例','value','CASE'),JSON_OBJECT('label','FAQ','value','FAQ'),JSON_OBJECT('label','规范','value','SPEC'))),
     JSON_OBJECT('prop','status','label','状态','type','select','span',8,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','草稿','value','DRAFT'),JSON_OBJECT('label','已发布','value','PUBLISHED'),JSON_OBJECT('label','已归档','value','ARCHIVED')))
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit'),JSON_OBJECT('type','publish','label','发布','icon','Promotion')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_kb_article/list'
 ),
 1, 'DRAFT', 'KB', 'demo', NOW() - INTERVAL 18 DAY, 'demo', NOW() - INTERVAL 18 DAY, 0),

('list_demo_department',
 '部门列表',
 '演示-部门列表（含树形结构）',
 JSON_OBJECT(
   'title','部门列表','entityCode','demo_department',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','dept_code','label','部门编码','width',120,'breakpoint','xs'),
     JSON_OBJECT('prop','dept_name','label','部门名称','width',180,'breakpoint','xs'),
     JSON_OBJECT('prop','parent_id','label','上级部门','width',140,'breakpoint','md','formatter','parentDeptName'),
     JSON_OBJECT('prop','manager_id','label','负责人','width',120,'breakpoint','md'),
     JSON_OBJECT('prop','description','label','描述','width',200,'breakpoint','lg')
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','dept_name','label','部门名称','type','input','span',12,'breakpoint','xs'),
     JSON_OBJECT('prop','dept_code','label','部门编码','type','input','span',12,'breakpoint','sm')
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit'),JSON_OBJECT('type','delete','label','删除','icon','Delete')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_department/list'
 ),
 1, 'DRAFT', 'EMP_ONBOARDING', 'demo', NOW() - INTERVAL 35 DAY, 'demo', NOW() - INTERVAL 35 DAY, 0),

('list_demo_onboarding_task',
 '入职任务列表',
 '演示-入职任务列表（含状态/截止日筛选）',
 JSON_OBJECT(
   'title','入职任务列表','entityCode','demo_onboarding_task',
   'columns', JSON_ARRAY(
     JSON_OBJECT('prop','task_name','label','任务名称','width',200,'breakpoint','xs'),
     JSON_OBJECT('prop','task_type','label','任务类型','width',100,'breakpoint','md'),
     JSON_OBJECT('prop','assignee','label','负责人','width',100,'breakpoint','md'),
     JSON_OBJECT('prop','status','label','状态','width',100,'breakpoint','md','tag',true,'tagTypes',JSON_OBJECT('PENDING','info','IN_PROGRESS','warning','DONE','success')),
     JSON_OBJECT('prop','due_date','label','截止日期','width',120,'breakpoint','lg'),
     JSON_OBJECT('prop','completed_at','label','完成时间','width',160,'breakpoint','xl')
   ),
   'filters', JSON_ARRAY(
     JSON_OBJECT('prop','task_name','label','任务名称','type','input','span',8,'breakpoint','xs'),
     JSON_OBJECT('prop','status','label','状态','type','select','span',8,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','待处理','value','PENDING'),JSON_OBJECT('label','进行中','value','IN_PROGRESS'),JSON_OBJECT('label','已完成','value','DONE'))),
     JSON_OBJECT('prop','task_type','label','类型','type','select','span',8,'breakpoint','sm','options',JSON_ARRAY(JSON_OBJECT('label','设备配备','value','EQUIPMENT'),JSON_OBJECT('label','培训','value','TRAINING'),JSON_OBJECT('label','入职引导','value','ORIENTATION')))
   ),
   'operations', JSON_ARRAY(JSON_OBJECT('type','view','label','查看','icon','View'),JSON_OBJECT('type','edit','label','编辑','icon','Edit')),
   'pagination', JSON_OBJECT('pageSize',10,'pageSizes',JSON_ARRAY(10,20,50),'layout','total, sizes, prev, pager, next, jumper'),
   'searchApi','/api/lowcode/dynamic/demo_onboarding_task/list'
 ),
 1, 'DRAFT', 'EMP_ONBOARDING', 'demo', NOW() - INTERVAL 35 DAY, 'demo', NOW() - INTERVAL 35 DAY, 0);

-- 1.5 微流定义（在 V58 现有 1 个基础上补充 10 个，合计 11 个 ≥10）
--     节点类型覆盖 START/END/CALL_RULE/ASSIGN/CALL_CONNECTOR/IF/LOOP/CALL_MICROFLOW/CALL_PROCESS
INSERT IGNORE INTO `low_microflow`
    (`code`, `name`, `description`, `definition`, `status`, `version`, `biz_type`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
VALUES
('microflow_demo_device_status_change',
 '设备状态变更同步微流',
 '演示-设备状态变更后同步到监控系统',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','设备状态变更开始')),
     JSON_OBJECT('id','n_conn','type','CALL_CONNECTOR','config',JSON_OBJECT('connectorCode','connector_demo_crm_oauth2','inputs',JSON_OBJECT('device_code','${device_code}','status','${status}')),'description','同步设备状态到监控系统'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','同步完成'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_conn'),JSON_OBJECT('source','n_conn','target','n_end'))
 ),
 'DRAFT', 1, 'DEVICE', 'demo', 'demo', NOW() - INTERVAL 29 DAY, NOW() - INTERVAL 29 DAY, 0),

('microflow_demo_work_order_assign',
 '工单智能分配微流',
 '演示-工单创建后按规则自动分配工程师',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','工单分配开始')),
     JSON_OBJECT('id','n_rule','type','CALL_RULE','config',JSON_OBJECT('ruleCode','rule_demo_work_order_priority','inputs',JSON_OBJECT('fault_type','${fault_type}','priority','${priority}')),'description','调用优先级规则'),
     JSON_OBJECT('id','n_assign','type','ASSIGN','config',JSON_OBJECT('expression','${rule_result}.assignee','target','assignee'),'description','赋值工程师'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','工单分配结束'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_rule'),JSON_OBJECT('source','n_rule','target','n_assign'),JSON_OBJECT('source','n_assign','target','n_end'))
 ),
 'DRAFT', 1, 'WORK_ORDER', 'demo', 'demo', NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY, 0),

('microflow_demo_kb_publish_notify',
 '知识库发布通知微流',
 '演示-知识库文章发布后通知订阅者',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','发布通知开始')),
     JSON_OBJECT('id','n_proc','type','CALL_PROCESS','config',JSON_OBJECT('processKey','demo_kb_publish_process','inputs',JSON_OBJECT('article_id','${id}','title','${title}')),'description','调用发布流程'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','通知完成'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_proc'),JSON_OBJECT('source','n_proc','target','n_end'))
 ),
 'DRAFT', 1, 'KB', 'demo', 'demo', NOW() - INTERVAL 17 DAY, NOW() - INTERVAL 17 DAY, 0),

('microflow_demo_contract_approval_flow',
 '合同审批微流',
 '演示-合同金额>10万走财务二审，否则直接通过',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','合同审批开始')),
     JSON_OBJECT('id','n_if','type','IF','config',JSON_OBJECT('condition','${amount} > 100000','trueBranch','n_finance','falseBranch','n_pass'),'description','判断合同金额'),
     JSON_OBJECT('id','n_finance','type','CALL_MICROFLOW','config',JSON_OBJECT('microflowCode','microflow_demo_finance_review','inputs',JSON_OBJECT('contract_id','${id}','amount','${amount}')),'description','调用财务二审子流'),
     JSON_OBJECT('id','n_pass','type','ASSIGN','config',JSON_OBJECT('expression','APPROVED','target','approval_status'),'description','直接通过'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','合同审批结束'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_if'),JSON_OBJECT('source','n_finance','target','n_end'),JSON_OBJECT('source','n_pass','target','n_end'))
 ),
 'DRAFT', 1, 'CONTRACT', 'demo', 'demo', NOW() - INTERVAL 24 DAY, NOW() - INTERVAL 24 DAY, 0),

('microflow_demo_inspection_remind',
 '巡检提醒批量发送微流',
 '演示-循环遍历待巡检设备，逐个发送提醒邮件',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','巡检提醒开始')),
     JSON_OBJECT('id','n_loop','type','LOOP','config',JSON_OBJECT('iterable','${devices}','varName','device'),'description','遍历设备列表'),
     JSON_OBJECT('id','n_conn','type','CALL_CONNECTOR','config',JSON_OBJECT('connectorCode','connector_demo_email_bearer','inputs',JSON_OBJECT('to','${device.inspector_email}','subject','巡检提醒','body',CONCAT('请于今日完成 ', '${device.device_name}', ' 巡检'))),'description','发送提醒邮件'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','提醒发送完成'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_loop'),JSON_OBJECT('source','n_loop','target','n_conn'),JSON_OBJECT('source','n_conn','target','n_end'))
 ),
 'DRAFT', 1, 'INSPECTION', 'demo', 'demo', NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 21 DAY, 0),

('microflow_demo_maintenance_generate',
 '维保任务批量生成微流',
 '演示-循环遍历到期维保计划，生成维保工单',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','维保任务生成开始')),
     JSON_OBJECT('id','n_loop','type','LOOP','config',JSON_OBJECT('iterable','${due_plans}','varName','plan'),'description','遍历到期计划'),
     JSON_OBJECT('id','n_assign','type','ASSIGN','config',JSON_OBJECT('expression',CONCAT('MAINT-', '${plan.plan_no}', '-', NOW()),'target','work_order_no'),'description','生成工单号'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','维保任务生成完成'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_loop'),JSON_OBJECT('source','n_loop','target','n_assign'),JSON_OBJECT('source','n_assign','target','n_end'))
 ),
 'DRAFT', 1, 'MAINTENANCE', 'demo', 'demo', NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 19 DAY, 0),

('microflow_demo_customer_followup',
 '客户跟进同步微流',
 '演示-客户跟进记录同步到 CRM 系统',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','客户跟进开始')),
     JSON_OBJECT('id','n_assign','type','ASSIGN','config',JSON_OBJECT('expression',NOW(),'target','followup_time'),'description','记录跟进时间'),
     JSON_OBJECT('id','n_conn','type','CALL_CONNECTOR','config',JSON_OBJECT('connectorCode','connector_demo_crm_oauth2','inputs',JSON_OBJECT('customer_id','${customer_id}','content','${content}','followup_time','${followup_time}')),'description','同步到 CRM'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','跟进同步完成'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_assign'),JSON_OBJECT('source','n_assign','target','n_conn'),JSON_OBJECT('source','n_conn','target','n_end'))
 ),
 'DRAFT', 1, 'CRM', 'demo', 'demo', NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, 0),

('microflow_demo_supplier_evaluate',
 '供应商评估微流',
 '演示-按评级规则评估供应商，不达标则告警',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','供应商评估开始')),
     JSON_OBJECT('id','n_rule','type','CALL_RULE','config',JSON_OBJECT('ruleCode','rule_demo_supplier_rating','inputs',JSON_OBJECT('supplier_id','${supplier_id}','quality_score','${quality_score}','delivery_score','${delivery_score}')),'description','调用评级规则'),
     JSON_OBJECT('id','n_if','type','IF','config',JSON_OBJECT('condition','${rule_result}.rating == "C"','trueBranch','n_alert','falseBranch','n_end'),'description','判断是否不达标'),
     JSON_OBJECT('id','n_alert','type','CALL_CONNECTOR','config',JSON_OBJECT('connectorCode','connector_demo_dingtalk_bearer','inputs',JSON_OBJECT('text',CONCAT('供应商 ', '${supplier_name}', ' 评级为 C，请关注'))),'description','发送钉钉告警'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','评估结束'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_rule'),JSON_OBJECT('source','n_rule','target','n_if'),JSON_OBJECT('source','n_alert','target','n_end'))
 ),
 'DRAFT', 1, 'SUPPLIER', 'demo', 'demo', NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY, 0),

('microflow_demo_contract_expire_remind',
 '合同到期提醒微流',
 '演示-循环扫描即将到期合同，发送提醒',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','到期提醒开始')),
     JSON_OBJECT('id','n_loop','type','LOOP','config',JSON_OBJECT('iterable','${expiring_contracts}','varName','contract'),'description','遍历到期合同'),
     JSON_OBJECT('id','n_conn','type','CALL_CONNECTOR','config',JSON_OBJECT('connectorCode','connector_demo_sms_apikey','inputs',JSON_OBJECT('phone','${contract.contact_phone}','msg',CONCAT('合同 ', '${contract.contract_no}', ' 将于 ', '${contract.expire_date}', ' 到期'))),'description','发送短信提醒'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','提醒完成'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_loop'),JSON_OBJECT('source','n_loop','target','n_conn'),JSON_OBJECT('source','n_conn','target','n_end'))
 ),
 'DRAFT', 1, 'CONTRACT', 'demo', 'demo', NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY, 0),

('microflow_demo_finance_review',
 '财务二审子微流',
 '演示-大额合同财务二审子流程',
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','n_start','type','START','config',JSON_OBJECT('description','财务二审开始')),
     JSON_OBJECT('id','n_rule','type','CALL_RULE','config',JSON_OBJECT('ruleCode','rule_demo_risk_warning','inputs',JSON_OBJECT('amount','${amount}','customer_level','${customer_level}')),'description','调用风险预警规则'),
     JSON_OBJECT('id','n_end','type','END','config',JSON_OBJECT('description','财务二审结束'))
   ),
   'edges', JSON_ARRAY(JSON_OBJECT('source','n_start','target','n_rule'),JSON_OBJECT('source','n_rule','target','n_end'))
 ),
 'DRAFT', 1, 'CONTRACT', 'demo', 'demo', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY, 0);

-- 1.6 规则定义（在 V58 现有 1 个 DECISION_TABLE 基础上补充 10 个，合计 11 个 ≥10）
--     类型覆盖 DECISION_TABLE/EXPRESSION/LITEFLOW
INSERT IGNORE INTO `low_rule`
    (`code`, `name`, `description`, `type`, `definition`, `ext`, `status`, `version`, `biz_type`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
VALUES
('rule_demo_contract_discount',
 '合同折扣计算表达式规则',
 '演示-按合同金额阶梯计算折扣：≥10万9.5折，≥50万9折，≥100万8.5折',
 'EXPRESSION',
 'amount >= 100000 ? 0.85 : (amount >= 50000 ? 0.90 : (amount >= 10000 ? 0.95 : 1.0))',
 JSON_OBJECT('inputsSchema', JSON_ARRAY(JSON_OBJECT('name','amount','type','number','label','合同金额'))),
 'DRAFT', 1, 'CONTRACT', 'demo', 'demo', NOW() - INTERVAL 24 DAY, NOW() - INTERVAL 24 DAY, 0),

('rule_demo_work_order_priority',
 '工单优先级判定表达式规则',
 '演示-按故障类型与客户等级判定工单最终优先级',
 'EXPRESSION',
 'fault_type == "HARDWARE" && customer_level == "A" ? "URGENT" : (fault_type == "HARDWARE" ? "HIGH" : (customer_level == "A" ? "HIGH" : "MEDIUM"))',
 JSON_OBJECT('inputsSchema', JSON_ARRAY(JSON_OBJECT('name','fault_type','type','string'),JSON_OBJECT('name','customer_level','type','string'))),
 'DRAFT', 1, 'WORK_ORDER', 'demo', 'demo', NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY, 0),

('rule_demo_customer_grade',
 '客户分级 LiteFlow 规则',
 '演示-按年交易额与回款率多节点判定客户分级',
 'LITEFLOW',
 'THEN(customerAmountChain, customerPaymentChain, customerGradeChain);',
 JSON_OBJECT('inputsSchema', JSON_ARRAY(JSON_OBJECT('name','annual_amount','type','number'),JSON_OBJECT('name','payment_rate','type','number'))),
 'DRAFT', 1, 'CRM', 'demo', 'demo', NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY, 0),

('rule_demo_device_status_transition',
 '设备状态转换决策表',
 '演示-定义设备状态合法转换路径',
 'DECISION_TABLE',
 JSON_OBJECT(
   'hitPolicy','FIRST',
   'conditionColumns', JSON_ARRAY(JSON_OBJECT('field','current_status','operator','EQ','label','当前状态'),JSON_OBJECT('field','action','operator','EQ','label','操作')),
   'actionColumns', JSON_ARRAY(JSON_OBJECT('field','next_status','label','目标状态'),JSON_OBJECT('field','allowed','label','是否允许')),
   'rows', JSON_ARRAY(
     JSON_OBJECT('conditions',JSON_ARRAY('IDLE','ENABLE'),'actions',JSON_ARRAY('IN_USE',true)),
     JSON_OBJECT('conditions',JSON_ARRAY('IN_USE','DISABLE'),'actions',JSON_ARRAY('IDLE',true)),
     JSON_OBJECT('conditions',JSON_ARRAY('IN_USE','REPAIR'),'actions',JSON_ARRAY('REPAIR',true)),
     JSON_OBJECT('conditions',JSON_ARRAY('REPAIR','COMPLETE'),'actions',JSON_ARRAY('IN_USE',true)),
     JSON_OBJECT('conditions',JSON_ARRAY('IN_USE','SCRAP'),'actions',JSON_ARRAY('SCRAPPED',true)),
     JSON_OBJECT('conditions',JSON_ARRAY('IDLE','SCRAP'),'actions',JSON_ARRAY('SCRAPPED',true)),
     JSON_OBJECT('conditions',JSON_ARRAY('SCRAPPED','ENABLE'),'actions',JSON_ARRAY('IDLE',false))
   )
 ),
 NULL,
 'DRAFT', 1, 'DEVICE', 'demo', 'demo', NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 28 DAY, 0),

('rule_demo_inspection_judge',
 '巡检结果判定表达式规则',
 '演示-按温度与振动值判定巡检结果',
 'EXPRESSION',
 'temperature > 75 ? "NEED_REPAIR" : (temperature > 60 || vibration > 5.0 ? "ABNORMAL" : "NORMAL")',
 JSON_OBJECT('inputsSchema', JSON_ARRAY(JSON_OBJECT('name','temperature','type','number'),JSON_OBJECT('name','vibration','type','number'))),
 'DRAFT', 1, 'INSPECTION', 'demo', 'demo', NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 21 DAY, 0),

('rule_demo_supplier_rating',
 '供应商评级 LiteFlow 规则',
 '演示-按质量分/交付分/价格分加权计算供应商评级',
 'LITEFLOW',
 'THEN(qualityScoreNode, deliveryScoreNode, priceScoreNode, ratingAggregateNode);',
 JSON_OBJECT('inputsSchema', JSON_ARRAY(JSON_OBJECT('name','quality_score','type','number'),JSON_OBJECT('name','delivery_score','type','number'),JSON_OBJECT('name','price_score','type','number'))),
 'DRAFT', 1, 'SUPPLIER', 'demo', 'demo', NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY, 0),

('rule_demo_maintenance_cycle',
 '维保周期决策表',
 '演示-按设备类型与重要等级决定维保周期',
 'DECISION_TABLE',
 JSON_OBJECT(
   'hitPolicy','FIRST',
   'conditionColumns', JSON_ARRAY(JSON_OBJECT('field','device_type','operator','EQ','label','设备类型'),JSON_OBJECT('field','importance','operator','EQ','label','重要等级')),
   'actionColumns', JSON_ARRAY(JSON_OBJECT('field','cycle','label','维保周期')),
   'rows', JSON_ARRAY(
     JSON_OBJECT('conditions',JSON_ARRAY('SERVER','HIGH'),'actions',JSON_ARRAY('MONTHLY')),
     JSON_OBJECT('conditions',JSON_ARRAY('SERVER','MEDIUM'),'actions',JSON_ARRAY('QUARTERLY')),
     JSON_OBJECT('conditions',JSON_ARRAY('SERVER','LOW'),'actions',JSON_ARRAY('HALF_YEAR')),
     JSON_OBJECT('conditions',JSON_ARRAY('NETWORK','HIGH'),'actions',JSON_ARRAY('MONTHLY')),
     JSON_OBJECT('conditions',JSON_ARRAY('NETWORK','MEDIUM'),'actions',JSON_ARRAY('QUARTERLY')),
     JSON_OBJECT('conditions',JSON_ARRAY('STORAGE','LOW'),'actions',JSON_ARRAY('YEARLY'))
   )
 ),
 NULL,
 'DRAFT', 1, 'MAINTENANCE', 'demo', 'demo', NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 19 DAY, 0),

('rule_demo_kb_recommend',
 '知识库推荐表达式规则',
 '演示-按阅读量与最近发布时间计算推荐权重',
 'EXPRESSION',
 'view_count * 0.3 + (days_since_publish < 30 ? 50 : 0) + (category == user_interest ? 20 : 0)',
 JSON_OBJECT('inputsSchema', JSON_ARRAY(JSON_OBJECT('name','view_count','type','number'),JSON_OBJECT('name','days_since_publish','type','number'),JSON_OBJECT('name','category','type','string'),JSON_OBJECT('name','user_interest','type','string'))),
 'DRAFT', 1, 'KB', 'demo', 'demo', NOW() - INTERVAL 17 DAY, NOW() - INTERVAL 17 DAY, 0),

('rule_demo_risk_warning',
 '风险预警 LiteFlow 规则',
 '演示-多节点评估合同风险等级',
 'LITEFLOW',
 'SWITCH(amountRiskNode).to(lowRiskNode, midRiskNode, highRiskNode);',
 JSON_OBJECT('inputsSchema', JSON_ARRAY(JSON_OBJECT('name','amount','type','number'),JSON_OBJECT('name','customer_level','type','string'))),
 'DRAFT', 1, 'CONTRACT', 'demo', 'demo', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY, 0),

('rule_demo_asset_depreciation',
 '资产折旧计算表达式规则',
 '演示-按使用年限与残值率直线法计算折旧',
 'EXPRESSION',
 '(original_value * (1 - residual_rate)) / useful_life',
 JSON_OBJECT('inputsSchema', JSON_ARRAY(JSON_OBJECT('name','original_value','type','number'),JSON_OBJECT('name','residual_rate','type','number'),JSON_OBJECT('name','useful_life','type','number'))),
 'DRAFT', 1, 'DEVICE', 'demo', 'demo', NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 16 DAY, 0);

-- 1.7 触发器（在 V58 现有 1 个 CRUD 基础上补充 10 个，合计 11 个 ≥10）
--     类型覆盖 CRUD/QUARTZ/EVENT，目标覆盖 MICROFLOW/PROCESS
INSERT IGNORE INTO `low_trigger`
    (`code`, `name`, `type`, `config`, `target_type`, `target_code`, `status`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
VALUES
('trigger_demo_quartz_inspection_daily',
 '每日巡检提醒定时触发器',
 'QUARTZ',
 JSON_OBJECT('cron','0 0 8 * * ?','description','每天 8:00 触发巡检提醒'),
 'MICROFLOW','microflow_demo_inspection_remind','ACTIVE',
 'demo','demo',NOW() - INTERVAL 21 DAY,NOW() - INTERVAL 21 DAY,0),

('trigger_demo_quartz_contract_expire',
 '合同到期扫描定时触发器',
 'QUARTZ',
 JSON_OBJECT('cron','0 0 9 1 * ?','description','每月 1 日 9:00 扫描到期合同'),
 'MICROFLOW','microflow_demo_contract_expire_remind','ACTIVE',
 'demo','demo',NOW() - INTERVAL 20 DAY,NOW() - INTERVAL 20 DAY,0),

('trigger_demo_quartz_maintenance_generate',
 '维保任务生成定时触发器',
 'QUARTZ',
 JSON_OBJECT('cron','0 30 7 * * ?','description','每天 7:30 生成当日维保任务'),
 'PROCESS','demo_maintenance_generate_process','ACTIVE',
 'demo','demo',NOW() - INTERVAL 19 DAY,NOW() - INTERVAL 19 DAY,0),

('trigger_demo_crud_work_order_create',
 '工单创建后触发分配微流',
 'CRUD',
 JSON_OBJECT('entityCode','demo_work_order','operations',JSON_ARRAY('CREATE'),'timing',JSON_ARRAY('AFTER')),
 'MICROFLOW','microflow_demo_work_order_assign','ACTIVE',
 'demo','demo',NOW() - INTERVAL 23 DAY,NOW() - INTERVAL 23 DAY,0),

('trigger_demo_crud_device_status_change',
 '设备状态变更后触发同步',
 'CRUD',
 JSON_OBJECT('entityCode','demo_device','operations',JSON_ARRAY('UPDATE'),'timing',JSON_ARRAY('AFTER'),'fields',JSON_ARRAY('status')),
 'MICROFLOW','microflow_demo_device_status_change','ACTIVE',
 'demo','demo',NOW() - INTERVAL 29 DAY,NOW() - INTERVAL 29 DAY,0),

('trigger_demo_event_customer_complaint',
 '客户投诉事件触发流程',
 'EVENT',
 JSON_OBJECT('eventType','CUSTOMER_COMPLAINT','description','客户投诉事件触发处理流程'),
 'PROCESS','demo_customer_complaint_process','ACTIVE',
 'demo','demo',NOW() - INTERVAL 15 DAY,NOW() - INTERVAL 15 DAY,0),

('trigger_demo_event_kb_publish',
 '知识库发布事件触发通知',
 'EVENT',
 JSON_OBJECT('eventType','KB_ARTICLE_PUBLISHED','description','知识库文章发布事件'),
 'MICROFLOW','microflow_demo_kb_publish_notify','ACTIVE',
 'demo','demo',NOW() - INTERVAL 17 DAY,NOW() - INTERVAL 17 DAY,0),

('trigger_demo_quartz_daily_report',
 '日报生成定时触发器',
 'QUARTZ',
 JSON_OBJECT('cron','0 0 18 * * ?','description','每天 18:00 生成日报'),
 'MICROFLOW','microflow_demo_work_order_close_cleanup','ACTIVE',
 'demo','demo',NOW() - INTERVAL 14 DAY,NOW() - INTERVAL 14 DAY,0),

('trigger_demo_crud_contract_create',
 '合同创建后触发审批微流',
 'CRUD',
 JSON_OBJECT('entityCode','demo_contract','operations',JSON_ARRAY('CREATE'),'timing',JSON_ARRAY('AFTER')),
 'MICROFLOW','microflow_demo_contract_approval_flow','ACTIVE',
 'demo','demo',NOW() - INTERVAL 24 DAY,NOW() - INTERVAL 24 DAY,0),

('trigger_demo_event_urgent_work_order',
 '紧急工单事件触发升级流程',
 'EVENT',
 JSON_OBJECT('eventType','URGENT_WORK_ORDER','description','紧急工单事件触发升级流程'),
 'PROCESS','demo_work_order_escalation_process','ACTIVE',
 'demo','demo',NOW() - INTERVAL 13 DAY,NOW() - INTERVAL 13 DAY,0);

-- 1.8 连接器（在 V58 现有 1 个 REST+API_KEY 基础上补充 10 个，合计 11 个 ≥10）
--     认证覆盖 OAUTH2/BASIC/BEARER/API_KEY/NONE，类型覆盖 REST/DB，方法覆盖 GET/POST/PUT
INSERT IGNORE INTO `low_connector`
    (`code`, `name`, `description`, `type`, `config`, `status`, `version`, `biz_type`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
VALUES
('connector_demo_crm_oauth2',
 'CRM 系统 OAuth2 连接器',
 '演示-对接外部 CRM 系统，OAuth2 认证，同步客户/跟进数据',
 'REST',
 JSON_OBJECT(
   'url','https://crm.example.com/api/v1','method','POST',
   'headers',JSON_OBJECT('Content-Type','application/json'),
   'auth',JSON_OBJECT('type','OAUTH2','tokenUrl','https://crm.example.com/oauth/token','clientId','${CRM_CLIENT_ID}','clientSecret','${CRM_CLIENT_SECRET}','grantType','client_credentials'),
   'retry',JSON_OBJECT('maxAttempts',3,'waitMillis',500),
   'timeoutMillis',8000
 ),
 'ACTIVE',1,'CRM','demo','demo',NOW() - INTERVAL 26 DAY,NOW() - INTERVAL 26 DAY,0),

('connector_demo_erp_basic',
 'ERP 系统 Basic 认证连接器',
 '演示-对接 ERP 系统，Basic 认证，查询订单数据',
 'REST',
 JSON_OBJECT(
   'url','https://erp.example.com/api/orders','method','GET',
   'headers',JSON_OBJECT('Accept','application/json'),
   'auth',JSON_OBJECT('type','BASIC','username','${ERP_USER}','password','${ERP_PASS}'),
   'retry',JSON_OBJECT('maxAttempts',2,'waitMillis',1000),
   'timeoutMillis',10000
 ),
 'ACTIVE',1,'CONTRACT','demo','demo',NOW() - INTERVAL 24 DAY,NOW() - INTERVAL 24 DAY,0),

('connector_demo_email_bearer',
 '邮件服务 Bearer 连接器',
 '演示-对接邮件服务，Bearer Token 认证，发送提醒邮件',
 'REST',
 JSON_OBJECT(
   'url','https://mail.example.com/api/send','method','POST',
   'headers',JSON_OBJECT('Content-Type','application/json'),
   'auth',JSON_OBJECT('type','BEARER','token','${MAIL_BEARER_TOKEN}'),
   'body',JSON_OBJECT('to','${to}','subject','${subject}','body','${body}'),
   'retry',JSON_OBJECT('maxAttempts',2,'waitMillis',800),
   'timeoutMillis',5000
 ),
 'ACTIVE',1,'INSPECTION','demo','demo',NOW() - INTERVAL 21 DAY,NOW() - INTERVAL 21 DAY,0),

('connector_demo_weather_none',
 '天气查询无认证连接器',
 '演示-对接公开天气 API，无认证，GET 方式',
 'REST',
 JSON_OBJECT(
   'url','https://api.weather.example.com/v1/forecast','method','GET',
   'headers',JSON_OBJECT('Accept','application/json'),
   'auth',JSON_OBJECT('type','NONE'),
   'params',JSON_OBJECT('city','${city}','days',3),
   'timeoutMillis',5000
 ),
 'ACTIVE',1,'DEVICE','demo','demo',NOW() - INTERVAL 27 DAY,NOW() - INTERVAL 27 DAY,0),

('connector_demo_mysql_readonly',
 'MySQL 只读分析库连接器',
 '演示-对接只读分析库，直连查询报表数据',
 'DB',
 JSON_OBJECT(
   'url','jdbc:mysql://analytics-db.example.com:3306/report_db?useSSL=true&characterEncoding=utf8',
   'username','${ANALYTICS_USER}','password','${ANALYTICS_PASS}',
   'driverClassName','com.mysql.cj.jdbc.Driver',
   'poolSize',5,
   'validationQuery','SELECT 1'
 ),
 'ACTIVE',1,'DEVICE','demo','demo',NOW() - INTERVAL 28 DAY,NOW() - INTERVAL 28 DAY,0),

('connector_demo_mes_sqlserver',
 'MES 系统 SQLServer 连接器',
 '演示-对接 MES 系统 SQLServer，查询生产工单数据',
 'DB',
 JSON_OBJECT(
   'url','jdbc:sqlserver://mes-db.example.com:1433;databaseName=MES;encrypt=true',
   'username','${MES_USER}','password','${MES_PASS}',
   'driverClassName','com.microsoft.sqlserver.jdbc.SQLServerDriver',
   'poolSize',3,
   'validationQuery','SELECT 1'
 ),
 'ACTIVE',1,'WORK_ORDER','demo','demo',NOW() - INTERVAL 23 DAY,NOW() - INTERVAL 23 DAY,0),

('connector_demo_sms_apikey',
 '短信网关 API Key 连接器',
 '演示-对接短信网关，API Key 认证，发送通知短信',
 'REST',
 JSON_OBJECT(
   'url','https://sms.example.com/api/v2/send','method','POST',
   'headers',JSON_OBJECT('Content-Type','application/json','X-API-Key','${SMS_API_KEY}'),
   'auth',JSON_OBJECT('type','API_KEY','headerName','X-API-Key','apiKey','${SMS_API_KEY}'),
   'body',JSON_OBJECT('phone','${phone}','message','${msg}'),
   'retry',JSON_OBJECT('maxAttempts',3,'waitMillis',300),
   'rateLimiter',JSON_OBJECT('limitForPeriod',20,'limitRefreshPeriodMillis',1000),
   'timeoutMillis',5000
 ),
 'ACTIVE',1,'CONTRACT','demo','demo',NOW() - INTERVAL 20 DAY,NOW() - INTERVAL 20 DAY,0),

('connector_demo_wechat_oauth2',
 '微信企业号 OAuth2 连接器',
 '演示-对接微信企业号，OAuth2 认证，推送企业消息',
 'REST',
 JSON_OBJECT(
   'url','https://qyapi.weixin.qq.com/cgi-bin/message/send','method','POST',
   'headers',JSON_OBJECT('Content-Type','application/json'),
   'auth',JSON_OBJECT('type','OAUTH2','tokenUrl','https://qyapi.weixin.qq.com/cgi-bin/gettoken','clientId','${WX_CORP_ID}','clientSecret','${WX_CORP_SECRET}','grantType','client_credentials'),
   'body',JSON_OBJECT('touser','${user}','msgtype','text','text',JSON_OBJECT('content','${content}')),
   'timeoutMillis',8000
 ),
 'ACTIVE',1,'WORK_ORDER','demo','demo',NOW() - INTERVAL 19 DAY,NOW() - INTERVAL 19 DAY,0),

('connector_demo_dingtalk_bearer',
 '钉钉机器人 Bearer 连接器',
 '演示-对接钉钉机器人，Bearer Token 认证，推送告警',
 'REST',
 JSON_OBJECT(
   'url','https://oapi.dingtalk.com/robot/send','method','POST',
   'headers',JSON_OBJECT('Content-Type','application/json'),
   'auth',JSON_OBJECT('type','BEARER','token','${DINGTALK_TOKEN}'),
   'body',JSON_OBJECT('msgtype','text','text',JSON_OBJECT('content','${text}')),
   'retry',JSON_OBJECT('maxAttempts',2,'waitMillis',500),
   'timeoutMillis',5000
 ),
 'ACTIVE',1,'SUPPLIER','demo','demo',NOW() - INTERVAL 25 DAY,NOW() - INTERVAL 25 DAY,0),

('connector_demo_pg_log',
 'PostgreSQL 日志库连接器',
 '演示-对接 PostgreSQL 日志库，查询操作日志',
 'DB',
 JSON_OBJECT(
   'url','jdbc:postgresql://log-db.example.com:5432/audit_log',
   'username','${LOG_USER}','password','${LOG_PASS}',
   'driverClassName','org.postgresql.Driver',
   'poolSize',5,
   'validationQuery','SELECT 1'
 ),
 'ACTIVE',1,'KB','demo','demo',NOW() - INTERVAL 17 DAY,NOW() - INTERVAL 17 DAY,0);

-- ---------------------------------------------------------------------
-- 第二部分：空白配置类表（tab/related_page/config_version/microflow_version/
--           process_binding/config_template/rule_test_case/datasource/
--           approval_chain/gray_release/backup_record 各 ≥10 条）
-- ---------------------------------------------------------------------

-- 2.1 标签页配置（11 条，多实体主从表/多 tab 切换场景）
INSERT IGNORE INTO `low_tab`
    (`code`, `name`, `description`, `tab_config`, `version`, `status`, `biz_type`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
VALUES
('tab_demo_employee_detail',
 '员工详情标签页',
 '演示-员工详情页多 tab 切换（基本信息/入职任务/合同）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','基本信息','pageCode','form_demo_employee','type','FORM'),
   JSON_OBJECT('title','入职任务','pageCode','list_demo_onboarding_task','type','LIST'),
   JSON_OBJECT('title','部门信息','pageCode','form_demo_department','type','FORM')
 )),
 1,'DRAFT','EMP_ONBOARDING','demo',NOW() - INTERVAL 30 DAY,'demo',NOW() - INTERVAL 30 DAY,0),

('tab_demo_device_detail',
 '设备详情标签页',
 '演示-设备详情页多 tab（基本信息/工单/巡检/维保）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','基本信息','pageCode','form_demo_device','type','FORM'),
   JSON_OBJECT('title','工单记录','pageCode','list_demo_work_order','type','LIST'),
   JSON_OBJECT('title','巡检记录','pageCode','list_demo_inspection','type','LIST'),
   JSON_OBJECT('title','维保计划','pageCode','list_demo_maintenance_plan','type','LIST')
 )),
 1,'DRAFT','DEVICE','demo',NOW() - INTERVAL 29 DAY,'demo',NOW() - INTERVAL 29 DAY,0),

('tab_demo_contract_detail',
 '合同详情标签页',
 '演示-合同详情页（基本信息/工单/付款记录）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','基本信息','pageCode','form_demo_contract','type','FORM'),
   JSON_OBJECT('title','关联合同工单','pageCode','list_demo_work_order','type','LIST')
 )),
 1,'DRAFT','CONTRACT','demo',NOW() - INTERVAL 25 DAY,'demo',NOW() - INTERVAL 25 DAY,0),

('tab_demo_customer_detail',
 '客户详情标签页',
 '演示-客户详情页（基本信息/合同/设备）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','基本信息','pageCode','form_demo_customer','type','FORM'),
   JSON_OBJECT('title','客户合同','pageCode','list_demo_contract','type','LIST'),
   JSON_OBJECT('title','客户设备','pageCode','list_demo_device','type','LIST')
 )),
 1,'DRAFT','CRM','demo',NOW() - INTERVAL 28 DAY,'demo',NOW() - INTERVAL 28 DAY,0),

('tab_demo_work_order_detail',
 '工单详情标签页',
 '演示-工单详情页（基本信息/处理记录/附件）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','基本信息','pageCode','form_demo_work_order','type','FORM'),
   JSON_OBJECT('title','设备信息','pageCode','form_demo_device','type','FORM'),
   JSON_OBJECT('title','合同信息','pageCode','form_demo_contract','type','FORM')
 )),
 1,'DRAFT','WORK_ORDER','demo',NOW() - INTERVAL 24 DAY,'demo',NOW() - INTERVAL 24 DAY,0),

('tab_demo_supplier_detail',
 '供应商详情标签页',
 '演示-供应商详情页（基本信息/供应设备/评级）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','基本信息','pageCode','form_demo_supplier','type','FORM'),
   JSON_OBJECT('title','供应设备','pageCode','list_demo_device','type','LIST')
 )),
 1,'DRAFT','SUPPLIER','demo',NOW() - INTERVAL 27 DAY,'demo',NOW() - INTERVAL 27 DAY,0),

('tab_demo_kb_article_detail',
 '知识库详情标签页',
 '演示-知识库文章详情（基本信息/评论/历史）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','文章信息','pageCode','form_demo_kb_article','type','FORM'),
   JSON_OBJECT('title','作者信息','pageCode','form_demo_employee','type','FORM')
 )),
 1,'DRAFT','KB','demo',NOW() - INTERVAL 18 DAY,'demo',NOW() - INTERVAL 18 DAY,0),

('tab_demo_maintenance_detail',
 '维保详情标签页',
 '演示-维保计划详情（基本信息/历史记录）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','计划信息','pageCode','form_demo_maintenance_plan','type','FORM'),
   JSON_OBJECT('title','设备信息','pageCode','form_demo_device','type','FORM'),
   JSON_OBJECT('title','巡检记录','pageCode','list_demo_inspection','type','LIST')
 )),
 1,'DRAFT','MAINTENANCE','demo',NOW() - INTERVAL 20 DAY,'demo',NOW() - INTERVAL 20 DAY,0),

('tab_demo_inspection_detail',
 '巡检详情标签页',
 '演示-巡检记录详情（基本信息/异常处理）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','巡检信息','pageCode','form_demo_inspection','type','FORM'),
   JSON_OBJECT('title','设备信息','pageCode','form_demo_device','type','FORM'),
   JSON_OBJECT('title','维保计划','pageCode','list_demo_maintenance_plan','type','LIST')
 )),
 1,'DRAFT','INSPECTION','demo',NOW() - INTERVAL 22 DAY,'demo',NOW() - INTERVAL 22 DAY,0),

('tab_demo_dashboard',
 '综合仪表盘标签页',
 '演示-综合仪表盘（设备/工单/合同概览）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','设备概览','pageCode','list_demo_device','type','LIST'),
   JSON_OBJECT('title','工单概览','pageCode','list_demo_work_order','type','LIST'),
   JSON_OBJECT('title','合同概览','pageCode','list_demo_contract','type','LIST'),
   JSON_OBJECT('title','客户概览','pageCode','list_demo_customer','type','LIST')
 )),
 1,'DRAFT','DASHBOARD','demo',NOW() - INTERVAL 15 DAY,'demo',NOW() - INTERVAL 15 DAY,0),

('tab_demo_employee_full',
 '员工完整信息标签页',
 '演示-员工完整页（档案/任务/部门/入职表单）',
 JSON_OBJECT('tabs', JSON_ARRAY(
   JSON_OBJECT('title','员工档案','pageCode','form_demo_employee','type','FORM'),
   JSON_OBJECT('title','入职任务','pageCode','list_demo_onboarding_task','type','LIST'),
   JSON_OBJECT('title','部门档案','pageCode','form_demo_department','type','FORM'),
   JSON_OBJECT('title','入职表单','pageCode','form_demo_onboarding_task','type','FORM')
 )),
 1,'DRAFT','EMP_ONBOARDING','demo',NOW() - INTERVAL 12 DAY,'demo',NOW() - INTERVAL 12 DAY,0);

-- 2.2 关联页配置（11 条）
INSERT IGNORE INTO `low_related_page`
    (`code`, `name`, `description`, `related_config`, `version`, `status`, `biz_type`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`)
VALUES
('related_demo_employee_tasks',
 '员工-入职任务关联页',
 '演示-员工详情页关联显示入职任务列表',
 JSON_OBJECT('sourceEntity','demo_employee','targetEntity','demo_onboarding_task','relationField','emp_id','targetPageCode','list_demo_onboarding_task','displayMode','inline'),
 1,'DRAFT','EMP_ONBOARDING','demo',NOW() - INTERVAL 30 DAY,'demo',NOW() - INTERVAL 30 DAY,0),

('related_demo_device_work_orders',
 '设备-工单关联页',
 '演示-设备详情页关联显示工单列表',
 JSON_OBJECT('sourceEntity','demo_device','targetEntity','demo_work_order','relationField','device_id','targetPageCode','list_demo_work_order','displayMode','inline'),
 1,'DRAFT','DEVICE','demo',NOW() - INTERVAL 29 DAY,'demo',NOW() - INTERVAL 29 DAY,0),

('related_demo_device_inspections',
 '设备-巡检关联页',
 '演示-设备详情页关联显示巡检记录',
 JSON_OBJECT('sourceEntity','demo_device','targetEntity','demo_inspection_record','relationField','device_id','targetPageCode','list_demo_inspection','displayMode','inline'),
 1,'DRAFT','DEVICE','demo',NOW() - INTERVAL 28 DAY,'demo',NOW() - INTERVAL 28 DAY,0),

('related_demo_device_maintenance',
 '设备-维保计划关联页',
 '演示-设备详情页关联显示维保计划',
 JSON_OBJECT('sourceEntity','demo_device','targetEntity','demo_maintenance_plan','relationField','device_id','targetPageCode','list_demo_maintenance_plan','displayMode','inline'),
 1,'DRAFT','DEVICE','demo',NOW() - INTERVAL 27 DAY,'demo',NOW() - INTERVAL 27 DAY,0),

('related_demo_contract_work_orders',
 '合同-工单关联页',
 '演示-合同详情页关联显示工单',
 JSON_OBJECT('sourceEntity','demo_contract','targetEntity','demo_work_order','relationField','contract_id','targetPageCode','list_demo_work_order','displayMode','inline'),
 1,'DRAFT','CONTRACT','demo',NOW() - INTERVAL 25 DAY,'demo',NOW() - INTERVAL 25 DAY,0),

('related_demo_customer_contracts',
 '客户-合同关联页',
 '演示-客户详情页关联显示合同',
 JSON_OBJECT('sourceEntity','demo_customer','targetEntity','demo_contract','relationField','customer_id','targetPageCode','list_demo_contract','displayMode','inline'),
 1,'DRAFT','CRM','demo',NOW() - INTERVAL 26 DAY,'demo',NOW() - INTERVAL 26 DAY,0),

('related_demo_customer_devices',
 '客户-设备关联页',
 '演示-客户详情页关联显示设备',
 JSON_OBJECT('sourceEntity','demo_customer','targetEntity','demo_device','relationField','owner_customer_id','targetPageCode','list_demo_device','displayMode','inline'),
 1,'DRAFT','CRM','demo',NOW() - INTERVAL 24 DAY,'demo',NOW() - INTERVAL 24 DAY,0),

('related_demo_supplier_devices',
 '供应商-设备关联页',
 '演示-供应商详情页关联显示供应设备',
 JSON_OBJECT('sourceEntity','demo_supplier','targetEntity','demo_device','relationField','supplier_id','targetPageCode','list_demo_device','displayMode','inline'),
 1,'DRAFT','SUPPLIER','demo',NOW() - INTERVAL 23 DAY,'demo',NOW() - INTERVAL 23 DAY,0),

('related_demo_employee_kb',
 '员工-知识库文章关联页',
 '演示-员工详情页关联显示其撰写的知识库文章',
 JSON_OBJECT('sourceEntity','demo_employee','targetEntity','demo_kb_article','relationField','author_emp_id','targetPageCode','list_demo_kb_article','displayMode','inline'),
 1,'DRAFT','KB','demo',NOW() - INTERVAL 18 DAY,'demo',NOW() - INTERVAL 18 DAY,0),

('related_demo_dept_employees',
 '部门-员工关联页',
 '演示-部门详情页关联显示部门员工',
 JSON_OBJECT('sourceEntity','demo_department','targetEntity','demo_employee','relationField','dept_id','targetPageCode','list_demo_employee','displayMode','inline'),
 1,'DRAFT','EMP_ONBOARDING','demo',NOW() - INTERVAL 20 DAY,'demo',NOW() - INTERVAL 20 DAY,0),

('related_demo_inspection_device',
 '巡检-设备关联页',
 '演示-巡检记录详情页关联显示设备信息',
 JSON_OBJECT('sourceEntity','demo_inspection_record','targetEntity','demo_device','relationField','device_id','targetPageCode','form_demo_device','displayMode','panel'),
 1,'DRAFT','INSPECTION','demo',NOW() - INTERVAL 17 DAY,'demo',NOW() - INTERVAL 17 DAY,0);

-- 2.3 配置版本快照（12 条，config_type 覆盖 FORM/LIST/ENTITY/MICROFLOW/RULE/CONNECTOR）
--     使用 INSERT...SELECT 引用已发布配置的 id，保证 config_id 正确
INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'FORM', id, code, 1, JSON_OBJECT('code',code,'name',name,'form_config',form_config,'version',version,'status',status),
       '初始版本发布', 'ACTIVE', 'DEV', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'FORM', id, code, 1, JSON_OBJECT('code',code,'name',name,'form_config',form_config,'version',version,'status',status),
       '设备档案表单初始版本', 'ACTIVE', 'DEV', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_form` WHERE code = 'form_demo_device';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'FORM', id, code, 1, JSON_OBJECT('code',code,'name',name,'form_config',form_config,'version',version,'status',status),
       '合同表单初始版本', 'ACTIVE', 'TEST', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_form` WHERE code = 'form_demo_contract';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'LIST', id, code, 1, JSON_OBJECT('code',code,'name',name,'list_config',list_config,'version',version,'status',status),
       '员工列表初始版本', 'ACTIVE', 'DEV', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_list` WHERE code = 'list_demo_employee';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'LIST', id, code, 1, JSON_OBJECT('code',code,'name',name,'list_config',list_config,'version',version,'status',status),
       '设备列表初始版本', 'ACTIVE', 'DEV', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_list` WHERE code = 'list_demo_device';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'ENTITY', id, code, 1, JSON_OBJECT('code',code,'name',name,'table_name',table_name,'biz_type',biz_type,'status',status),
       '员工实体初始版本', 'ACTIVE', 'DEV', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_entity` WHERE code = 'demo_employee';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'ENTITY', id, code, 1, JSON_OBJECT('code',code,'name',name,'table_name',table_name,'biz_type',biz_type,'status',status),
       '设备实体初始版本', 'ACTIVE', 'DEV', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_entity` WHERE code = 'demo_device';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'ENTITY', id, code, 1, JSON_OBJECT('code',code,'name',name,'table_name',table_name,'biz_type',biz_type,'status',status),
       '合同实体初始版本', 'ACTIVE', 'TEST', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_entity` WHERE code = 'demo_contract';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'MICROFLOW', id, code, 1, JSON_OBJECT('code',code,'name',name,'definition',definition,'version',version,'status',status),
       '入职处理微流初始版本', 'ACTIVE', 'DEV', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'MICROFLOW', id, code, 1, JSON_OBJECT('code',code,'name',name,'definition',definition,'version',version,'status',status),
       '合同审批微流初始版本', 'ACTIVE', 'TEST', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_microflow` WHERE code = 'microflow_demo_contract_approval_flow';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'RULE', id, code, 1, JSON_OBJECT('code',code,'name',name,'type',type,'definition',definition,'version',version,'status',status),
       '试用期决策表初始版本', 'ACTIVE', 'DEV', NULL, 'main', 'v1.0-init',
       create_time, update_time, 'demo-v59', 0
FROM `low_rule` WHERE code = 'rule_demo_probation_decision';

INSERT IGNORE INTO `low_config_version`
    (`config_type`, `config_id`, `config_code`, `version`, `snapshot`, `change_log`, `status`, `environment`, `parent_version_id`, `branch`, `tags`, `create_time`, `update_time`, `create_by`, `deleted`)
SELECT 'RULE', id, code, 1, JSON_OBJECT('code',code,'name',name,'type',type,'definition',definition,'version',version,'status',status),
       '合同折扣规则初始版本', 'ACTIVE', 'PROD', NULL, 'main', 'v1.0-release',
       create_time, update_time, 'demo-v59', 0
FROM `low_rule` WHERE code = 'rule_demo_contract_discount';

-- 2.4 微流版本历史（11 条，为每个微流生成版本快照）
INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '初始版本发布', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '设备状态变更微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_device_status_change';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '工单分配微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_work_order_assign';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '知识库发布通知微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_kb_publish_notify';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '合同审批微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_contract_approval_flow';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '巡检提醒微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_inspection_remind';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '维保任务生成微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_maintenance_generate';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '客户跟进同步微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_customer_followup';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '供应商评估微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_supplier_evaluate';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '合同到期提醒微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_contract_expire_remind';

INSERT IGNORE INTO `low_microflow_version`
    (`microflow_id`, `version`, `definition`, `change_log`, `status`, `create_by`, `create_time`)
SELECT id, 1, definition, '财务二审子微流初始版本', 'PUBLISHED', 'demo-v59', create_time
FROM `low_microflow` WHERE code = 'microflow_demo_finance_review';

-- 2.5 流程绑定（11 条，process_definition_key 唯一，含 task_callbacks）
INSERT IGNORE INTO `low_process_binding`
    (`process_definition_key`, `process_definition_name`, `node_form_bindings`, `task_callbacks`, `status`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
VALUES
('demo_onboarding_approval',
 '员工入职审批流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','hr_review','formCode','form_demo_employee','microflowCode','microflow_demo_onboarding'),JSON_OBJECT('nodeId','manager_review','formCode','form_demo_employee')),
 JSON_OBJECT('hr_review',JSON_OBJECT('onCreate','microflow_demo_onboarding','onComplete','microflow_demo_onboarding')),
 'ACTIVE','demo','demo',NOW() - INTERVAL 30 DAY,NOW() - INTERVAL 30 DAY,0),

('demo_contract_approval',
 '合同审批流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','submit','formCode','form_demo_contract'),JSON_OBJECT('nodeId','finance_review','formCode','form_demo_contract','microflowCode','microflow_demo_contract_approval_flow'),JSON_OBJECT('nodeId','manager_approve','formCode','form_demo_contract')),
 JSON_OBJECT('finance_review',JSON_OBJECT('onCreate','microflow_demo_finance_review')),
 'ACTIVE','demo','demo',NOW() - INTERVAL 25 DAY,NOW() - INTERVAL 25 DAY,0),

('demo_kb_publish_process',
 '知识库发布流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','author_submit','formCode','form_demo_kb_article'),JSON_OBJECT('nodeId','reviewer_review','formCode','form_demo_kb_article','microflowCode','microflow_demo_kb_publish_notify')),
 JSON_OBJECT('reviewer_review',JSON_OBJECT('onComplete','microflow_demo_kb_publish_notify')),
 'ACTIVE','demo','demo',NOW() - INTERVAL 18 DAY,NOW() - INTERVAL 18 DAY,0),

('demo_maintenance_generate_process',
 '维保任务生成流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','generate','formCode','form_demo_maintenance_plan','microflowCode','microflow_demo_maintenance_generate'),JSON_OBJECT('nodeId','confirm','formCode','form_demo_maintenance_plan')),
 JSON_OBJECT('generate',JSON_OBJECT('onCreate','microflow_demo_maintenance_generate')),
 'ACTIVE','demo','demo',NOW() - INTERVAL 19 DAY,NOW() - INTERVAL 19 DAY,0),

('demo_customer_complaint_process',
 '客户投诉处理流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','register','formCode','form_demo_customer'),JSON_OBJECT('nodeId','investigate','formCode','form_demo_customer'),JSON_OBJECT('nodeId','resolve','formCode','form_demo_customer')),
 NULL,
 'ACTIVE','demo','demo',NOW() - INTERVAL 15 DAY,NOW() - INTERVAL 15 DAY,0),

('demo_work_order_escalation_process',
 '工单升级处理流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','escalate','formCode','form_demo_work_order','microflowCode','microflow_demo_work_order_assign'),JSON_OBJECT('nodeId','manager_review','formCode','form_demo_work_order')),
 JSON_OBJECT('escalate',JSON_OBJECT('onCreate','microflow_demo_work_order_assign')),
 'ACTIVE','demo','demo',NOW() - INTERVAL 13 DAY,NOW() - INTERVAL 13 DAY,0),

('demo_employee_leave_process',
 '员工离职流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','submit','formCode','form_demo_employee'),JSON_OBJECT('nodeId','hr_review','formCode','form_demo_employee'),JSON_OBJECT('nodeId','manager_approve','formCode','form_demo_employee')),
 NULL,
 'ACTIVE','demo','demo',NOW() - INTERVAL 22 DAY,NOW() - INTERVAL 22 DAY,0),

('demo_device_scrap_process',
 '设备报废流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','apply','formCode','form_demo_device'),JSON_OBJECT('nodeId','manager_approve','formCode','form_demo_device')),
 NULL,
 'ACTIVE','demo','demo',NOW() - INTERVAL 20 DAY,NOW() - INTERVAL 20 DAY,0),

('demo_supplier_onboarding_process',
 '供应商入驻流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','submit','formCode','form_demo_supplier'),JSON_OBJECT('nodeId','procurement_review','formCode','form_demo_supplier','microflowCode','microflow_demo_supplier_evaluate'),JSON_OBJECT('nodeId','manager_approve','formCode','form_demo_supplier')),
 JSON_OBJECT('procurement_review',JSON_OBJECT('onComplete','microflow_demo_supplier_evaluate')),
 'ACTIVE','demo','demo',NOW() - INTERVAL 17 DAY,NOW() - INTERVAL 17 DAY,0),

('demo_work_order_approval',
 '工单审批流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','submit','formCode','form_demo_work_order'),JSON_OBJECT('nodeId','engineer_handle','formCode','form_demo_work_order','microflowCode','microflow_demo_work_order_assign'),JSON_OBJECT('nodeId','verify','formCode','form_demo_work_order')),
 JSON_OBJECT('engineer_handle',JSON_OBJECT('onCreate','microflow_demo_work_order_assign')),
 'ACTIVE','demo','demo',NOW() - INTERVAL 16 DAY,NOW() - INTERVAL 16 DAY,0),

('demo_kb_review_process',
 '知识库审核流程',
 JSON_ARRAY(JSON_OBJECT('nodeId','author_submit','formCode','form_demo_kb_article'),JSON_OBJECT('nodeId','reviewer_review','formCode','form_demo_kb_article'),JSON_OBJECT('nodeId','publisher_publish','formCode','form_demo_kb_article','microflowCode','microflow_demo_kb_publish_notify')),
 JSON_OBJECT('publisher_publish',JSON_OBJECT('onComplete','microflow_demo_kb_publish_notify')),
 'ACTIVE','demo','demo',NOW() - INTERVAL 14 DAY,NOW() - INTERVAL 14 DAY,0);

-- 2.6 配置模板市场（11 条，含表单/列表/微流/规则模板，status=PUBLISHED）
INSERT IGNORE INTO `low_config_template`
    (`code`, `name`, `config_type`, `category`, `config_json`, `thumbnail`, `description`, `author`, `tags`, `status`, `download_count`, `rating`, `rating_count`, `version`, `parameters`, `create_time`, `update_time`)
VALUES
('tpl_employee_form',
 '员工档案表单模板',
 'FORM','人事管理',
 JSON_OBJECT('title','员工档案','fields',JSON_ARRAY('emp_no','name','gender','dept_id','position')),
 '/static/thumbnails/employee-form.png',
 '标准员工档案录入表单模板，含工号/姓名/部门/职位等核心字段',
 'platform','人事,表单,员工','PUBLISHED',356,4.7,42,'1.2.0',
 JSON_OBJECT('fields',JSON_ARRAY(JSON_OBJECT('name','deptOptions','label','部门选项','type','array'))),
 NOW() - INTERVAL 40 DAY,NOW() - INTERVAL 10 DAY),

('tpl_device_form',
 '设备台账表单模板',
 'FORM','设备管理',
 JSON_OBJECT('title','设备台账','fields',JSON_ARRAY('device_code','device_name','model','supplier_id','status')),
 '/static/thumbnails/device-form.png',
 '设备资产台账录入表单模板，含编号/型号/供应商/状态',
 'platform','设备,表单,资产','PUBLISHED',289,4.5,31,'1.1.0',
 NULL,
 NOW() - INTERVAL 38 DAY,NOW() - INTERVAL 12 DAY),

('tpl_contract_form',
 '合同录入表单模板',
 'FORM','合同管理',
 JSON_OBJECT('title','合同录入','fields',JSON_ARRAY('contract_no','customer_id','amount','sign_date','expire_date')),
 '/static/thumbnails/contract-form.png',
 '合同录入表单模板，含金额/客户/到期日/审批流绑定',
 'platform','合同,表单,审批','PUBLISHED',412,4.8,55,'1.3.0',
 JSON_OBJECT('fields',JSON_ARRAY(JSON_OBJECT('name','approvalProcess','label','审批流程','type','string'))),
 NOW() - INTERVAL 36 DAY,NOW() - INTERVAL 8 DAY),

('tpl_employee_list',
 '员工列表模板',
 'LIST','人事管理',
 JSON_OBJECT('title','员工列表','columns',JSON_ARRAY('emp_no','name','dept_id','status'),'pagination',JSON_OBJECT('pageSize',10)),
 '/static/thumbnails/employee-list.png',
 '标准员工列表模板，含搜索/筛选/分页/操作列',
 'platform','列表,员工,人事','PUBLISHED',234,4.3,28,'1.0.0',
 NULL,
 NOW() - INTERVAL 34 DAY,NOW() - INTERVAL 14 DAY),

('tpl_work_order_list',
 '工单列表模板',
 'LIST','工单管理',
 JSON_OBJECT('title','工单列表','columns',JSON_ARRAY('work_order_no','priority','status','assignee')),
 '/static/thumbnails/work-order-list.png',
 '售后工单列表模板，含优先级标签/状态筛选/工程师指派',
 'platform','列表,工单,售后','PUBLISHED',198,4.4,22,'1.1.0',
 NULL,
 NOW() - INTERVAL 32 DAY,NOW() - INTERVAL 9 DAY),

('tpl_approval_microflow',
 '合同审批微流模板',
 'MICROFLOW','流程编排',
 JSON_OBJECT('nodes',JSON_ARRAY('START','IF','CALL_MICROFLOW','END')),
 '/static/thumbnails/approval-microflow.png',
 '合同审批微流模板：金额判断→分支审批→结束',
 'platform','微流,审批,合同','PUBLISHED',167,4.6,19,'1.2.0',
 JSON_OBJECT('fields',JSON_ARRAY(JSON_OBJECT('name','threshold','label','金额阈值','type','number','default',100000))),
 NOW() - INTERVAL 30 DAY,NOW() - INTERVAL 5 DAY),

('tpl_notify_microflow',
 '通知发送微流模板',
 'MICROFLOW','流程编排',
 JSON_OBJECT('nodes',JSON_ARRAY('START','LOOP','CALL_CONNECTOR','END')),
 '/static/thumbnails/notify-microflow.png',
 '批量通知发送微流模板：循环遍历→调用连接器发送',
 'platform','微流,通知,批量','PUBLISHED',143,4.2,16,'1.0.0',
 NULL,
 NOW() - INTERVAL 28 DAY,NOW() - INTERVAL 7 DAY),

('tpl_discount_rule',
 '合同折扣规则模板',
 'RULE','规则引擎',
 JSON_OBJECT('type','EXPRESSION','definition','amount >= 100000 ? 0.85 : 1.0'),
 '/static/thumbnails/discount-rule.png',
 '合同折扣计算表达式规则模板，阶梯折扣',
 'platform','规则,折扣,合同','PUBLISHED',176,4.5,24,'1.1.0',
 NULL,
 NOW() - INTERVAL 26 DAY,NOW() - INTERVAL 6 DAY),

('tpl_priority_rule',
 '工单优先级规则模板',
 'RULE','规则引擎',
 JSON_OBJECT('type','EXPRESSION','definition','fault_type == "HARDWARE" ? "HIGH" : "MEDIUM"'),
 '/static/thumbnails/priority-rule.png',
 '工单优先级判定规则模板，按故障类型分流',
 'platform','规则,工单,优先级','PUBLISHED',132,4.3,18,'1.0.0',
 NULL,
 NOW() - INTERVAL 24 DAY,NOW() - INTERVAL 4 DAY),

('tpl_decision_table_rule',
 '状态转换决策表模板',
 'RULE','规则引擎',
 JSON_OBJECT('type','DECISION_TABLE','hitPolicy','FIRST'),
 '/static/thumbnails/decision-table.png',
 '通用状态转换决策表模板，可配置状态机',
 'platform','规则,决策表,状态机','PUBLISHED',98,4.1,12,'1.0.0',
 NULL,
 NOW() - INTERVAL 20 DAY,NOW() - INTERVAL 3 DAY),

('tpl_crm_connector',
 'CRM 连接器模板',
 'CONNECTOR','集成对接',
 JSON_OBJECT('type','REST','auth',JSON_OBJECT('type','OAUTH2')),
 '/static/thumbnails/crm-connector.png',
 'CRM 系统 OAuth2 连接器模板，含令牌刷新/重试/限流',
 'platform','连接器,CRM,OAuth2','PUBLISHED',87,4.4,10,'1.0.0',
 NULL,
 NOW() - INTERVAL 18 DAY,NOW() - INTERVAL 2 DAY);

-- 2.7 规则测试用例（11 条，含 PASS/FAIL 场景）
--     先清理 V59 旧数据保证幂等，再用 INSERT...SELECT 引用规则 id
DELETE FROM `low_rule_test_case` WHERE `create_by` = 'demo-v59';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, 'P1职级试用期=1个月', '验证 P1 职级返回 1 个月试用期',
       '{"level":"P1"}', '{"probation_months":1}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 12 DAY, 'demo-v59', NOW() - INTERVAL 12 DAY, '正常场景'
FROM `low_rule` WHERE code = 'rule_demo_probation_decision';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, 'P5职级试用期=6个月', '验证 P5 职级返回 6 个月试用期',
       '{"level":"P5"}', '{"probation_months":6}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 11 DAY, 'demo-v59', NOW() - INTERVAL 11 DAY, '正常场景'
FROM `low_rule` WHERE code = 'rule_demo_probation_decision';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, '12万合同折扣=0.95', '验证 12 万金额返回 0.95 折扣',
       '{"amount":120000}', '{"discount":0.95}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 10 DAY, 'demo-v59', NOW() - INTERVAL 10 DAY, '阶梯折扣场景'
FROM `low_rule` WHERE code = 'rule_demo_contract_discount';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, '60万合同折扣=0.90', '验证 60 万金额返回 0.90 折扣',
       '{"amount":600000}', '{"discount":0.90}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 9 DAY, 'demo-v59', NOW() - INTERVAL 9 DAY, '阶梯折扣场景'
FROM `low_rule` WHERE code = 'rule_demo_contract_discount';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, '硬件故障+A级客户=紧急', '验证硬件故障且 A 级客户返回 URGENT',
       '{"fault_type":"HARDWARE","customer_level":"A"}', '{"priority":"URGENT"}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 8 DAY, 'demo-v59', NOW() - INTERVAL 8 DAY, '组合条件场景'
FROM `low_rule` WHERE code = 'rule_demo_work_order_priority';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, '80度温度=需维修', '验证温度 80 度返回 NEED_REPAIR',
       '{"temperature":80,"vibration":2.0}', '{"result":"NEED_REPAIR"}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 7 DAY, 'demo-v59', NOW() - INTERVAL 7 DAY, '高温告警场景'
FROM `low_rule` WHERE code = 'rule_demo_inspection_judge';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, 'IDLE+启用=IN_USE', '验证闲置状态启用后转为在用',
       '{"current_status":"IDLE","action":"ENABLE"}', '{"next_status":"IN_USE","allowed":true}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 6 DAY, 'demo-v59', NOW() - INTERVAL 6 DAY, '状态转换场景'
FROM `low_rule` WHERE code = 'rule_demo_device_status_transition';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, '服务器+高重要=月度维保', '验证服务器高重要等级返回月度维保',
       '{"device_type":"SERVER","importance":"HIGH"}', '{"cycle":"MONTHLY"}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 5 DAY, 'demo-v59', NOW() - INTERVAL 5 DAY, '维保周期场景'
FROM `low_rule` WHERE code = 'rule_demo_maintenance_cycle';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, '5千合同无折扣', '验证 5 千金额返回无折扣（1.0）',
       '{"amount":5000}', '{"discount":1.0}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 4 DAY, 'demo-v59', NOW() - INTERVAL 4 DAY, '低金额场景'
FROM `low_rule` WHERE code = 'rule_demo_contract_discount';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, '资产折旧计算验证', '验证原值10万/残值5%/5年=年折旧1.9万',
       '{"original_value":100000,"residual_rate":0.05,"useful_life":5}', '{"annual_depreciation":19000}', 'EQUALS', 1,
       'demo-v59', NOW() - INTERVAL 3 DAY, 'demo-v59', NOW() - INTERVAL 3 DAY, '折旧计算场景'
FROM `low_rule` WHERE code = 'rule_demo_asset_depreciation';

INSERT INTO `low_rule_test_case`
    (`rule_id`, `rule_code`, `name`, `description`, `input_json`, `expected_output_json`, `assertion_mode`, `enabled`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT id, code, '未知职级预期失败', '验证未知职级 P9 不在决策表覆盖范围（预期失败）',
       '{"level":"P9"}', '{"probation_months":0}', 'NOT_NULL', 1,
       'demo-v59', NOW() - INTERVAL 2 DAY, 'demo-v59', NOW() - INTERVAL 2 DAY, 'FAIL 场景-边界测试'
FROM `low_rule` WHERE code = 'rule_demo_probation_decision';

-- 2.8 数据源配置（11 条，DIRECT/REPLICA/FEDERATED 模式，MySQL/PostgreSQL/SQLServer）
INSERT IGNORE INTO `low_datasource`
    (`code`, `name`, `db_type`, `integration_mode`, `url`, `username`, `password`, `driver_class_name`, `pool_size`, `status`, `linked_entity_code`, `sync_config`, `description`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
('ds_demo_main_mysql',
 '主数据源-MySQL',
 'mysql','DIRECT',
 'jdbc:mysql://main-db.example.com:3306/dppms_d365?useSSL=true&characterEncoding=utf8',
 'pms_app','ENC(abc123def456==)','com.mysql.cj.jdbc.Driver',10,
 'ACTIVE',NULL,NULL,
 '主业务数据源（直连 MySQL）',
 'demo',NOW() - INTERVAL 30 DAY,'demo',NOW() - INTERVAL 30 DAY,'直连模式'),

('ds_demo_readonly_mysql',
 '只读分析库-MySQL',
 'mysql','DIRECT',
 'jdbc:mysql://analytics-db.example.com:3306/report_db?useSSL=true',
 'report_user','ENC(xyz789==)','com.mysql.cj.jdbc.Driver',5,
 'ACTIVE',NULL,NULL,
 '只读分析库（直连 MySQL，报表查询）',
 'demo',NOW() - INTERVAL 28 DAY,'demo',NOW() - INTERVAL 28 DAY,'直连只读'),

('ds_demo_mes_sqlserver',
 'MES系统-SQLServer',
 'sqlserver','DIRECT',
 'jdbc:sqlserver://mes-db.example.com:1433;databaseName=MES;encrypt=true',
 'mes_user','ENC(mes123==)','com.microsoft.sqlserver.jdbc.SQLServerDriver',3,
 'ACTIVE',NULL,NULL,
 'MES 系统 SQLServer 数据源（直连，查询生产工单）',
 'demo',NOW() - INTERVAL 27 DAY,'demo',NOW() - INTERVAL 27 DAY,'直连 SQLServer'),

('ds_demo_sap_sqlserver',
 'SAP系统-SQLServer',
 'sqlserver','DIRECT',
 'jdbc:sqlserver://sap-db.example.com:1433;databaseName=SAP;encrypt=true',
 'sap_user','ENC(sap456==)','com.microsoft.sqlserver.jdbc.SQLServerDriver',3,
 'ACTIVE',NULL,NULL,
 'SAP 系统 SQLServer 数据源（直连，查询订单数据）',
 'demo',NOW() - INTERVAL 26 DAY,'demo',NOW() - INTERVAL 26 DAY,'直连 SQLServer'),

('ds_demo_pg_log',
 '日志库-PostgreSQL',
 'postgresql','DIRECT',
 'jdbc:postgresql://log-db.example.com:5432/audit_log',
 'log_user','ENC(log789==)','org.postgresql.Driver',5,
 'ACTIVE',NULL,NULL,
 'PostgreSQL 日志库（直连，审计日志查询）',
 'demo',NOW() - INTERVAL 25 DAY,'demo',NOW() - INTERVAL 25 DAY,'直连 PostgreSQL'),

('ds_demo_d365_replica',
 'D365副本-MySQL',
 'mysql','REPLICA',
 'jdbc:mysql://d365-replica.example.com:3306/d365_sync?useSSL=true',
 'd365_sync','ENC(d365abc==)','com.mysql.cj.jdbc.Driver',5,
 'ACTIVE','demo_contract',
 JSON_OBJECT('syncMode','INCREMENTAL','cron','0 0 * * * ?','fields',JSON_ARRAY('contract_no','amount','customer_id')),
 'D365 合同数据副本（增量同步）',
 'demo',NOW() - INTERVAL 24 DAY,'demo',NOW() - INTERVAL 24 DAY,'副本模式-合同同步'),

('ds_demo_crm_replica',
 'CRM客户副本-MySQL',
 'mysql','REPLICA',
 'jdbc:mysql://crm-replica.example.com:3306/crm_sync?useSSL=true',
 'crm_sync','ENC(crmxyz==)','com.mysql.cj.jdbc.Driver',5,
 'ACTIVE','demo_customer',
 JSON_OBJECT('syncMode','FULL','cron','0 0 2 * * ?','fields',JSON_ARRAY('customer_code','customer_name','level')),
 'CRM 客户数据副本（全量同步，每日凌晨）',
 'demo',NOW() - INTERVAL 23 DAY,'demo',NOW() - INTERVAL 23 DAY,'副本模式-客户全量同步'),

('ds_demo_device_replica',
 '设备台账副本-PostgreSQL',
 'postgresql','REPLICA',
 'jdbc:postgresql://device-replica.example.com:5432/device_sync',
 'device_sync','ENC(dev123==)','org.postgresql.Driver',3,
 'ACTIVE','demo_device',
 JSON_OBJECT('syncMode','INCREMENTAL','cron','0 30 * * * ?','fields',JSON_ARRAY('device_code','device_name','status')),
 '设备台账副本（PostgreSQL，每小时增量同步）',
 'demo',NOW() - INTERVAL 22 DAY,'demo',NOW() - INTERVAL 22 DAY,'副本模式-设备增量同步'),

('ds_demo_federated_order',
 '联邦订单库-SQLServer',
 'sqlserver','FEDERATED',
 'jdbc:sqlserver://federated-db.example.com:1433;databaseName=ORDER_HUB;encrypt=true',
 'fed_user','ENC(fed456==)','com.microsoft.sqlserver.jdbc.SQLServerDriver',5,
 'ACTIVE',NULL,
 JSON_OBJECT('federatedTables',JSON_ARRAY('demo_contract','demo_work_order'),'joinMode','VIEW'),
 '联邦订单库（SQLServer，整合合同与工单视图）',
 'demo',NOW() - INTERVAL 21 DAY,'demo',NOW() - INTERVAL 21 DAY,'联邦模式-订单整合'),

('ds_demo_federated_analytics',
 '联邦分析库-MySQL',
 'mysql','FEDERATED',
 'jdbc:mysql://federated-analytics.example.com:3306/analytics_hub?useSSL=true',
 'analytics_user','ENC(ana789==)','com.mysql.cj.jdbc.Driver',5,
 'ACTIVE',NULL,
 JSON_OBJECT('federatedTables',JSON_ARRAY('demo_device','demo_inspection_record'),'joinMode','VIEW'),
 '联邦分析库（MySQL，整合设备与巡检数据）',
 'demo',NOW() - INTERVAL 20 DAY,'demo',NOW() - INTERVAL 20 DAY,'联邦模式-分析整合'),

('ds_demo_oracle_legacy',
 '遗留系统-Oracle',
 'oracle','DIRECT',
 'jdbc:oracle:thin:@legacy-db.example.com:1521:LEGACY',
 'legacy_user','ENC(leg123==)','oracle.jdbc.OracleDriver',2,
 'INACTIVE',NULL,NULL,
 '遗留 Oracle 系统（直连，仅历史查询，已停用）',
 'demo',NOW() - INTERVAL 35 DAY,'demo',NOW() - INTERVAL 15 DAY,'直连 Oracle-已停用');

-- 2.9 多级审批链（11 条，不同 configType，2-4 级审批）
--     先清理 V59 旧数据（按 name LIKE 'V59-%'），保证幂等
DELETE FROM `low_approval_chain` WHERE `name` LIKE 'V59-%';

INSERT INTO `low_approval_chain`
    (`config_type`, `name`, `levels`, `enabled`, `create_time`, `update_time`)
VALUES
('FORM',
 'V59-表单发布三级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_editor','name','编辑人自审'),
   JSON_OBJECT('level',2,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',3,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY),

('FORM',
 'V59-表单发布两级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',2,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 28 DAY),

('LIST',
 'V59-列表发布三级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_editor','name','编辑人自审'),
   JSON_OBJECT('level',2,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',3,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 27 DAY, NOW() - INTERVAL 27 DAY),

('ENTITY',
 'V59-实体发布四级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_editor','name','编辑人自审'),
   JSON_OBJECT('level',2,'approverRole','dba','name','DBA审核'),
   JSON_OBJECT('level',3,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',4,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 26 DAY),

('ENTITY',
 'V59-实体发布两级快速审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',2,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY),

('MICROFLOW',
 'V59-微流发布三级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_editor','name','编辑人自审'),
   JSON_OBJECT('level',2,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',3,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 24 DAY, NOW() - INTERVAL 24 DAY),

('MICROFLOW',
 'V59-微流发布两级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',2,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY),

('RULE',
 'V59-规则发布三级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_editor','name','编辑人自审'),
   JSON_OBJECT('level',2,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',3,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 22 DAY),

('CONNECTOR',
 'V59-连接器发布四级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_editor','name','编辑人自审'),
   JSON_OBJECT('level',2,'approverRole','security_reviewer','name','安全评审'),
   JSON_OBJECT('level',3,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',4,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 21 DAY),

('CONNECTOR',
 'V59-连接器发布两级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','security_reviewer','name','安全评审'),
   JSON_OBJECT('level',2,'approverRole','admin','name','管理员审批')
 ),
 1, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY),

('TAB',
 'V59-标签页发布三级审批链',
 JSON_ARRAY(
   JSON_OBJECT('level',1,'approverRole','config_editor','name','编辑人自审'),
   JSON_OBJECT('level',2,'approverRole','config_reviewer','name','配置评审'),
   JSON_OBJECT('level',3,'approverRole','admin','name','管理员审批')
 ),
 0, NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 19 DAY);

-- 2.10 灰度发布策略（11 条，不同 percentage/白名单/status）
--      先清理 V59 旧数据，再用 INSERT...SELECT 引用配置 id
DELETE FROM `low_gray_release` WHERE `create_by` = 'demo-v59';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'FORM', id, code, 2, NULL, 100, NULL, 'FULL',
       NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 10 DAY, NULL, 'demo-v59', NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 10 DAY
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'FORM', id, code, 2, NULL, 50, JSON_ARRAY('tenant_001','tenant_002'), 'GRAYING',
       NOW() - INTERVAL 5 DAY, NULL, NULL, 'demo-v59', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 1 DAY
FROM `low_form` WHERE code = 'form_demo_device';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'FORM', id, code, 2, NULL, 30, JSON_ARRAY('tenant_003'), 'GRAYING',
       NOW() - INTERVAL 3 DAY, NULL, NULL, 'demo-v59', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY
FROM `low_form` WHERE code = 'form_demo_contract';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'LIST', id, code, 2, NULL, 100, NULL, 'FULL',
       NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 8 DAY, NULL, 'demo-v59', NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 8 DAY
FROM `low_list` WHERE code = 'list_demo_employee';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'LIST', id, code, 2, NULL, 20, JSON_ARRAY('tenant_001','tenant_004'), 'GRAYING',
       NOW() - INTERVAL 4 DAY, NULL, NULL, 'demo-v59', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 2 DAY
FROM `low_list` WHERE code = 'list_demo_device';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'LIST', id, code, 2, NULL, 10, JSON_ARRAY('tenant_005'), 'ROLLED_BACK',
       NOW() - INTERVAL 15 DAY, NULL, NOW() - INTERVAL 10 DAY, 'demo-v59', NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 10 DAY
FROM `low_list` WHERE code = 'list_demo_contract';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'MICROFLOW', id, code, 2, NULL, 100, NULL, 'FULL',
       NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 6 DAY, NULL, 'demo-v59', NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 6 DAY
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'MICROFLOW', id, code, 2, NULL, 50, JSON_ARRAY('tenant_002','tenant_006'), 'GRAYING',
       NOW() - INTERVAL 2 DAY, NULL, NULL, 'demo-v59', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY
FROM `low_microflow` WHERE code = 'microflow_demo_contract_approval_flow';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'MICROFLOW', id, code, 2, NULL, 5, JSON_ARRAY('tenant_test'), 'ROLLED_BACK',
       NOW() - INTERVAL 12 DAY, NULL, NOW() - INTERVAL 8 DAY, 'demo-v59', NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 8 DAY
FROM `low_microflow` WHERE code = 'microflow_demo_work_order_assign';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'RULE', id, code, 2, NULL, 100, NULL, 'FULL',
       NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 4 DAY, NULL, 'demo-v59', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 4 DAY
FROM `low_rule` WHERE code = 'rule_demo_probation_decision';

INSERT INTO `low_gray_release`
    (`config_type`, `config_id`, `config_code`, `version`, `publish_record_id`, `gray_percentage`, `tenant_whitelist`, `status`, `gray_started_at`, `full_released_at`, `rolled_back_at`, `create_by`, `create_time`, `update_time`)
SELECT 'RULE', id, code, 2, NULL, 30, JSON_ARRAY('tenant_007'), 'GRAYING',
       NOW() - INTERVAL 1 DAY, NULL, NULL, 'demo-v59', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY
FROM `low_rule` WHERE code = 'rule_demo_contract_discount';

-- 2.11 备份记录（11 条，FULL/INCREMENTAL）
--      先清理 V59 旧数据（按 operator='demo-v59'），保证幂等
DELETE FROM `low_backup_record` WHERE `operator` = 'demo-v59';

INSERT INTO `low_backup_record`
    (`type`, `scope`, `file_path`, `file_size`, `status`, `operator`, `backup_time`, `expire_at`, `create_time`, `update_time`)
VALUES
('FULL', NULL,
 '/backup/v59/full_20260613.sql.gz', 52428800, 'SUCCESS', 'demo-v59',
 NOW() - INTERVAL 30 DAY, NOW() + INTERVAL 60 DAY, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY),

('INCREMENTAL', 'demo_employee',
 '/backup/v59/inc_demo_employee_20260620.sql.gz', 1048576, 'SUCCESS', 'demo-v59',
 NOW() - INTERVAL 23 DAY, NOW() + INTERVAL 30 DAY, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY),

('INCREMENTAL', 'demo_device',
 '/backup/v59/inc_demo_device_20260625.sql.gz', 2097152, 'SUCCESS', 'demo-v59',
 NOW() - INTERVAL 18 DAY, NOW() + INTERVAL 30 DAY, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY),

('FULL', NULL,
 '/backup/v59/full_20260701.sql.gz', 56623104, 'SUCCESS', 'demo-v59',
 NOW() - INTERVAL 12 DAY, NOW() + INTERVAL 60 DAY, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY),

('INCREMENTAL', 'demo_contract',
 '/backup/v59/inc_demo_contract_20260705.sql.gz', 524288, 'SUCCESS', 'demo-v59',
 NOW() - INTERVAL 8 DAY, NOW() + INTERVAL 30 DAY, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY),

('INCREMENTAL', 'demo_customer',
 '/backup/v59/inc_demo_customer_20260708.sql.gz', 786432, 'SUCCESS', 'demo-v59',
 NOW() - INTERVAL 5 DAY, NOW() + INTERVAL 30 DAY, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY),

('FULL', NULL,
 '/backup/v59/full_20260710.sql.gz', 59801600, 'SUCCESS', 'demo-v59',
 NOW() - INTERVAL 3 DAY, NOW() + INTERVAL 60 DAY, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),

('INCREMENTAL', 'demo_work_order',
 '/backup/v59/inc_demo_work_order_20260711.sql.gz', 1310720, 'SUCCESS', 'demo-v59',
 NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 30 DAY, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

('INCREMENTAL', 'demo_kb_article',
 '/backup/v59/inc_demo_kb_article_20260712_failed.sql.gz', 0, 'FAILED', 'demo-v59',
 NOW() - INTERVAL 1 DAY, NULL, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),

('FULL', NULL,
 '/backup/v59/full_20260713_running.sql.gz', 0, 'RUNNING', 'demo-v59',
 NOW(), NOW() + INTERVAL 60 DAY, NOW(), NOW()),

('INCREMENTAL', 'demo_supplier',
 '/backup/v59/inc_demo_supplier_20260710.sql.gz', 655360, 'SUCCESS', 'demo-v59',
 NOW() - INTERVAL 3 DAY, NOW() + INTERVAL 30 DAY, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY);

-- ---------------------------------------------------------------------
-- 第三部分：流程协作类表（publish_record/comment/config_audit_log 各 ≥10 条）
-- ---------------------------------------------------------------------

-- 3.1 发布记录（11 条，覆盖 DRAFT/SUBMITTED/APPROVED/REJECTED/PUBLISHED 全状态流转）
--     先清理 V59 旧数据，再用 INSERT...SELECT 引用配置 id
DELETE FROM `low_publish_record` WHERE `applicant` = 'demo-v59';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'FORM', id, code, 2, 'PUBLISHED', 3, NULL, 1, 'demo-v59', 2, '管理员', '员工档案表单 v2 发布：新增试用期结束字段', NULL,
       NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 20 DAY
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'FORM', id, code, 2, 'SUBMITTED', 1, NULL, 1, 'demo-v59', NULL, NULL, '设备档案表单 v2 提交发布：新增状态字段', NULL,
       NOW() - INTERVAL 2 DAY, NULL, NULL, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY
FROM `low_form` WHERE code = 'form_demo_device';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'FORM', id, code, 2, 'APPROVED', 2, NULL, 1, 'demo-v59', 2, '管理员', '合同表单 v2 审批通过：金额字段增加校验', NULL,
       NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 1 DAY, NULL, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 1 DAY
FROM `low_form` WHERE code = 'form_demo_contract';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'LIST', id, code, 2, 'PUBLISHED', 2, NULL, 1, 'demo-v59', 2, '管理员', '员工列表 v2 发布：新增状态标签', NULL,
       NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 15 DAY
FROM `low_list` WHERE code = 'list_demo_employee';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'LIST', id, code, 2, 'REJECTED', NULL, NULL, 1, 'demo-v59', 2, '管理员', '设备列表 v2 提交', '筛选条件设计不合理，缺少供应商筛选', 
       NOW() - INTERVAL 4 DAY, NULL, NULL, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY
FROM `low_list` WHERE code = 'list_demo_device';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'MICROFLOW', id, code, 2, 'PUBLISHED', 3, NULL, 1, 'demo-v59', 2, '管理员', '入职处理微流 v2 发布：优化连接器调用', NULL,
       NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 17 DAY, NOW() - INTERVAL 12 DAY
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'MICROFLOW', id, code, 2, 'SUBMITTED', 1, NULL, 1, 'demo-v59', NULL, NULL, '合同审批微流 v2 提交：增加财务二审分支', NULL,
       NOW() - INTERVAL 1 DAY, NULL, NULL, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY
FROM `low_microflow` WHERE code = 'microflow_demo_contract_approval_flow';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'RULE', id, code, 2, 'PUBLISHED', 2, NULL, 1, 'demo-v59', 2, '管理员', '试用期决策表 v2 发布：补充 P8 职级', NULL,
       NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 10 DAY
FROM `low_rule` WHERE code = 'rule_demo_probation_decision';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'RULE', id, code, 1, 'DRAFT', NULL, NULL, 1, 'demo-v59', NULL, NULL, '合同折扣规则 v1 草稿创建', NULL,
       NULL, NULL, NULL, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY
FROM `low_rule` WHERE code = 'rule_demo_contract_discount';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'ENTITY', id, code, 2, 'PUBLISHED', 4, NULL, 1, 'demo-v59', 2, '管理员', '员工实体 v2 发布：新增 probation_end_date 字段', NULL,
       NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 17 DAY, NOW() - INTERVAL 22 DAY, NOW() - INTERVAL 17 DAY
FROM `low_entity` WHERE code = 'demo_employee';

INSERT INTO `low_publish_record`
    (`config_type`, `config_id`, `config_code`, `version`, `status`, `current_level`, `approval_chain_id`, `applicant_id`, `applicant`, `approver_id`, `approver`, `change_log`, `reject_reason`, `submitted_at`, `approved_at`, `published_at`, `create_time`, `update_time`)
SELECT 'ENTITY', id, code, 1, 'APPROVED', 3, NULL, 1, 'demo-v59', 2, '管理员', '设备实体 v1 审批通过', NULL,
       NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 1 DAY, NULL, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 1 DAY
FROM `low_entity` WHERE code = 'demo_device';

-- 3.2 配置评论（11 条，含 parent_id 回复链、@提及）
--     先清理 V59 旧数据，再分两批插入：父评论 → 子评论
DELETE FROM `low_comment` WHERE `user_name` = 'demo-v59';

-- 父评论（6 条，parent_id=NULL）
INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'FORM', id, 1, 'demo-v59', '员工档案表单的试用期结束字段建议设为只读，由微流自动计算', '@admin', NULL, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY, 0
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'FORM', id, 2, 'demo-v59', '工号字段建议增加唯一性校验，避免重复录入', NULL, NULL, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 0
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'MICROFLOW', id, 1, 'demo-v59', '入职处理微流的 CALL_CONNECTOR 节点建议增加熔断配置', '@demo', NULL, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'RULE', id, 2, 'demo-v59', '试用期决策表缺少 P8 职级，需要补充', '@admin', NULL, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 6 DAY, 0
FROM `low_rule` WHERE code = 'rule_demo_probation_decision';

INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'LIST', id, 1, 'demo-v59', '设备列表建议增加供应商筛选条件', NULL, NULL, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY, 0
FROM `low_list` WHERE code = 'list_demo_device';

INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'FORM', id, 3, 'demo-v59', '合同表单金额字段建议增加万元单位提示', '@demo', NULL, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 0
FROM `low_form` WHERE code = 'form_demo_contract';

-- 子评论（5 条，回复父评论，parent_id 引用刚插入的父评论 id）
INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'FORM', id, 2, 'demo-v59', '同意，已将试用期结束字段设为只读（disabled=true）', NULL,
       (SELECT c.id FROM (SELECT * FROM low_comment) c WHERE c.user_name='demo-v59' AND c.content LIKE '%试用期结束字段建议设为只读%' ORDER BY c.id DESC LIMIT 1),
       NOW() - INTERVAL 11 DAY, NOW() - INTERVAL 11 DAY, 0
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'FORM', id, 1, 'demo-v59', '工号唯一性校验已通过 rules 配置实现', NULL,
       (SELECT c.id FROM (SELECT * FROM low_comment) c WHERE c.user_name='demo-v59' AND c.content LIKE '%工号字段建议增加唯一性校验%' ORDER BY c.id DESC LIMIT 1),
       NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY, 0
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'MICROFLOW', id, 2, 'demo-v59', '已增加 circuitBreaker 配置，failureRateThreshold=50', NULL,
       (SELECT c.id FROM (SELECT * FROM low_comment) c WHERE c.user_name='demo-v59' AND c.content LIKE '%CALL_CONNECTOR 节点建议增加熔断%' ORDER BY c.id DESC LIMIT 1),
       NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 7 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'RULE', id, 1, 'demo-v59', 'P8 职级已补充，试用期 6 个月', NULL,
       (SELECT c.id FROM (SELECT * FROM low_comment) c WHERE c.user_name='demo-v59' AND c.content LIKE '%试用期决策表缺少 P8%' ORDER BY c.id DESC LIMIT 1),
       NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 0
FROM `low_rule` WHERE code = 'rule_demo_probation_decision';

INSERT INTO `low_comment`
    (`config_type`, `config_id`, `user_id`, `user_name`, `content`, `mentions`, `parent_id`, `create_time`, `update_time`, `deleted`)
SELECT 'LIST', id, 2, 'demo-v59', '供应商筛选已在 v2 版本添加', NULL,
       (SELECT c.id FROM (SELECT * FROM low_comment) c WHERE c.user_name='demo-v59' AND c.content LIKE '%设备列表建议增加供应商筛选%' ORDER BY c.id DESC LIMIT 1),
       NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, 0
FROM `low_list` WHERE code = 'list_demo_device';

-- 3.3 配置审计日志（11 条，覆盖 CREATE/UPDATE/DELETE/PUBLISH/ROLLBACK 各动作）
DELETE FROM `low_config_audit_log` WHERE `actor` = 'demo-v59';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'FORM', id, code, 'CREATE', NULL,
       JSON_OBJECT('code',code,'name',name,'status','DRAFT'),
       '创建员工档案表单', '192.168.1.100', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 35 DAY
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'FORM', id, code, 'UPDATE',
       JSON_OBJECT('version',1,'fields',JSON_ARRAY('emp_no','name')),
       JSON_OBJECT('version',2,'fields',JSON_ARRAY('emp_no','name','probation_end_date')),
       '新增 probation_end_date 字段', '192.168.1.100', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 26 DAY
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'FORM', id, code, 'PUBLISH',
       JSON_OBJECT('status','DRAFT','version',2),
       JSON_OBJECT('status','PUBLISHED','version',2),
       '员工档案表单 v2 发布', '192.168.1.100', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 20 DAY
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'LIST', id, code, 'CREATE', NULL,
       JSON_OBJECT('code',code,'name',name,'status','DRAFT'),
       '创建设备列表', '192.168.1.101', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 30 DAY
FROM `low_list` WHERE code = 'list_demo_device';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'LIST', id, code, 'UPDATE',
       JSON_OBJECT('filters',JSON_ARRAY('device_code','device_name')),
       JSON_OBJECT('filters',JSON_ARRAY('device_code','device_name','status','supplier_id')),
       '新增 status 和 supplier_id 筛选条件', '192.168.1.101', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 5 DAY
FROM `low_list` WHERE code = 'list_demo_device';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'MICROFLOW', id, code, 'PUBLISH',
       JSON_OBJECT('status','DRAFT','version',2),
       JSON_OBJECT('status','PUBLISHED','version',2),
       '入职处理微流 v2 发布', '192.168.1.102', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 12 DAY
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'FORM', id, code, 'DELETE',
       JSON_OBJECT('code',code,'name',name,'status','ARCHIVED'),
       NULL,
       '删除已归档的旧版员工表单', '192.168.1.103', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 15 DAY
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'FORM', id, code, 'ROLLBACK',
       JSON_OBJECT('version',3,'fields',JSON_ARRAY('emp_no','name','probation_end_date','extra_field')),
       JSON_OBJECT('version',2,'fields',JSON_ARRAY('emp_no','name','probation_end_date')),
       '回滚到 v2 版本（移除 extra_field）', '192.168.1.100', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 8 DAY
FROM `low_form` WHERE code = 'form_demo_employee';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'RULE', id, code, 'CREATE', NULL,
       JSON_OBJECT('code',code,'name',name,'type','EXPRESSION','status','DRAFT'),
       '创建合同折扣规则', '192.168.1.104', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 24 DAY
FROM `low_rule` WHERE code = 'rule_demo_contract_discount';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'RULE', id, code, 'UPDATE',
       JSON_OBJECT('definition','amount >= 100000 ? 0.85 : 1.0'),
       JSON_OBJECT('definition','amount >= 100000 ? 0.85 : (amount >= 50000 ? 0.90 : (amount >= 10000 ? 0.95 : 1.0))'),
       '扩展阶梯折扣：新增 5 万和 1 万档位', '192.168.1.104', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 1 DAY
FROM `low_rule` WHERE code = 'rule_demo_contract_discount';

INSERT INTO `low_config_audit_log`
    (`actor`, `config_type`, `config_id`, `config_code`, `action`, `before_snapshot`, `after_snapshot`, `diff_summary`, `ip`, `user_agent`, `tenant_id`, `create_time`)
SELECT 'demo-v59', 'RULE', id, code, 'PUBLISH',
       JSON_OBJECT('status','DRAFT','version',2),
       JSON_OBJECT('status','PUBLISHED','version',2),
       '试用期决策表 v2 发布', '192.168.1.105', 'Mozilla/5.0 Chrome/120', 'default',
       NOW() - INTERVAL 10 DAY
FROM `low_rule` WHERE code = 'rule_demo_probation_decision';

-- ---------------------------------------------------------------------
-- 第四部分：运行时日志类表（microflow_execution_log/trigger_execution_log/
--           ddl_backup/ddl_execution_log/import_task/process_sla_record 各 ≥10 条）
-- ---------------------------------------------------------------------

-- 4.1 微流执行轨迹（11 条，含 SUCCESS/FAILED）
--     先清理 V59 旧数据，再用 INSERT...SELECT 引用微流 id
DELETE FROM `low_microflow_execution_log` WHERE `create_by` = 'demo-v59';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-001', 'n_start', 'START',
       NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 5,
       JSON_OBJECT('emp_no','EMP001','name','张三','level','P3'),
       JSON_OBJECT('started',true), JSON_OBJECT('emp_no','EMP001'),
       'SUCCESS', NULL, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-001', 'n_rule', 'CALL_RULE',
       NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 120,
       JSON_OBJECT('ruleCode','rule_demo_probation_decision','inputs',JSON_OBJECT('level','P3')),
       JSON_OBJECT('probation_months',3), JSON_OBJECT('probation_months',3),
       'SUCCESS', NULL, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-001', 'n_conn', 'CALL_CONNECTOR',
       NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 350,
       JSON_OBJECT('connectorCode','connector_demo_hr_sync','emp_no','EMP001'),
       JSON_OBJECT('syncStatus','SUCCESS'), JSON_OBJECT('syncStatus','SUCCESS'),
       'SUCCESS', NULL, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-002', 'n_start', 'START',
       NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 4,
       JSON_OBJECT('device_code','DEV001','status','IN_USE'),
       JSON_OBJECT('started',true), NULL,
       'SUCCESS', NULL, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_device_status_change';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-002', 'n_conn', 'CALL_CONNECTOR',
       NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 8000,
       JSON_OBJECT('connectorCode','connector_demo_crm_oauth2'),
       NULL, NULL,
       'FAILED', '连接器调用超时（timeoutMillis=8000）', 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_device_status_change';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-003', 'n_if', 'IF',
       NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, 8,
       JSON_OBJECT('amount',150000,'condition','amount > 100000'),
       JSON_OBJECT('branch','n_finance'), NULL,
       'SUCCESS', NULL, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_contract_approval_flow';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-004', 'n_loop', 'LOOP',
       NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 250,
       JSON_OBJECT('iterable','${devices}','count',5),
       JSON_OBJECT('iter',1), NULL,
       'SUCCESS', NULL, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_inspection_remind';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-005', 'n_rule', 'CALL_RULE',
       NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 95,
       JSON_OBJECT('ruleCode','rule_demo_work_order_priority','inputs',JSON_OBJECT('fault_type','HARDWARE','customer_level','A')),
       JSON_OBJECT('priority','URGENT'), JSON_OBJECT('priority','URGENT'),
       'SUCCESS', NULL, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_work_order_assign';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-006', 'n_rule', 'CALL_RULE',
       NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, 110,
       JSON_OBJECT('ruleCode','rule_demo_supplier_rating','inputs',JSON_OBJECT('quality_score',60,'delivery_score',70)),
       NULL, NULL,
       'FAILED', '规则执行异常：LiteFlow 链未找到节点 ratingAggregateNode', 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_supplier_evaluate';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-007', 'n_proc', 'CALL_PROCESS',
       NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, 420,
       JSON_OBJECT('processKey','demo_kb_publish_process','article_id',1001),
       JSON_OBJECT('processInstanceId','PI-2026-001'), NULL,
       'SUCCESS', NULL, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, 0
FROM `low_microflow` WHERE code = 'microflow_demo_kb_publish_notify';

INSERT INTO `low_microflow_execution_log`
    (`microflow_id`, `microflow_code`, `execution_id`, `node_id`, `node_type`, `start_time`, `end_time`, `duration_ms`, `inputs`, `outputs`, `variables_snapshot`, `status`, `error_message`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'V59-EXEC-008', 'n_assign', 'ASSIGN',
       NOW(), NOW(), 3,
       JSON_OBJECT('expression','import java.time.LocalDate; LocalDate.now().plusMonths(3)','target','probation_end_date'),
       JSON_OBJECT('probation_end_date','2026-10-13'), NULL,
       'SUCCESS', NULL, 'demo-v59', 'demo-v59', 'demo-v59', NOW(), NOW(), 0
FROM `low_microflow` WHERE code = 'microflow_demo_onboarding';

-- 4.2 触发器执行日志（11 条）
DELETE FROM `low_trigger_execution_log` WHERE `create_by` = 'demo-v59';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'CRUD', 'MICROFLOW', 'microflow_demo_onboarding', 'V59-EXEC-001',
       JSON_OBJECT('entityCode','demo_employee','operation','CREATE','recordId',1001),
       JSON_OBJECT('microflowExecutionId','V59-EXEC-001'),
       'SUCCESS', NULL, 480, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_employee_after_create';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'QUARTZ', 'MICROFLOW', 'microflow_demo_inspection_remind', 'V59-EXEC-004',
       JSON_OBJECT('cron','0 0 8 * * ?','firedAt',NOW() - INTERVAL 2 DAY),
       JSON_OBJECT('notificationsSent',5),
       'SUCCESS', NULL, 1250, 'system', 'demo-v59', 'demo-v59', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_quartz_inspection_daily';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'QUARTZ', 'MICROFLOW', 'microflow_demo_contract_expire_remind', 'V59-EXEC-009',
       JSON_OBJECT('cron','0 0 9 1 * ?','firedAt',NOW() - INTERVAL 5 DAY),
       JSON_OBJECT('notificationsSent',3),
       'SUCCESS', NULL, 850, 'system', 'demo-v59', 'demo-v59', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_quartz_contract_expire';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'CRUD', 'MICROFLOW', 'microflow_demo_work_order_assign', 'V59-EXEC-005',
       JSON_OBJECT('entityCode','demo_work_order','operation','CREATE','recordId',2001),
       JSON_OBJECT('microflowExecutionId','V59-EXEC-005'),
       'SUCCESS', NULL, 150, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_crud_work_order_create';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'CRUD', 'MICROFLOW', 'microflow_demo_device_status_change', 'V59-EXEC-002',
       JSON_OBJECT('entityCode','demo_device','operation','UPDATE','recordId',3001,'fields',JSON_ARRAY('status')),
       JSON_OBJECT('microflowExecutionId','V59-EXEC-002'),
       'FAILED', '微流执行失败：连接器调用超时', 8050, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_crud_device_status_change';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'EVENT', 'PROCESS', 'demo_customer_complaint_process', 'V59-EXEC-010',
       JSON_OBJECT('eventType','CUSTOMER_COMPLAINT','customerId',5001,'severity','HIGH'),
       JSON_OBJECT('processInstanceId','PI-2026-COMP-001'),
       'SUCCESS', NULL, 300, 'system', 'demo-v59', 'demo-v59', NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 7 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_event_customer_complaint';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'EVENT', 'MICROFLOW', 'microflow_demo_kb_publish_notify', 'V59-EXEC-007',
       JSON_OBJECT('eventType','KB_ARTICLE_PUBLISHED','articleId',1001),
       JSON_OBJECT('microflowExecutionId','V59-EXEC-007'),
       'SUCCESS', NULL, 430, 'system', 'demo-v59', 'demo-v59', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_event_kb_publish';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'QUARTZ', 'PROCESS', 'demo_maintenance_generate_process', 'V59-EXEC-011',
       JSON_OBJECT('cron','0 30 7 * * ?','firedAt',NOW() - INTERVAL 1 DAY),
       JSON_OBJECT('processInstanceId','PI-2026-MAINT-001','tasksGenerated',8),
       'SUCCESS', NULL, 2100, 'system', 'demo-v59', 'demo-v59', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_quartz_maintenance_generate';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'CRUD', 'MICROFLOW', 'microflow_demo_contract_approval_flow', 'V59-EXEC-003',
       JSON_OBJECT('entityCode','demo_contract','operation','CREATE','recordId',4001,'amount',150000),
       JSON_OBJECT('microflowExecutionId','V59-EXEC-003'),
       'SUCCESS', NULL, 200, 'demo-v59', 'demo-v59', 'demo-v59', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_crud_contract_create';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'EVENT', 'PROCESS', 'demo_work_order_escalation_process', 'V59-EXEC-012',
       JSON_OBJECT('eventType','URGENT_WORK_ORDER','workOrderId',2005),
       JSON_OBJECT('processInstanceId','PI-2026-ESC-001'),
       'FAILED', '流程启动失败：流程定义 demo_work_order_escalation_process 未部署', 50, 'system', 'demo-v59', 'demo-v59', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY, 0
FROM `low_trigger` WHERE code = 'trigger_demo_event_urgent_work_order';

INSERT INTO `low_trigger_execution_log`
    (`trigger_id`, `trigger_code`, `trigger_type`, `target_type`, `target_code`, `execution_id`, `inputs`, `outputs`, `status`, `error_message`, `duration_ms`, `operator`, `create_by`, `update_by`, `create_time`, `update_time`, `deleted`)
SELECT id, code, 'QUARTZ', 'MICROFLOW', 'microflow_demo_work_order_close_cleanup', 'V59-EXEC-013',
       JSON_OBJECT('cron','0 0 18 * * ?','firedAt',NOW()),
       JSON_OBJECT('cleanedTasks',12),
       'SUCCESS', NULL, 680, 'system', 'demo-v59', 'demo-v59', NOW(), NOW(), 0
FROM `low_trigger` WHERE code = 'trigger_demo_quartz_daily_report';

-- 4.3 DDL 执行备份（11 条，CREATE/ALTER/DROP_COLUMN）
DELETE FROM `low_ddl_backup` WHERE `operator` = 'demo-v59';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE',
       CONCAT('CREATE TABLE `', table_name, '` (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY(id)) ENGINE=InnoDB;'),
       NULL, 'demo-v59', NOW() - INTERVAL 30 DAY
FROM `low_entity` WHERE code = 'demo_device';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE',
       CONCAT('CREATE TABLE `', table_name, '` (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY(id)) ENGINE=InnoDB;'),
       NULL, 'demo-v59', NOW() - INTERVAL 28 DAY
FROM `low_entity` WHERE code = 'demo_customer';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE',
       CONCAT('CREATE TABLE `', table_name, '` (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY(id)) ENGINE=InnoDB;'),
       NULL, 'demo-v59', NOW() - INTERVAL 25 DAY
FROM `low_entity` WHERE code = 'demo_contract';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'ALTER',
       CONCAT('ALTER TABLE `', table_name, '` ADD COLUMN status VARCHAR(16) DEFAULT "IDLE";'),
       NULL, 'demo-v59', NOW() - INTERVAL 20 DAY
FROM `low_entity` WHERE code = 'demo_device';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'ALTER',
       CONCAT('ALTER TABLE `', table_name, '` ADD COLUMN level VARCHAR(8);'),
       NULL, 'demo-v59', NOW() - INTERVAL 18 DAY
FROM `low_entity` WHERE code = 'demo_customer';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'ALTER',
       CONCAT('ALTER TABLE `', table_name, '` ADD COLUMN amount DECIMAL(18,2);'),
       NULL, 'demo-v59', NOW() - INTERVAL 15 DAY
FROM `low_entity` WHERE code = 'demo_contract';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'DROP_COLUMN',
       CONCAT('ALTER TABLE `', table_name, '` DROP COLUMN temp_field;'),
       JSON_ARRAY(JSON_OBJECT('id',1,'temp_field','old_value'),JSON_OBJECT('id',2,'temp_field','old_value2')),
       'demo-v59', NOW() - INTERVAL 10 DAY
FROM `low_entity` WHERE code = 'demo_device';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE',
       CONCAT('CREATE TABLE `', table_name, '` (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY(id)) ENGINE=InnoDB;'),
       NULL, 'demo-v59', NOW() - INTERVAL 24 DAY
FROM `low_entity` WHERE code = 'demo_work_order';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE',
       CONCAT('CREATE TABLE `', table_name, '` (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY(id)) ENGINE=InnoDB;'),
       NULL, 'demo-v59', NOW() - INTERVAL 22 DAY
FROM `low_entity` WHERE code = 'demo_inspection_record';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'ALTER',
       CONCAT('ALTER TABLE `', table_name, '` ADD INDEX idx_status (status);'),
       NULL, 'demo-v59', NOW() - INTERVAL 8 DAY
FROM `low_entity` WHERE code = 'demo_work_order';

INSERT INTO `low_ddl_backup`
    (`entity_id`, `entity_code`, `table_name`, `backup_type`, `backup_sql`, `backup_data`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE',
       CONCAT('CREATE TABLE `', table_name, '` (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY(id)) ENGINE=InnoDB;'),
       NULL, 'demo-v59', NOW() - INTERVAL 18 DAY
FROM `low_entity` WHERE code = 'demo_kb_article';

-- 4.4 DDL 执行日志（11 条，CREATE/ALTER/DROP_COLUMN/CREATE_INDEX，SUCCESS/FAILED）
DELETE FROM `low_ddl_execution_log` WHERE `operator` = 'demo-v59';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE',
       CONCAT('CREATE TABLE `', table_name, '` (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY(id)) ENGINE=InnoDB;'),
       'SUCCESS', NULL, 'demo-v59', NOW() - INTERVAL 30 DAY
FROM `low_entity` WHERE code = 'demo_device';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE',
       CONCAT('CREATE TABLE `', table_name, '` (id BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY(id)) ENGINE=InnoDB;'),
       'SUCCESS', NULL, 'demo-v59', NOW() - INTERVAL 28 DAY
FROM `low_entity` WHERE code = 'demo_customer';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'ALTER',
       CONCAT('ALTER TABLE `', table_name, '` ADD COLUMN status VARCHAR(16) DEFAULT "IDLE";'),
       'SUCCESS', NULL, 'demo-v59', NOW() - INTERVAL 20 DAY
FROM `low_entity` WHERE code = 'demo_device';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'ALTER',
       CONCAT('ALTER TABLE `', table_name, '` ADD COLUMN amount DECIMAL(18,2);'),
       'SUCCESS', NULL, 'demo-v59', NOW() - INTERVAL 15 DAY
FROM `low_entity` WHERE code = 'demo_contract';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'DROP_COLUMN',
       CONCAT('ALTER TABLE `', table_name, '` DROP COLUMN temp_field;'),
       'SUCCESS', NULL, 'demo-v59', NOW() - INTERVAL 10 DAY
FROM `low_entity` WHERE code = 'demo_device';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE_INDEX',
       CONCAT('CREATE INDEX idx_status ON `', table_name, '` (status);'),
       'SUCCESS', NULL, 'demo-v59', NOW() - INTERVAL 8 DAY
FROM `low_entity` WHERE code = 'demo_work_order';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE_INDEX',
       CONCAT('CREATE UNIQUE INDEX uk_emp_no ON `', table_name, '` (emp_no);'),
       'FAILED', 'Duplicate key name: uk_emp_no 索引已存在', 'demo-v59', NOW() - INTERVAL 7 DAY
FROM `low_entity` WHERE code = 'demo_employee';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'ALTER',
       CONCAT('ALTER TABLE `', table_name, '` ADD COLUMN contact_phone VARCHAR(32);'),
       'SUCCESS', NULL, 'demo-v59', NOW() - INTERVAL 5 DAY
FROM `low_entity` WHERE code = 'demo_supplier';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'ALTER',
       CONCAT('ALTER TABLE `', table_name, '` MODIFY COLUMN amount DECIMAL(20,2);'),
       'SUCCESS', NULL, 'demo-v59', NOW() - INTERVAL 3 DAY
FROM `low_entity` WHERE code = 'demo_contract';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'DROP_INDEX',
       CONCAT('DROP INDEX idx_old ON `', table_name, '`;'),
       'FAILED', "Can't DROP 'idx_old'; check that column/key exists", 'demo-v59', NOW() - INTERVAL 2 DAY
FROM `low_entity` WHERE code = 'demo_device';

INSERT INTO `low_ddl_execution_log`
    (`entity_id`, `entity_code`, `table_name`, `execution_type`, `ddl_sql`, `status`, `error_message`, `operator`, `create_time`)
SELECT id, code, table_name, 'CREATE_INDEX',
       CONCAT('CREATE INDEX idx_inspect_date ON `', table_name, '` (inspect_date);'),
       'SUCCESS', NULL, 'demo-v59', NOW() - INTERVAL 1 DAY
FROM `low_entity` WHERE code = 'demo_inspection_record';

-- 4.5 导入任务（11 条，含成功/失败/部分失败）
DELETE FROM `low_import_task` WHERE `operator` = 'demo-v59';

INSERT INTO `low_import_task`
    (`entity_code`, `file_name`, `status`, `total_rows`, `success_rows`, `failed_rows`, `failed_detail`, `error_message`, `operator`, `start_time`, `end_time`, `create_time`, `update_time`)
VALUES
('demo_employee', '员工导入_20260613.xlsx', 'SUCCESS', 50, 50, 0, NULL, NULL,
 'demo-v59', NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 30 DAY),

('demo_device', '设备导入_20260615.xlsx', 'SUCCESS', 30, 30, 0, NULL, NULL,
 'demo-v59', NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 28 DAY),

('demo_customer', '客户导入_20260618.xlsx', 'SUCCESS', 100, 95, 5,
 JSON_ARRAY(JSON_OBJECT('row',12,'field','contact_phone','error','手机号格式不正确'),JSON_OBJECT('row',25,'field','contact_phone','error','手机号格式不正确'),JSON_OBJECT('row',40,'field','customer_code','error','编码已存在'),JSON_OBJECT('row',67,'field','contact_phone','error','手机号格式不正确'),JSON_OBJECT('row',88,'field','customer_name','error','名称不能为空')),
 NULL, 'demo-v59', NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY),

('demo_contract', '合同导入_20260620.xlsx', 'SUCCESS', 20, 20, 0, NULL, NULL,
 'demo-v59', NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY),

('demo_supplier', '供应商导入_20260622.xlsx', 'FAILED', 15, 0, 15, NULL,
 'Excel 文件格式错误：缺少 supplier_code 列',
 'demo-v59', NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 21 DAY),

('demo_work_order', '工单导入_20260625.xlsx', 'SUCCESS', 40, 38, 2,
 JSON_ARRAY(JSON_OBJECT('row',5,'field','device_id','error','设备不存在'),JSON_OBJECT('row',22,'field','priority','error','优先级值无效')),
 NULL, 'demo-v59', NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 18 DAY),

('demo_inspection_record', '巡检导入_20260628.xlsx', 'SUCCESS', 60, 60, 0, NULL, NULL,
 'demo-v59', NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 15 DAY),

('demo_kb_article', '知识库导入_20260701.xlsx', 'FAILED', 10, 0, 10, NULL,
 '文件大小超过限制（最大 10MB，实际 15MB）',
 'demo-v59', NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY),

('demo_maintenance_plan', '维保计划导入_20260705.xlsx', 'SUCCESS', 25, 25, 0, NULL, NULL,
 'demo-v59', NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY),

('demo_employee', '员工更新导入_20260710.xlsx', 'SUCCESS', 80, 78, 2,
 JSON_ARRAY(JSON_OBJECT('row',3,'field','emp_no','error','工号不存在'),JSON_OBJECT('row',45,'field','dept_id','error','部门不存在')),
 NULL, 'demo-v59', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),

('demo_device', '设备更新导入_20260713.xlsx', 'RUNNING', 0, 0, 0, NULL, NULL,
 'demo-v59', NOW(), NULL, NOW(), NOW());

-- 4.6 流程 SLA 记录（11 条，ACTIVE/WARNING/ESCALATED/COMPLETED）
DELETE FROM `low_process_sla_record` WHERE `process_instance_id` LIKE 'V59-%';

INSERT INTO `low_process_sla_record`
    (`process_instance_id`, `task_id`, `sla_config_json`, `deadline`, `warning_sent`, `escalate_sent`, `status`, `create_time`, `update_time`)
VALUES
('V59-PI-001', 'V59-TASK-001',
 JSON_OBJECT('slaDuration',3,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_work_order_assign'),
 NOW() + INTERVAL 1 DAY, 0, 0, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

('V59-PI-002', 'V59-TASK-002',
 JSON_OBJECT('slaDuration',2,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_work_order_assign'),
 NOW() + INTERVAL 12 HOUR, 1, 0, 'WARNING', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 6 HOUR),

('V59-PI-003', 'V59-TASK-003',
 JSON_OBJECT('slaDuration',1,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_kb_publish_notify'),
 NOW() - INTERVAL 2 HOUR, 1, 1, 'ESCALATED', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 HOUR),

('V59-PI-004', 'V59-TASK-004',
 JSON_OBJECT('slaDuration',5,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_contract_approval_flow'),
 NOW() + INTERVAL 3 DAY, 0, 0, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

('V59-PI-005', 'V59-TASK-005',
 JSON_OBJECT('slaDuration',3,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_contract_approval_flow'),
 NOW() - INTERVAL 1 DAY, 1, 1, 'ESCALATED', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 1 DAY),

('V59-PI-006', 'V59-TASK-006',
 JSON_OBJECT('slaDuration',2,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_work_order_assign'),
 NOW() - INTERVAL 1 DAY, 1, 0, 'COMPLETED', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 1 DAY),

('V59-PI-007', 'V59-TASK-007',
 JSON_OBJECT('slaDuration',1,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_kb_publish_notify'),
 NOW() - INTERVAL 3 DAY, 1, 0, 'COMPLETED', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY),

('V59-PI-008', 'V59-TASK-008',
 JSON_OBJECT('slaDuration',7,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_supplier_evaluate'),
 NOW() + INTERVAL 5 DAY, 0, 0, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

('V59-PI-009', 'V59-TASK-009',
 JSON_OBJECT('slaDuration',3,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_maintenance_generate'),
 NOW() + INTERVAL 6 HOUR, 1, 0, 'WARNING', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 2 HOUR),

('V59-PI-010', 'V59-TASK-010',
 JSON_OBJECT('slaDuration',1,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_inspection_remind'),
 NOW() - INTERVAL 5 DAY, 1, 1, 'COMPLETED', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY),

('V59-PI-011', 'V59-TASK-011',
 JSON_OBJECT('slaDuration',2,'slaUnit','DAYS','slaEscalationMicroflow','microflow_demo_contract_expire_remind'),
 NOW() + INTERVAL 1 DAY, 0, 0, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

-- ---------------------------------------------------------------------
-- 完成提示
-- ---------------------------------------------------------------------
SELECT 'V59 演示数据补齐完成' AS message,
       '已为 28 张 lowcode 表补齐演示数据，每张表 ≥10 条' AS detail;
