-- P11 / 新虚基类套件 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [selectCrmProduct] ACTUAL_SOURCE_MAPPER; source=pms-module-integration/src/main/resources/base-sync-source/P11SourceMapper.xml
SELECT TOP (#{limit})
  LTRIM(RTRIM(t.[itemCode])) AS [product_code],
  t.[itemModel] AS [product_model],
  t.[itemDesc] AS [product_name],
  t.[itemDesc] AS [product_desc],
  t.[remark] AS [technical_spec],
  t.[productCode] AS [product_category],
  t.[productSubCode] AS [product_type],
  t.[listPrice] AS [list_price],
  t.[status] AS [crm_status],
  t.[BU] AS [business_unit],
  t.[productLine] AS [product_line],
  t.[orgId] AS [org_id],
  t.[createTime] AS [source_created_at],
  t.[updateTime] AS [source_updated_at],
  t.[statecode] AS [crm_state_code],
  t.[statuscode] AS [crm_status_code],
  t.[productStage] AS [product_stage],
  t.[endOfSaleDate] AS [end_of_sale_date],
  t.[endOfSupportDate] AS [end_of_support_date],
  t.[endOfLifeDate] AS [end_of_life_date],
  t.[lastRenewalDate] AS [last_renewal_date]
    FROM DPtech_v_product_info_single t
    ORDER BY t.[itemCode];

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P11CrmProductSyncTask$TargetMapper.selectCrmTargets; pms-module-integration/src/main/resources/mapper/basesync/P11TargetMapper.xml:4; kind=select
SELECT product_code, product_model, product_name, product_desc, technical_spec, product_category, product_type, list_price, crm_status, business_unit, product_line, org_id, source_created_at, source_updated_at, crm_state_code, crm_status_code, product_stage, end_of_sale_date, end_of_support_date, end_of_life_date, last_renewal_date, id, tenant_id, source_system, source_key, source_version, status, version, synced_at, creator, updater, create_time, update_time, deleted FROM ast_product_official_info
    WHERE tenant_id=#{query.tenantId}
     /* CHOOSE  */ /* WHEN test=query.productCodes != null and !query.productCodes.isEmpty() */ AND product_code IN  /* FOREACH collection=query.productCodes; item=code; open=(; close=); separator=, */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END WHEN */  /* OTHERWISE  */ AND 1=0 /* END OTHERWISE */ /* END CHOOSE */ 
     /* IF test=!query.preview */ FOR UPDATE /* END IF */;

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- cn.iocoder.yudao.module.pms.integration.basesync.task.P11CrmProductSyncTask$TargetMapper.insertCrmProduct; pms-module-integration/src/main/resources/mapper/basesync/P11TargetMapper.xml:15; kind=insert
INSERT INTO ast_product_official_info
      (product_code, product_model, product_name, product_desc, technical_spec, product_category, product_type, list_price, crm_status, business_unit, product_line, org_id, source_created_at, source_updated_at, crm_state_code, crm_status_code, product_stage, end_of_sale_date, end_of_support_date, end_of_life_date, last_renewal_date, id, tenant_id, source_system, source_key, source_version, status, version, synced_at, creator, updater, create_time, update_time, deleted, sync_status)
    VALUES
     /* FOREACH collection=rows; item=row; separator=, */ (#{row.productCode}, #{row.productModel}, #{row.productName}, #{row.productDesc}, #{row.technicalSpec}, #{row.productCategory}, #{row.productType}, #{row.listPrice}, #{row.crmStatus}, #{row.businessUnit}, #{row.productLine}, #{row.orgId}, #{row.sourceCreatedAt}, #{row.sourceUpdatedAt}, #{row.crmStateCode}, #{row.crmStatusCode}, #{row.productStage}, #{row.endOfSaleDate}, #{row.endOfSupportDate}, #{row.endOfLifeDate}, #{row.lastRenewalDate}, #{row.id}, #{context.tenantId}, 'CRM', #{row.productCode}, #{row.sourceVersion}, CASE WHEN #{row.crmStateCode}=0 THEN 'ACTIVE' ELSE 'INACTIVE' END, 0, #{context.startedAt}, #{context.actorId}, #{context.actorId}, #{context.startedAt}, #{context.startedAt}, FALSE, 'SUCCESS') /* repeat with separator=,; END FOREACH */  AS incoming
    ON DUPLICATE KEY UPDATE
      
      id=CASE WHEN ast_product_official_info.tenant_id=incoming.tenant_id
        AND CAST(ast_product_official_info.product_code AS BINARY)=CAST(incoming.product_code AS BINARY)
        AND ast_product_official_info.deleted=FALSE
        AND CAST(ast_product_official_info.source_system AS BINARY) IN (CAST('CRM' AS BINARY),CAST('DPPMS' AS BINARY))
        THEN ast_product_official_info.id ELSE NULL END,
      product_model=COALESCE(incoming.product_model,ast_product_official_info.product_model),
      product_name=COALESCE(incoming.product_name,ast_product_official_info.product_name),
      product_desc=COALESCE(incoming.product_desc,ast_product_official_info.product_desc),
      technical_spec=COALESCE(incoming.technical_spec,ast_product_official_info.technical_spec),
      product_category=COALESCE(incoming.product_category,ast_product_official_info.product_category),
      product_type=COALESCE(incoming.product_type,ast_product_official_info.product_type),
      list_price=COALESCE(incoming.list_price,ast_product_official_info.list_price),
      crm_status=COALESCE(incoming.crm_status,ast_product_official_info.crm_status),
      business_unit=COALESCE(incoming.business_unit,ast_product_official_info.business_unit),
      product_line=COALESCE(incoming.product_line,ast_product_official_info.product_line),
      org_id=COALESCE(incoming.org_id,ast_product_official_info.org_id),
      source_created_at=COALESCE(incoming.source_created_at,ast_product_official_info.source_created_at),
      source_updated_at=COALESCE(incoming.source_updated_at,ast_product_official_info.source_updated_at),
      crm_state_code=COALESCE(incoming.crm_state_code,ast_product_official_info.crm_state_code),
      crm_status_code=COALESCE(incoming.crm_status_code,ast_product_official_info.crm_status_code),
      product_stage=COALESCE(incoming.product_stage,ast_product_official_info.product_stage),
      end_of_sale_date=COALESCE(incoming.end_of_sale_date,ast_product_official_info.end_of_sale_date),
      end_of_support_date=COALESCE(incoming.end_of_support_date,ast_product_official_info.end_of_support_date),
      end_of_life_date=COALESCE(incoming.end_of_life_date,ast_product_official_info.end_of_life_date),
      last_renewal_date=COALESCE(incoming.last_renewal_date,ast_product_official_info.last_renewal_date),
      source_system=incoming.source_system,
      source_key=incoming.source_key,
      source_version=incoming.source_version,
      status=incoming.status,
      synced_at=incoming.synced_at,
      updater=incoming.updater,
      update_time=incoming.update_time,
      sync_status=incoming.sync_status,
      version=ast_product_official_info.version+1;

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
