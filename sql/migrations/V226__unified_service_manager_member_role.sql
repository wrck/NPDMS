-- 专项最新裁决：主子项目均使用服务经理；旧字典及历史区间保持不变。
INSERT INTO system_dict_data
    (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, updater, deleted)
SELECT 993109100508, 1, '服务经理', 'SERVICE_MANAGER', 'pms_project_member_role',
       0, 'primary', '', '项目成员统一角色，主责不额外授予权限', 's0-inheritance', 's0-inheritance', b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data
                  WHERE dict_type = 'pms_project_member_role' AND value = 'SERVICE_MANAGER' AND deleted = b'0');
