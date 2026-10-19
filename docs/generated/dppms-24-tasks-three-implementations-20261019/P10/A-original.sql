-- P10 / 原通用引擎 / Owner适配器 / 2026-10-19 汇总
-- 只读代码摘录。本轮没有执行以下来源SQL、DML、临时表DDL或任何同步。
-- MyBatis动态标签以注释说明，参数仍是#{...}/:...；这不是可以整体直接执行的脚本。
-- 当前源范围、queryRef、业务转换、运行前后置和验证边界见本任务README。
-- 相关Mapper声明可能服务多个路径，存在声明不等于本任务已调用/已执行；实际调用位置另列。

-- ===== 1. 实际来源查询 / 本地或受控输入 =====

-- [CUSTOMER] EXACT_DEFINITION_SQL; source=D:/CodexTaskArtifacts/project-detail-followup/config-correction-20261015/candidate-commands/P10.json
SELECT AccountId,AccountNumber,erpCustomerCode,Name,Telephone1,EMailAddress1,Address1_Composite,new_mkt_unit_id,new_mkt_unit_idName,new_industry_level1_id,new_industry_level1_idName,new_industry_level2_id,new_industry_level2_idName,new_industry_level3_id,new_industry_level3_idName,new_industry_level4_id,new_industry_level4_idName,StateCode,ModifiedOn,CASE WHEN Name IS NULL OR LTRIM(RTRIM(Name))='' THEN 'MISSING_CUSTOMER_NAME' WHEN LEN(Name)>128 THEN 'CUSTOMER_NAME_TOO_LONG' WHEN COUNT(*) OVER (PARTITION BY AccountNumber)>1 THEN 'DUPLICATE_CUSTOMER_CODE' ELSE NULL END AS migrationIssue FROM DPtech_v_account;

-- ===== 2. 目标/辅助查询与前置 =====

-- cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper.selectIncludingDeletedForUpdate; pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml:5; kind=select
SELECT * FROM cus_customer_master WHERE tenant_id=#{tenantId} AND id=#{id} FOR UPDATE;

-- cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper.selectIncludingDeleted; pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml:45; kind=select
SELECT *
        FROM cus_customer_master
        WHERE tenant_id = #{tenantId}
          AND id = #{id};

-- cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper.selectVisibleById; pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml:144; kind=select
SELECT *
        FROM cus_customer_master
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.customerId}
          AND deleted = b'0'
         /* IF test=!query.allScope */ AND (
             /* FOREACH collection=query.scopeSlices; item=slice; separator= OR  */ (
                1 = 1
                 /* IF test=!slice.departmentCodes.isEmpty() */ AND department_code IN
                     /* FOREACH collection=slice.departmentCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.marketCodes.isEmpty() */ AND market_code IN
                     /* FOREACH collection=slice.marketCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.systemCodes.isEmpty() */ AND system_code IN
                     /* FOREACH collection=slice.systemCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.expendCodes.isEmpty() */ AND expend_code IN
                     /* FOREACH collection=slice.expendCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.industryCodes.isEmpty() */ AND industry_code IN
                     /* FOREACH collection=slice.industryCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                ) /* repeat with separator= OR ; END FOREACH */ 
            ) /* END IF */;

-- cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper.selectVisibleCount; pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml:192; kind=select
SELECT COUNT(*)
        FROM cus_customer_master
        WHERE tenant_id = #{query.tenantId}
         /* CHOOSE  */ /* WHEN test="DELETED".equals(query.lifecycleStatus) */ AND deleted = b'1' /* END WHEN */ 
             /* OTHERWISE  */ AND deleted = b'0' /* END OTHERWISE */ /* END CHOOSE */ 
         /* IF test=query.code != null and query.code != '' */ AND code LIKE CONCAT(#{query.code}, '%') /* END IF */ 
         /* IF test=query.name != null and query.name != '' */ AND name LIKE CONCAT('%', #{query.name}, '%') /* END IF */ 
         /* IF test=query.departmentCode != null and query.departmentCode != '' */ AND department_code = #{query.departmentCode} /* END IF */ 
         /* IF test=query.marketCode != null and query.marketCode != '' */ AND market_code = #{query.marketCode} /* END IF */ 
         /* IF test=query.systemCode != null and query.systemCode != '' */ AND system_code = #{query.systemCode} /* END IF */ 
         /* IF test=query.expendCode != null and query.expendCode != '' */ AND expend_code = #{query.expendCode} /* END IF */ 
         /* IF test=query.industryCode != null and query.industryCode != '' */ AND industry_code = #{query.industryCode} /* END IF */ 
         /* IF test=query.lifecycleStatus != null and query.lifecycleStatus != '' */ AND lifecycle_status = #{query.lifecycleStatus} /* END IF */ 
         /* IF test=query.sourceType != null and query.sourceType != '' */ AND source_type = #{query.sourceType} /* END IF */ 
         /* IF test=!query.allScope */ AND (
             /* FOREACH collection=query.scopeSlices; item=slice; separator= OR  */ (
                1 = 1
                 /* IF test=!slice.departmentCodes.isEmpty() */ AND department_code IN
                     /* FOREACH collection=slice.departmentCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.marketCodes.isEmpty() */ AND market_code IN
                     /* FOREACH collection=slice.marketCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.systemCodes.isEmpty() */ AND system_code IN
                     /* FOREACH collection=slice.systemCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.expendCodes.isEmpty() */ AND expend_code IN
                     /* FOREACH collection=slice.expendCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.industryCodes.isEmpty() */ AND industry_code IN
                     /* FOREACH collection=slice.industryCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                ) /* repeat with separator= OR ; END FOREACH */ 
            ) /* END IF */;

-- cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper.selectVisibleList; pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml:198; kind=select
/* BIND name=pageOffset; value=(query.pageParam.pageNo - 1) * query.pageParam.pageSize */  /* END BIND */ 
        SELECT *
        FROM cus_customer_master
        WHERE tenant_id = #{query.tenantId}
         /* CHOOSE  */ /* WHEN test="DELETED".equals(query.lifecycleStatus) */ AND deleted = b'1' /* END WHEN */ 
             /* OTHERWISE  */ AND deleted = b'0' /* END OTHERWISE */ /* END CHOOSE */ 
         /* IF test=query.code != null and query.code != '' */ AND code LIKE CONCAT(#{query.code}, '%') /* END IF */ 
         /* IF test=query.name != null and query.name != '' */ AND name LIKE CONCAT('%', #{query.name}, '%') /* END IF */ 
         /* IF test=query.departmentCode != null and query.departmentCode != '' */ AND department_code = #{query.departmentCode} /* END IF */ 
         /* IF test=query.marketCode != null and query.marketCode != '' */ AND market_code = #{query.marketCode} /* END IF */ 
         /* IF test=query.systemCode != null and query.systemCode != '' */ AND system_code = #{query.systemCode} /* END IF */ 
         /* IF test=query.expendCode != null and query.expendCode != '' */ AND expend_code = #{query.expendCode} /* END IF */ 
         /* IF test=query.industryCode != null and query.industryCode != '' */ AND industry_code = #{query.industryCode} /* END IF */ 
         /* IF test=query.lifecycleStatus != null and query.lifecycleStatus != '' */ AND lifecycle_status = #{query.lifecycleStatus} /* END IF */ 
         /* IF test=query.sourceType != null and query.sourceType != '' */ AND source_type = #{query.sourceType} /* END IF */ 
         /* IF test=!query.allScope */ AND (
             /* FOREACH collection=query.scopeSlices; item=slice; separator= OR  */ (
                1 = 1
                 /* IF test=!slice.departmentCodes.isEmpty() */ AND department_code IN
                     /* FOREACH collection=slice.departmentCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.marketCodes.isEmpty() */ AND market_code IN
                     /* FOREACH collection=slice.marketCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.systemCodes.isEmpty() */ AND system_code IN
                     /* FOREACH collection=slice.systemCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.expendCodes.isEmpty() */ AND expend_code IN
                     /* FOREACH collection=slice.expendCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                 /* IF test=!slice.industryCodes.isEmpty() */ AND industry_code IN
                     /* FOREACH collection=slice.industryCodes; item=code; open=(; separator=,; close=) */ (#{code}) /* repeat with separator=,; END FOREACH */ /* END IF */ 
                ) /* repeat with separator= OR ; END FOREACH */ 
            ) /* END IF */
        ORDER BY id DESC
        LIMIT #{pageOffset}, #{query.pageParam.pageSize};

-- ===== 3. 插入语句与明确标记的动态模板 =====

-- BaseMapper.insert:cus_customer_master; pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/dal/dataobject/customer/CustomerMasterDO.java:1; kind=orm_template
-- ORM_BASEMAPPER_COLUMN_TEMPLATE: 当前Owner存在BaseMapper.insert调用；下列为列/参数对应展示，不是独立XML或实测SQL。
-- NULL省略、自动填充、逻辑删除、typeHandler由实际DO注解、MyBatis-Plus全局策略及运行值决定；不要直接执行本模板。
INSERT INTO `cus_customer_master` (`id`, `code`, `name`, `short_name`, `customer_level`, `lifecycle_status`, `source_type`, `source_key`, `source_version`, `sync_status`, `data_as_of`, `reconciliation_pending`, `temporary_reason`, `contact_phone`, `contact_email`, `department_code`, `department_name`, `market_code`, `market_name`, `system_code`, `system_name`, `expend_code`, `expend_name`, `industry_code`, `industry_name`, `address`, `remark`, `version`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES (:orm_id, :orm_code, :orm_name, :orm_short_name, :orm_customer_level, :orm_lifecycle_status, :orm_source_type, :orm_source_key, :orm_source_version, :orm_sync_status, :orm_data_as_of, :orm_reconciliation_pending, :orm_temporary_reason, :orm_contact_phone, :orm_contact_email, :orm_department_code, :orm_department_name, :orm_market_code, :orm_market_name, :orm_system_code, :orm_system_name, :orm_expend_code, :orm_expend_name, :orm_industry_code, :orm_industry_name, :orm_address, :orm_remark, :orm_version, :orm_creator, :orm_create_time, :orm_updater, :orm_update_time, :orm_deleted, :orm_tenant_id);

-- ===== 4. 条件更新 / 关联后处理 / 缓存删除 =====

-- cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper.updatePlatformFieldsByVersion; pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml:9; kind=update
UPDATE cus_customer_master
         SET /* dynamic SET trims trailing commas */ /* IF test=query.updateName */ name = #{query.name}, /* END IF */ 
             /* IF test=query.updateShortName */ short_name = #{query.shortName}, /* END IF */ 
             /* IF test=query.updateRemark */ remark = #{query.remark}, /* END IF */ 
             /* IF test=query.updateCustomerLevel */ customer_level = #{query.customerLevel}, /* END IF */ 
             /* IF test=query.updateClassification */ department_code = #{query.departmentCode},
                department_name = #{query.departmentName},
                market_code = #{query.marketCode},
                market_name = #{query.marketName},
                system_code = #{query.systemCode},
                system_name = #{query.systemName},
                expend_code = #{query.expendCode},
                expend_name = #{query.expendName},
                industry_code = #{query.industryCode},
                industry_name = #{query.industryName}, /* END IF */ 
            version = version + 1,
            update_time = CURRENT_TIMESTAMP
        WHERE id = #{query.customerId}
          AND tenant_id = #{query.tenantId}
          AND version = #{query.expectedVersion}
          AND deleted = b'0';

-- cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper.updateLifecycleByVersion; pms-module-customer/src/main/resources/mapper/customer/CustomerMasterMapper.xml:53; kind=update
UPDATE cus_customer_master
        SET lifecycle_status = #{query.targetStatus},
            deleted = #{query.targetDeleted},
            version = version + 1,
            update_time = CURRENT_TIMESTAMP
        WHERE tenant_id = #{query.tenantId}
          AND id = #{query.customerId}
          AND lifecycle_status = #{query.expectedStatus}
          AND version = #{query.expectedVersion}
          AND deleted = #{query.expectedDeleted};

-- ===== 5. 已有运行时临时表语句（仅定义摘录，本轮未执行） =====
-- 本方式未定位到独立SQL原件；见实际Java/API/ORM路径与本任务处理逻辑，不编造SQL。
