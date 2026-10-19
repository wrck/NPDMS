-- P25 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [fb_service] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P25.json
SELECT r.id,r.con_xb,r.barcode,r.grade,r.begin_date,r.end_date,r.warranty,r.remark,r.isyb,r.state,
 CONCAT('fb_service:',r.id,':',COALESCE(r.barcode,'')) AS renewalSourceKey
FROM fb_service r
WHERE r.barcode IS NOT NULL AND r.barcode<>''
 AND (:deviceSns='' OR FIND_IN_SET(r.barcode,:deviceSns))
;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper.selectForUpdate; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRenewalRecordMapper.xml:4; kind=select
SELECT * FROM ast_device_warranty_renewal_record
        WHERE tenant_id=#{query.tenantId} AND id=#{query.id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper.count; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRenewalRecordMapper.xml:44; kind=select
SELECT COUNT(*) FROM ast_device_warranty_renewal_record r WHERE r.tenant_id=#{query.tenantId} AND r.deleted=FALSE
        AND r.device_sn IS NOT NULL AND r.device_sn <> ''
         /* CHOOSE  */ /* WHEN test=query.deviceSn != null and query.deviceSn != '' */ AND r.device_sn=#{query.deviceSn} /* END WHEN */ 
             /* WHEN test=query.contractNumbers != null and !query.contractNumbers.isEmpty() */ AND EXISTS (SELECT 1 FROM ast_device_shipment s
                    WHERE s.tenant_id=r.tenant_id AND s.deleted=FALSE AND s.device_sn=r.device_sn
                      AND s.contract_no IN
                       /* FOREACH collection=query.contractNumbers; item=number; open=(; separator=,; close=) */ (#{number}) /* repeat with separator=,; END FOREACH */ 
                      AND (s.rma_no IS NULL OR s.rma_no='' OR s.rma_no='null')) /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
         /* IF test=query.sn != null and query.sn != '' */ AND r.device_sn LIKE CONCAT('%',#{query.sn},'%') /* END IF */;

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper.selectPage; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRenewalRecordMapper.xml:47; kind=select
SELECT r.* FROM ast_device_warranty_renewal_record r WHERE r.tenant_id=#{query.tenantId} AND r.deleted=FALSE
        AND r.device_sn IS NOT NULL AND r.device_sn <> ''
         /* CHOOSE  */ /* WHEN test=query.deviceSn != null and query.deviceSn != '' */ AND r.device_sn=#{query.deviceSn} /* END WHEN */ 
             /* WHEN test=query.contractNumbers != null and !query.contractNumbers.isEmpty() */ AND EXISTS (SELECT 1 FROM ast_device_shipment s
                    WHERE s.tenant_id=r.tenant_id AND s.deleted=FALSE AND s.device_sn=r.device_sn
                      AND s.contract_no IN
                       /* FOREACH collection=query.contractNumbers; item=number; open=(; separator=,; close=) */ (#{number}) /* repeat with separator=,; END FOREACH */ 
                      AND (s.rma_no IS NULL OR s.rma_no='' OR s.rma_no='null')) /* END WHEN */ 
             /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
         /* IF test=query.sn != null and query.sn != '' */ AND r.device_sn LIKE CONCAT('%',#{query.sn},'%') /* END IF */
        ORDER BY r.warranty_start_date DESC,r.id DESC LIMIT #{query.limit} OFFSET #{query.offset};

-- cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper.selectMaintenance; pms-module-integration/src/main/resources/mapper/sync/SyncRunMapper.xml:8; kind=select
SELECT id, status, config_snapshot FROM int_sync_run WHERE tenant_id=#{tenantId} AND deleted=FALSE
      AND (cache_pending=TRUE OR (status IN ('QUEUED','READING','VALIDATING','APPLYING') AND started_at < #{now}))
      ORDER BY id LIMIT 100;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper.insertRecord; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRenewalRecordMapper.xml:8; kind=insert
INSERT INTO ast_device_warranty_renewal_record
        (id,tenant_id,device_sn,warranty_contract_no,warranty_grade,warranty_start_date,warranty_end_date,
         warranty_months,extended,is_latest,remark,version,creator,create_time,updater,update_time,deleted)
        VALUES (#{row.id},#{row.tenantId},#{row.deviceSn},#{row.warrantyContractNo},#{row.warrantyGrade},
          #{row.warrantyStartDate},#{row.warrantyEndDate},#{row.warrantyMonths},#{row.extended},#{row.isLatest},
          #{row.remark},#{row.version},#{row.creator},#{row.createTime},#{row.updater},#{row.updateTime},#{row.deleted});

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRenewalRecordMapper.updateRecord; pms-module-asset/src/main/resources/mapper/warranty/DeviceWarrantyRenewalRecordMapper.xml:16; kind=update
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
