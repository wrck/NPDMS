-- P01 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectCompanies] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P01SourceMapper.xml
SELECT compID AS id,compCode AS code,compName AS name,CAST(isDisabled AS UNSIGNED) AS disabled FROM ehr_company LIMIT #{limit};

-- [selectDepartments] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P01SourceMapper.xml
SELECT depID AS id,depCode AS code,depName AS name,compID AS companyId,adminID AS parentId,depGrade AS grade,director,depProperty AS property,depNum AS number,xOrder AS sortOrder,remark,depCustom1 AS custom1,depCustom3 AS custom3,depCustom5 AS custom5,CAST(isDisabled AS UNSIGNED) AS disabled FROM ehr_department LIMIT #{limit};

-- [selectFnd] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P01SourceMapper.xml
SELECT id,departmentNum AS code,departmentName AS name,status,createTime,updateTime,effectiveFrom,effectiveTo FROM fnd_department LIMIT #{limit};

-- [selectCompanyAliases] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P01SourceMapper.xml
SELECT id,compCode AS code FROM t_company LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P01OrganizationSyncTask$TargetMapper.selectCompanies; pms-module-integration/src/main/resources/mapper/basesync/P01TargetMapper.xml:4; kind=select
SELECT id,tenant_id,code,name,status,version,creator,create_time,updater,update_time,deleted, 'COMPANY' AS kind FROM system_company WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P01OrganizationSyncTask$TargetMapper.selectDepartments; pms-module-integration/src/main/resources/mapper/basesync/P01TargetMapper.xml:6; kind=select
SELECT id,tenant_id,code,name,status,version,creator,create_time,updater,update_time,deleted,parent_id,sort,leader_user_id,phone,email, 'DEPARTMENT' AS kind FROM system_dept WHERE tenant_id=#{query.tenantId} AND deleted=FALSE ORDER BY id /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P01OrganizationSyncTask$TargetMapper.insertCompanies; pms-module-integration/src/main/resources/mapper/basesync/P01TargetMapper.xml:5; kind=insert
INSERT INTO system_company (id,tenant_id,code,name,status,version,creator,create_time,updater,update_time,deleted) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.code},#{row.target.name},#{row.target.status},#{row.target.version},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P01OrganizationSyncTask$TargetMapper.insertDepartments; pms-module-integration/src/main/resources/mapper/basesync/P01TargetMapper.xml:7; kind=insert
INSERT INTO system_dept (id,tenant_id,code,name,status,version,creator,create_time,updater,update_time,deleted,parent_id,sort,leader_user_id,phone,email) VALUES  /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.code},#{row.target.name},#{row.target.status},#{row.target.version},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted},#{row.target.parentId},#{row.target.sort},#{row.target.leaderUserId},#{row.target.phone},#{row.target.email}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
