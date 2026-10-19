-- P01 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE ehr_company; dialect=MYSQL
SELECT
  -- ehr_company.compID -> INT来源绑定; 当前配置/Owner
  s.`compID` AS `raw__compID`,
  -- ehr_company.compCode -> system_company.code; 当前配置/Owner
  s.`compCode` AS `raw__code`,
  -- ehr_company.compName -> system_company.name; 当前配置/Owner
  s.`compName` AS `raw__name`,
  -- ehr_company.compAbbr -> P01未采集; 本任务未采集
  s.`compAbbr` AS `raw__compAbbr`,
  -- ehr_company.adminID -> P01未采集; 本任务未采集
  s.`adminID` AS `raw__adminID`,
  -- ehr_company.compGrade -> P01未采集; 本任务未采集
  s.`compGrade` AS `raw__compGrade`,
  -- ehr_company.compType -> P01未采集; 本任务未采集
  s.`compType` AS `raw__compType`,
  -- ehr_company.compArea -> P01未采集; 本任务未采集
  s.`compArea` AS `raw__compArea`,
  -- ehr_company.effectDate -> P01未采集; 本任务未采集
  s.`effectDate` AS `raw__effectDate`,
  -- ehr_company.lawyer -> P01未采集; 本任务未采集
  s.`lawyer` AS `raw__lawyer`,
  -- ehr_company.address -> P01未采集; 本任务未采集
  s.`address` AS `raw__address`,
  -- ehr_company.regAddress -> P01未采集; 本任务未采集
  s.`regAddress` AS `raw__regAddress`,
  -- ehr_company.tel -> P01未采集; 本任务未采集
  s.`tel` AS `raw__tel`,
  -- ehr_company.fax -> P01未采集; 本任务未采集
  s.`fax` AS `raw__fax`,
  -- ehr_company.postCode -> P01未采集; 本任务未采集
  s.`postCode` AS `raw__postCode`,
  -- ehr_company.webSite -> P01未采集; 本任务未采集
  s.`webSite` AS `raw__webSite`,
  -- ehr_company.isDisabled -> system_company.status; 当前配置/Owner
  s.`isDisabled` AS `raw__status`,
  -- ehr_company.disabledDate -> P01未采集; 本任务未采集
  s.`disabledDate` AS `raw__disabledDate`,
  -- ehr_company.remark -> P01未采集; 本任务未采集
  s.`remark` AS `raw__remark`
FROM `ehr_company` s;

-- SOURCE ehr_department; dialect=MYSQL
SELECT
  -- ehr_department.depID -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`depID` AS `raw__depID`,
  -- ehr_department.depCode -> system_dept.code; 业务字段/正式关系
  s.`depCode` AS `raw__code`,
  -- ehr_department.depName -> system_dept.name; 业务字段/正式关系
  s.`depName` AS `raw__name`,
  -- ehr_department.depAbbr -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depAbbr` AS `raw__depAbbr`,
  -- ehr_department.compID -> 不迁入正式业务字段; 业务字段/正式关系
  s.`compID` AS `raw__compID`,
  -- ehr_department.adminID -> system_dept.parent_id; 业务字段/正式关系
  s.`adminID` AS `raw__parent_id`,
  -- ehr_department.depGrade -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depGrade` AS `raw__depGrade`,
  -- ehr_department.depType -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depType` AS `raw__depType`,
  -- ehr_department.depProperty -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depProperty` AS `raw__depProperty`,
  -- ehr_department.depCost -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depCost` AS `raw__depCost`,
  -- ehr_department.director -> system_dept.leader_user_id; 业务字段/正式关系
  s.`director` AS `raw__leader_user_id`,
  -- ehr_department.director2 -> 不迁入正式业务字段; 业务字段/正式关系
  s.`director2` AS `raw__director2`,
  -- ehr_department.depEmp -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depEmp` AS `raw__depEmp`,
  -- ehr_department.depNum -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depNum` AS `raw__depNum`,
  -- ehr_department.effectDate -> 不迁入正式业务字段; 业务字段/正式关系
  s.`effectDate` AS `raw__effectDate`,
  -- ehr_department.xOrder -> 不迁入正式业务字段; 业务字段/正式关系
  s.`xOrder` AS `raw__xOrder`,
  -- ehr_department.isDisabled -> system_dept.status; 业务字段/正式关系
  s.`isDisabled` AS `raw__status`,
  -- ehr_department.disabledDate -> 不迁入正式业务字段; 业务字段/正式关系
  s.`disabledDate` AS `raw__disabledDate`,
  -- ehr_department.remark -> 不迁入正式业务字段; 业务字段/正式关系
  s.`remark` AS `raw__remark`,
  -- ehr_department.depCustom1 -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depCustom1` AS `raw__depCustom1`,
  -- ehr_department.depCustom2 -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depCustom2` AS `raw__depCustom2`,
  -- ehr_department.depCustom3 -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depCustom3` AS `raw__depCustom3`,
  -- ehr_department.depCustom4 -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depCustom4` AS `raw__depCustom4`,
  -- ehr_department.depCustom5 -> 不迁入正式业务字段; 业务字段/正式关系
  s.`depCustom5` AS `raw__depCustom5`
FROM `ehr_department` s;

-- SOURCE fnd_department; dialect=MYSQL
SELECT
  -- fnd_department.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- fnd_department.departmentNum -> system_dept.code；system_dept.parent_id; 业务字段/正式关系
  s.`departmentNum` AS `raw__code`,
  -- fnd_department.departmentName -> system_dept.name; 业务字段/正式关系
  s.`departmentName` AS `raw__name`,
  -- fnd_department.isparam -> 不迁入; 已批准排除
  s.`isparam` AS `raw__isparam`,
  -- fnd_department.status -> system_dept.status; 业务字段/正式关系
  s.`status` AS `raw__status`,
  -- fnd_department.createTime -> system_dept.create_time; 业务字段/正式关系
  s.`createTime` AS `raw__create_time`,
  -- fnd_department.effectiveFrom -> 不迁入; 已批准排除
  s.`effectiveFrom` AS `raw__effectiveFrom`,
  -- fnd_department.effectiveTo -> 不迁入; 已批准排除
  s.`effectiveTo` AS `raw__effectiveTo`,
  -- fnd_department.updateTime -> system_dept.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__update_time`
FROM `fnd_department` s;
