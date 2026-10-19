-- 最新原ID/用户名规则及P18/P22/源范围SQL变更见 ../base-sync-original-identifiers-preview-20261019.md 与 .sql；以下保留初次汇总SQL。
-- P24 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectReceivable] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P24SourceMapper.xml
SELECT
  r.`id` AS `source_id`,
  r.`contract_num` AS `contract_no`,
  r.`batch_code` AS `source_batch_code`,
  r.`project_name` AS `project_name`,
  r.`order_num` AS `source_order_no`,
  r.`client_supplier_code` AS `customer_code`,
  r.`client_supplier_name` AS `customer_name`,
  r.`contract_money_amount` AS `contract_amount`,
  r.`delivered_money_amount` AS `delivered_amount`,
  r.`collected_money_amount` AS `collected_amount`,
  r.`collected_money_ratio` AS `collected_ratio`,
  r.`receivables_money_amount` AS `receivable_amount`,
  r.`over_due_money_amount` AS `overdue_amount`,
  r.`maketing_department_name` AS `market_name`,
  r.`office_name` AS `department_name`,
  r.`industry_name` AS `industry_name`,
  r.`marketing_representative_name` AS `marketing_representative_name`,
  r.`currency_name` AS `currency_name`,
  r.`create_by` AS `source_creator`,
  r.`create_time` AS `create_time`,
  r.`update_by` AS `source_updater`,
  r.`update_time` AS `update_time`,
  r.`effective_from` AS `source_effective_from`,
  r.`effective_to` AS `source_effective_to`,
  r.`import_batch_num` AS `import_batch_no`,
  r.`contract_create_date` AS `contract_create_time`,
  r.`projectCode` AS `project_code`,
  r.`marketCode` AS `market_code`,
  r.`systemId` AS `system_source_key`,
  r.`industryId` AS `industry_code`,
  r.`officeCode` AS `department_code`,
  r.`expendId` AS `expend_source_key`,
  r.`usernamec` AS `marketing_representative_code`,
  r.`latest_ship_date` AS `latest_ship_time`,
  r.`usernamec2` AS `secondary_representative_code`,
  r.`systemid_o` AS `original_system_source_key`,
  r.`expendid_o` AS `original_expend_source_key`,
  r.`industry_name_o` AS `original_industry_name`,
  r.`dataSource` AS `source_system`
