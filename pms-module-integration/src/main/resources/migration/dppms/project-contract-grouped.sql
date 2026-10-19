-- 评审稿：用户已确认简化边界；未保存实际配置、未执行。
-- SQL仅按项目业务身份+合同号归并；原记录集合传入现有Owner，正式关系写一次。
-- ACTIVE优先、原创建/更新审计、同刻操作者冲突继续由原服务规则处理。
-- sourceRelations只作为传输/来源证据；不得把固定业务字段新存为业务JSON。
-- 不重读pm_project：按本地P16的projectType+完整projectCode精确筛选，再补originalProjectId。
-- 本地项目范围为空时不发起来源查询；范围筛选先于原审计/冲突校验。
SELECT grouped.projectType,grouped.originalProjectCode,grouped.contractNo,
 SHA2(CAST(JSON_ARRAY(grouped.projectType,grouped.originalProjectCode,grouped.contractNo) AS CHAR),256) AS migrationKey,
 grouped.sourceRelations
FROM (
SELECT g.projectType,REPLACE(m.projectCode,'-his','') AS originalProjectCode,
 c.contractNo,
 JSON_ARRAYAGG(JSON_OBJECT(
  'id',c.id,'contractNo',c.contractNo,'projectGroupCode',c.projectGroupCode,
  'createTime',c.createTime,'createBy',c.createBy,'updateTime',c.updateTime,'updateBy',c.updateBy,
  'member_id',m.id,'member_projectGroupCode',m.projectGroupCode,'member_projectCode',m.projectCode,
  'member_mergeBranchMark',m.mergeBranchMark,'member_smsProjectCode',m.smsProjectCode,
  'member_createTime',m.createTime,'member_createBy',m.createBy,'member_updateTime',m.updateTime,'member_updateBy',m.updateBy,
  'group_id',g.id,'group_projectGroupCode',g.projectGroupCode,'group_projectGroupName',g.projectGroupName,
  'group_projectType',g.projectType,'group_createTime',g.createTime,'group_createBy',g.createBy,
  'group_updateTime',g.updateTime,'group_updateBy',g.updateBy)) AS sourceRelations
FROM pm_project_contract c
JOIN pm_project_group g ON CAST(g.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
LEFT JOIN pm_project_group_relationship m ON CAST(m.projectGroupCode AS BINARY)=CAST(c.projectGroupCode AS BINARY)
WHERE FIND_IN_SET(g.projectType,:projectTypes)
GROUP BY g.projectType,CAST(g.projectType AS BINARY),REPLACE(m.projectCode,'-his',''),
 CAST(REPLACE(m.projectCode,'-his','') AS BINARY),c.contractNo,CAST(c.contractNo AS BINARY)
) grouped
