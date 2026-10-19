-- P34 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [fb_soft_version] TABLE_READER_PROJECTION; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT `serial_number`, `conp`, `cpld`, `boot`, `pcb` FROM `fb_soft_version`;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectFactoryHistoryMapper.selectGlobalRecordForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectFactoryHistoryMapper.xml:4; kind=select
SELECT * FROM ast_device_factory_version_record WHERE tenant_id=#{query.tenantId} AND project_id IS NULL AND id=#{query.id} FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectFactoryHistoryMapper.insert; pms-module-integration/src/main/resources/mapper/sync/DirectFactoryHistoryMapper.xml:7; kind=insert
INSERT INTO ast_device_factory_version_record(id,tenant_id,project_id,device_sn,conp_version,boot_version,cpld_version,pcb_version,creator,create_time,updater,update_time,deleted)
VALUES(#{row.id},#{row.tenantId},#{row.projectId},#{row.deviceSn},#{row.conpVersion},#{row.bootVersion},#{row.cpldVersion},#{row.pcbVersion},#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},#{row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
