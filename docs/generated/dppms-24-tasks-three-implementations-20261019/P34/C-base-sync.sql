-- P34 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectFactoryHistory] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P34SourceMapper.xml
SELECT `serial_number` AS `device_sn`, `conp` AS `conp_version`, `cpld` AS `cpld_version`, `boot` AS `boot_version`, `pcb` AS `pcb_version` FROM fb_soft_version ORDER BY BINARY serial_number, BINARY conp, BINARY cpld, BINARY boot, BINARY pcb LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P34FactoryHistorySyncTask$TargetMapper.selectExisting; pms-module-integration/src/main/resources/mapper/basesync/P34TargetMapper.xml:4; kind=select
SELECT `id`, `tenant_id`, `project_id`, `device_sn`, `conp_version`, `boot_version`, `cpld_version`, `pcb_version`, `creator`, `create_time`, `updater`, `update_time`, `deleted` FROM ast_device_factory_version_record
    WHERE tenant_id=#{query.tenantId}
     /* CHOOSE  */ /* WHEN test=query.ids != null and !query.ids.isEmpty() */ AND id IN
       /* FOREACH collection=query.ids; item=id; open=(; close=); separator=, */ (#{id}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */  /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P34FactoryHistorySyncTask$TargetMapper.nullableSerialColumn; pms-module-integration/src/main/resources/mapper/basesync/P34TargetMapper.xml:13; kind=select
SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ast_device_factory_version_record' AND column_name='device_sn' AND is_nullable='YES';

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P34FactoryHistorySyncTask$TargetMapper.insertFactoryHistory; pms-module-integration/src/main/resources/mapper/basesync/P34TargetMapper.xml:10; kind=insert
INSERT INTO ast_device_factory_version_record (`id`, `tenant_id`, `project_id`, `device_sn`, `conp_version`, `boot_version`, `cpld_version`, `pcb_version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
     /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id}, #{row.target.tenantId}, #{row.target.projectId}, #{row.target.deviceSn}, #{row.target.conpVersion}, #{row.target.bootVersion}, #{row.target.cpldVersion}, #{row.target.pcbVersion}, #{row.target.creator}, #{row.target.createTime}, #{row.target.updater}, #{row.target.updateTime}, #{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
