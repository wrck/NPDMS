-- P31 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_member; dialect=MYSQL
SELECT
  -- pm_project_member.id -> INT/PLT来源绑定; P31既有；P32最新裁决待实现
  s.`id` AS `raw__id`,
  -- pm_project_member.projectId -> proj_project_member_assignment.project_id; P31既有；P32最新裁决待实现
  s.`projectId` AS `raw__project_id`,
  -- pm_project_member.projectType -> 项目身份约束; P31既有；P32最新裁决待实现
  s.`projectType` AS `raw__projectType`,
  -- pm_project_member.memberRole -> proj_project_member_assignment.member_role; P31既有；P32最新裁决待实现
  s.`memberRole` AS `raw__member_role`,
  -- pm_project_member.memberCode -> proj_project_member_assignment.member_code；proj_project_member_assignment.user_id; P31既有；P32最新裁决待实现
  s.`memberCode` AS `raw__member_code`,
  -- pm_project_member.memberName -> proj_project_member_assignment.member_name; P31既有；P32最新裁决待实现
  s.`memberName` AS `raw__member_name`,
  -- pm_project_member.phoneNum -> proj_project_member_assignment.phone_number; P31既有；P32最新裁决待实现
  s.`phoneNum` AS `raw__phone_number`,
  -- pm_project_member.email -> proj_project_member_assignment.email; P31既有；P32最新裁决待实现
  s.`email` AS `raw__email`,
  -- pm_project_member.fromFlag -> proj_project_member_assignment.registration_source; P31既有；P32最新裁决待实现
  s.`fromFlag` AS `raw__registration_source`,
  -- pm_project_member.createTime -> proj_project_member_assignment.create_time; P31既有；P32最新裁决待实现
  s.`createTime` AS `raw__create_time`,
  -- pm_project_member.createBy -> proj_project_member_assignment.creator; P31既有；P32最新裁决待实现
  s.`createBy` AS `raw__creator`,
  -- pm_project_member.updateTime -> proj_project_member_assignment.update_time; P31既有；P32最新裁决待实现
  s.`updateTime` AS `raw__update_time`,
  -- pm_project_member.updateBy -> proj_project_member_assignment.updater; P31既有；P32最新裁决待实现
  s.`updateBy` AS `raw__updater`,
  -- pm_project_member.effectiveTo -> proj_project_member_assignment.effective_to; P31既有；P32最新裁决待实现
  s.`effectiveTo` AS `raw__effective_to`,
  -- pm_project_member.effectiveFrom -> proj_project_member_assignment.effective_from; P31既有；P32最新裁决待实现
  s.`effectiveFrom` AS `raw__effective_from`
FROM `pm_project_member` s;
