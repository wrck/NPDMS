-- 用户 2026-10-23 批准：SYSTEM 用户公司部门范围维护。
-- 按钮权限只进入既有用户管理，不给业务角色自动追加授权。
INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 995150001,'公司部门范围查询','system:user-scope:query',3,20,100,'','','',0,b'1',b'1',b'1',
       'scope-maintenance-seed','scope-maintenance-seed',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='system:user-scope:query' AND deleted=b'0');

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 995150002,'公司部门范围维护','system:user-scope:manage',3,21,100,'','','',0,b'1',b'1',b'1',
       'scope-maintenance-seed','scope-maintenance-seed',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='system:user-scope:manage' AND deleted=b'0');

-- 专用停用账户：无密码、不能登录，不改变任何实际用户的组织授权。
-- 仅复用既有示例公司/部门；不存在时不编造来源组织，也不跨租户引用。
INSERT INTO system_users
  (id,username,password,nickname,status,creator,updater,deleted,tenant_id)
SELECT 995150000001,'scope-maintenance-demo','','公司部门范围示例',1,
       'scope-maintenance-seed','scope-maintenance-seed',b'0',c.tenant_id
FROM system_company c
WHERE c.id=930800 AND c.code='DPTECH-DEMO' AND c.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_users u
    WHERE u.id=995150000001 OR (u.tenant_id=c.tenant_id AND u.username='scope-maintenance-demo'));

INSERT INTO system_user_company_department_scope
  (id,tenant_id,user_id,company_id,company_code,company_name,department_id,department_code,department_name,
   scope_role,is_primary,effective_from,effective_to,status,version,creator,updater,deleted)
SELECT 995150010000+sample.n,c.tenant_id,u.id,c.id,c.code,c.name,
       CASE WHEN sample.n=2 THEN NULL ELSE d.id END,
       CASE WHEN sample.n=2 THEN NULL ELSE d.code END,
       CASE WHEN sample.n=2 THEN NULL ELSE d.name END,
       CASE WHEN sample.n=2 THEN 'SERVICE_MANAGER' ELSE 'PROJECT_MANAGER' END,
       CASE WHEN sample.n=1 THEN b'1' ELSE b'0' END,
       CASE WHEN sample.n=3 THEN '2099-01-01 00:00:00' ELSE '2020-01-01 00:00:00' END,
       CASE WHEN sample.n=4 THEN '2021-01-01 00:00:00' ELSE NULL END,
       CASE WHEN sample.n=5 THEN 1 ELSE 0 END,0,
       'scope-maintenance-seed','scope-maintenance-seed',b'0'
FROM system_company c
JOIN system_dept d ON d.id=930801 AND d.code='OFFICE-HZ-DEMO'
  AND d.tenant_id=c.tenant_id AND d.deleted=b'0'
JOIN system_users u ON u.id=995150000001 AND u.tenant_id=c.tenant_id
  AND u.username='scope-maintenance-demo' AND u.status=1
  AND u.creator='scope-maintenance-seed' AND u.deleted=b'0'
JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) sample
WHERE c.id=930800 AND c.code='DPTECH-DEMO' AND c.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_user_company_department_scope s WHERE s.id=995150010000+sample.n);
