-- P25 / 独立直写任务 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [fb_service] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/dual-track-audit-20261017/tasks-before.json
SELECT r.id,r.con_xb,r.barcode,r.grade,r.begin_date,r.end_date,r.warranty,r.remark,r.isyb,r.state, CONCAT('fb_service:',r.id,':',COALESCE(r.barcode,'')) AS renewalSourceKey FROM fb_service r WHERE r.barcode IS NOT NULL AND r.barcode<>'' AND (:deviceSns='' OR FIND_IN_SET(r.barcode,:deviceSns)) AND EXISTS (SELECT 1 FROM fb_shipment_barcode sb WHERE sb.barcode=r.barcode AND (sb.rma_no IS NULL OR sb.rma_no='' OR sb.rma_no='null') AND sb.pack_id IN (SELECT selectedPackages.packlist_id FROM (SELECT s.packlist_id FROM fb_shipment s STRAIGHT_JOIN fb_contract c ON c.contract_id=s.con_id WHERE c.contract_type IN (0,11) AND c.contract_code IN (SELECT approved.contractNo FROM (SELECT DISTINCT pc.contractNo FROM pm_project_group_relationship m STRAIGHT_JOIN pm_project_group g ON g.projectGroupCode=m.projectGroupCode STRAIGHT_JOIN pm_project p ON p.projectCode=REPLACE(m.projectCode,'-his','') AND p.projectType=g.projectType STRAIGHT_JOIN pm_project_contract pc ON pc.projectGroupCode=g.projectGroupCode WHERE NOT EXISTS (SELECT 1 FROM pm_project newer WHERE newer.projectCode=p.projectCode AND newer.projectId>p.projectId AND CAST(newer.projectCode AS BINARY)=CAST(p.projectCode AS BINARY) AND CAST(newer.projectType AS BINARY)=CAST(p.projectType AS BINARY)) AND p.disabled=0 AND p.effectiveTo IS NULL AND FIND_IN_SET(p.projectType,:projectTypes) AND (:projectTypes='' OR FIND_IN_SET(p.projectType,:projectTypes)) AND (:projectIds='' OR FIND_IN_SET(p.projectId,:projectIds))) approved)) selectedPackages));

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectWarrantyMapper.selectForUpdate; pms-module-integration/src/main/resources/mapper/sync/DirectWarrantyMapper.xml:4; kind=select
SELECT * FROM ast_device_warranty_renewal_record
        WHERE tenant_id=#{query.tenantId} AND id=#{query.id} FOR UPDATE;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectWarrantyMapper.insertRecord; pms-module-integration/src/main/resources/mapper/sync/DirectWarrantyMapper.xml:8; kind=insert
INSERT INTO ast_device_warranty_renewal_record
        (id,tenant_id,device_sn,warranty_contract_no,warranty_grade,warranty_start_date,warranty_end_date,
         warranty_months,extended,is_latest,remark,version,creator,create_time,updater,update_time,deleted)
        VALUES (#{row.id},#{row.tenantId},#{row.deviceSn},#{row.warrantyContractNo},#{row.warrantyGrade},
          #{row.warrantyStartDate},#{row.warrantyEndDate},#{row.warrantyMonths},#{row.extended},#{row.isLatest},
          #{row.remark},#{row.version},#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},#{row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectWarrantyMapper.updateRecord; pms-module-integration/src/main/resources/mapper/sync/DirectWarrantyMapper.xml:16; kind=update
UPDATE ast_device_warranty_renewal_record
        SET device_sn=#{command.row.deviceSn},warranty_contract_no=#{command.row.warrantyContractNo},
            warranty_grade=#{command.row.warrantyGrade},warranty_start_date=#{command.row.warrantyStartDate},
            warranty_end_date=#{command.row.warrantyEndDate},warranty_months=#{command.row.warrantyMonths},
            extended=#{command.row.extended},is_latest=#{command.row.isLatest},remark=#{command.row.remark},
            version=#{command.row.version},updater=#{command.row.updater},update_time=#{command.row.updateTime}
        WHERE tenant_id=#{command.row.tenantId} AND id=#{command.row.id}
          AND version=#{command.expectedVersion} AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