FROM sms_ofst_contract_head_sap r ORDER BY r.id LIMIT #{limit};

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P24ReceivableSyncTask$TargetMapper.selectExisting; pms-module-integration/src/main/resources/mapper/basesync/P24TargetMapper.xml:4; kind=select
SELECT `id`,`tenant_id`,`contract_id`,`contract_no`,`company_id`,`company_code`,`company_name`,`company_resolution_source`,`mapping_status`,`project_name`,`source_order_no`,`customer_code`,`customer_name`,`contract_amount`,`delivered_amount`,`collected_amount`,`collected_ratio`,`receivable_amount`,`overdue_amount`,`currency_name`,`market_code`,`market_name`,`department_id`,`department_code`,`department_name`,`industry_name`,`marketing_representative_name`,`source_batch_code`,`import_batch_no`,`project_code`,`system_source_key`,`system_code`,`system_name`,`industry_code`,`expend_source_key`,`expend_code`,`expend_name`,`marketing_representative_code`,`secondary_representative_code`,`original_division_values`,`source_effective_from`,`source_effective_to`,`contract_create_time`,`latest_ship_time`,`source_system`,`source_record_key`,`source_sync_time`,`creator`,`create_time`,`updater`,`update_time`,`deleted` FROM com_contract_receivable WHERE tenant_id=#{query.tenantId}
     /* CHOOSE  */ /* WHEN test=query.keys != null and !query.keys.isEmpty() */ AND  /* FOREACH collection=query.keys; item=key; open=(; separator= OR ; close=) */ ((source_system=#{key.system} AND source_record_key=#{key.key})) /* repeat with separator= OR ; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
     /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P24ReceivableSyncTask$TargetMapper.insertReceivable; pms-module-integration/src/main/resources/mapper/basesync/P24TargetMapper.xml:13; kind=insert
INSERT INTO com_contract_receivable (`id`,`tenant_id`,`contract_id`,`contract_no`,`company_id`,`company_code`,`company_name`,`company_resolution_source`,`mapping_status`,`project_name`,`source_order_no`,`customer_code`,`customer_name`,`contract_amount`,`delivered_amount`,`collected_amount`,`collected_ratio`,`receivable_amount`,`overdue_amount`,`currency_name`,`market_code`,`market_name`,`department_id`,`department_code`,`department_name`,`industry_name`,`marketing_representative_name`,`source_batch_code`,`import_batch_no`,`project_code`,`system_source_key`,`system_code`,`system_name`,`industry_code`,`expend_source_key`,`expend_code`,`expend_name`,`marketing_representative_code`,`secondary_representative_code`,`original_division_values`,`source_effective_from`,`source_effective_to`,`contract_create_time`,`latest_ship_time`,`source_system`,`source_record_key`,`source_sync_time`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
    VALUES
     /* FOREACH collection=rows; item=row; separator=, */ (#{row.target.id},#{row.target.tenantId},#{row.target.contractId},#{row.target.contractNo},#{row.target.companyId},#{row.target.companyCode},#{row.target.companyName},#{row.target.companyResolutionSource},#{row.target.mappingStatus},#{row.target.projectName},#{row.target.sourceOrderNo},#{row.target.customerCode},#{row.target.customerName},#{row.target.contractAmount},#{row.target.deliveredAmount},#{row.target.collectedAmount},#{row.target.collectedRatio},#{row.target.receivableAmount},#{row.target.overdueAmount},#{row.target.currencyName},#{row.target.marketCode},#{row.target.marketName},#{row.target.departmentId},#{row.target.departmentCode},#{row.target.departmentName},#{row.target.industryName},#{row.target.marketingRepresentativeName},#{row.target.sourceBatchCode},#{row.target.importBatchNo},#{row.target.projectCode},#{row.target.systemSourceKey},#{row.target.systemCode},#{row.target.systemName},#{row.target.industryCode},#{row.target.expendSourceKey},#{row.target.expendCode},#{row.target.expendName},#{row.target.marketingRepresentativeCode},#{row.target.secondaryRepresentativeCode},#{row.target.originalDivisionValues},#{row.target.sourceEffectiveFrom},#{row.target.sourceEffectiveTo},#{row.target.contractCreateTime},#{row.target.latestShipTime},#{row.target.sourceSystem},#{row.target.sourceRecordKey},#{row.target.sourceSyncTime},#{row.target.creator},#{row.target.createTime},#{row.target.updater},#{row.target.updateTime},#{row.target.deleted}) /* repeat with separator=,; END FOREACH */;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P24ReceivableSyncTask$TargetMapper.updateReceivable; pms-module-integration/src/main/resources/mapper/basesync/P24TargetMapper.xml:18; kind=update
UPDATE com_contract_receivable SET
      `contract_id`=#{row.target.contractId}, `contract_no`=#{row.target.contractNo}, `company_id`=#{row.target.companyId}, `company_code`=#{row.target.companyCode}, `company_name`=#{row.target.companyName}, `company_resolution_source`=#{row.target.companyResolutionSource}, `mapping_status`=#{row.target.mappingStatus}, `project_name`=#{row.target.projectName}, `source_order_no`=#{row.target.sourceOrderNo}, `customer_code`=#{row.target.customerCode}, `customer_name`=#{row.target.customerName}, `contract_amount`=#{row.target.contractAmount}, `delivered_amount`=#{row.target.deliveredAmount}, `collected_amount`=#{row.target.collectedAmount}, `collected_ratio`=#{row.target.collectedRatio}, `receivable_amount`=#{row.target.receivableAmount}, `overdue_amount`=#{row.target.overdueAmount}, `currency_name`=#{row.target.currencyName}, `market_code`=#{row.target.marketCode}, `market_name`=#{row.target.marketName}, `department_id`=#{row.target.departmentId}, `department_code`=#{row.target.departmentCode}, `department_name`=#{row.target.departmentName}, `industry_name`=#{row.target.industryName}, `marketing_representative_name`=#{row.target.marketingRepresentativeName}, `source_batch_code`=#{row.target.sourceBatchCode}, `import_batch_no`=#{row.target.importBatchNo}, `project_code`=#{row.target.projectCode}, `system_source_key`=#{row.target.systemSourceKey}, `system_code`=#{row.target.systemCode}, `system_name`=#{row.target.systemName}, `industry_code`=#{row.target.industryCode}, `expend_source_key`=#{row.target.expendSourceKey}, `expend_code`=#{row.target.expendCode}, `expend_name`=#{row.target.expendName}, `marketing_representative_code`=#{row.target.marketingRepresentativeCode}, `secondary_representative_code`=#{row.target.secondaryRepresentativeCode}, `original_division_values`=#{row.target.originalDivisionValues}, `source_effective_from`=#{row.target.sourceEffectiveFrom}, `source_effective_to`=#{row.target.sourceEffectiveTo}, `contract_create_time`=#{row.target.contractCreateTime}, `latest_ship_time`=#{row.target.latestShipTime}, `source_system`=#{row.target.sourceSystem}, `source_record_key`=#{row.target.sourceRecordKey}, `source_sync_time`=#{row.target.sourceSyncTime}, `creator`=#{row.target.creator}, `create_time`=#{row.target.createTime}, `updater`=#{row.target.updater}, `update_time`=#{row.target.updateTime}
    WHERE tenant_id=#{row.target.tenantId} AND id=#{row.target.id} AND source_system=#{row.target.sourceSystem}
      AND source_record_key=#{row.target.sourceRecordKey} AND deleted=FALSE;

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
