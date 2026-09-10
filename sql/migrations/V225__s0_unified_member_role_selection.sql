-- S0专项Z01：角色资格与项目成员关系分开，保留V224及所有已发生的成员历史。
UPDATE system_dict_data
SET status = 1, updater = 's0-inheritance'
WHERE dict_type = 'pms_project_member_role' AND creator = 's0-inheritance'
  AND id IN (993109100504, 993109100505)
  AND value IN ('ENGINEER', 'OUTSOURCED_ENGINEER') AND deleted = b'0';

INSERT INTO system_dict_data
    (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater, deleted)
SELECT entry.id, entry.sort, entry.label, entry.value, 'pms_project_member_role', 0, 'info', '',
       'S0专项：统一成员角色，权限以当前项目有效关系和业务操作校验为准',
       's0-inheritance', 's0-inheritance', b'0'
FROM (
    SELECT 993109100506 AS id, 4 AS sort, '团队成员' AS label, 'TEAM_MEMBER' AS value
    UNION ALL SELECT 993109100507, 5, '销售代表', 'SALES_REPRESENTATIVE'
) entry
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data existing
                  WHERE existing.id = entry.id
                     OR (existing.dict_type = 'pms_project_member_role' AND existing.value = entry.value AND existing.deleted = b'0'));

-- 只补角色定义，不向任何已有用户分配角色，也不增加角色菜单权限。
-- 租户ID可能是大数值业务ID，直接保留其原值；角色ID使用本专项高段，不对租户ID做乘法。
SET @s0_role_base = GREATEST(993109110000,
    (SELECT COALESCE(MAX(id), 0) FROM system_role
     WHERE id >= 993109110000 AND id < 993109120000));
INSERT INTO system_role
    (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark,
     creator, updater, deleted, tenant_id)
SELECT @s0_role_base + ROW_NUMBER() OVER (ORDER BY tenant.id, entry.role_offset), entry.name, entry.code,
       100 + entry.role_offset, 5, '[]', 0, 2,
       'S0专项成员候选资格；人员角色和菜单权限须通过系统正常功能配置',
       's0-inheritance', 's0-inheritance', b'0', tenant.id
FROM system_tenant tenant
CROSS JOIN (
    SELECT 1 AS role_offset, '服务经理' AS name, 'SERVICE_MANAGER' AS code
    UNION ALL SELECT 2, '项目经理', 'PROJECT_MANAGER'
    UNION ALL SELECT 3, '销售代表', 'SALES_REPRESENTATIVE'
) entry
WHERE tenant.deleted = b'0' AND tenant.status = 0
  AND NOT EXISTS (SELECT 1 FROM system_role existing
                  WHERE existing.tenant_id = tenant.id AND existing.code = entry.code AND existing.deleted = b'0');
