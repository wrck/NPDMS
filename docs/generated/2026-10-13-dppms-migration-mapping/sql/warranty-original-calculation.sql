-- 来源：2026-10-13，已配置DPPMS原库，只读SHOW CREATE VIEW及information_schema.ROUTINES。
-- 原对象定义证据；不作为目标数据库的执行脚本。

CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `view_warranty_source` AS select `sb`.`id` AS `id`,`sb`.`barcode` AS `barcode`,`sb`.`com_barcode` AS `comBarCode`,(case when isnull(`fs`.`end_date`) then (`s`.`warrantyEndTime` - interval 1 day) else `fs`.`end_date` end) AS `old_warrantyEndTime`,(case when isnull(`fs`.`end_date`) then (ifnull((ifnull(`sb`.`warrantyStartDate`,`s`.`warrantyStartTime`) + interval if((ifnull(`sb`.`warrantyMonth`,0) = 0),(`c`.`warranty` * 12),`sb`.`warrantyMonth`) month),`s`.`warrantyEndTime`) - interval 1 day) else `fs`.`end_date` end) AS `warrantyEndTime`,(to_days(now()) - to_days((case when isnull(`fs`.`end_date`) then (`s`.`warrantyEndTime` - interval 1 day) else `fs`.`end_date` end))) AS `old_diff`,(to_days(now()) - to_days((case when isnull(`fs`.`end_date`) then (ifnull((ifnull(`sb`.`warrantyStartDate`,`s`.`warrantyStartTime`) + interval if((ifnull(`sb`.`warrantyMonth`,0) = 0),(`c`.`warranty` * 12),`sb`.`warrantyMonth`) month),`s`.`warrantyEndTime`) - interval 1 day) else `fs`.`end_date` end))) AS `diff`,ifnull(`sb`.`warrantyStartDate`,`s`.`warrantyStartTime`) AS `warrantyStartTime`,`s`.`warrantyStartTime` AS `old_warrantyStartTime`,`sb`.`item` AS `item`,`f`.`describe_` AS `describe_`,`f`.`itemname` AS `itemName`,(case when isnull(`grade`.`gradename`) then 'DPtech 维保服务,基本维保 5x10xNBD发出' else `grade`.`gradename` end) AS `gradeName`,(case when isnull(`grade`.`gradecode`) then '53030001' else `grade`.`gradecode` end) AS `gradeCode`,`s`.`packdate` AS `packdate`,`c`.`contract_code` AS `contract_code`,`c`.`contract_type` AS `contract_type`,`st`.`resolveName` AS `contract_type_name`,`c`.`project_name` AS `project_name`,`c`.`customer_name` AS `customer_name`,(case when (`t`.`officeCode` is not null) then `t`.`officeCode` else `c`.`office_code` end) AS `office_code`,(case when (`t`.`officeCode` is not null) then `t1`.`ocrName` else `d`.`ocrName` end) AS `office_name`,`c`.`marketCode` AS `marketCode`,`c`.`marketName` AS `marketName`,`c`.`systemId` AS `systemId`,`c`.`systemName` AS `systemName`,`c`.`warranty` AS `warranty`,if((ifnull(`sb`.`warrantyMonth`,0) > 0),`sb`.`warrantyMonth`,(`c`.`warranty` * 12)) AS `warrantyMonth`,`sb`.`barcode2` AS `barcode2`,`sb`.`item2` AS `item2`,`sb`.`syncTime` AS `syncTime` from (((((((((`fb_contract` `c` left join `fb_office_relationship` `t` on((`c`.`contract_code` = `t`.`contractNo`))) left join `department` `t1` on((`t`.`officeCode` = `t1`.`ocrCode`))) left join `fb_shipment` `s` on((`c`.`contract_id` = `s`.`con_id`))) left join `fb_shipment_barcode` `sb` on((`s`.`packlist_id` = `sb`.`pack_id`))) left join `fb_items` `f` on((`sb`.`item` = `f`.`item`))) left join `fb_service` `fs` on(((`sb`.`barcode` = `fs`.`barcode`) and (`s`.`packdate` < `fs`.`end_date`) and (`fs`.`state` = 1)))) left join `fb_warranty_grade` `grade` on((`fs`.`grade` = `grade`.`gradecode`))) left join `department` `d` on((`d`.`ocrCode` = `c`.`office_code`))) left join `sys_state_or_type` `st` on(((`st`.`resolveCode` = `c`.`contract_type`) and (`st`.`stCode` = '01')))) where ((`c`.`contract_type` in (0,11)) and (`sb`.`barcode` is not null) and (isnull(`sb`.`rma_no`) or (`sb`.`rma_no` = '') or (`sb`.`rma_no` = 'null')));

-- 原存储过程：dailyWarrantyContractStateTable
BEGIN
    set group_concat_max_len = 1024000;
    drop table if exists view_warranty_contract_state;
    CREATE TABLE IF NOT EXISTS view_warranty_contract_state (
        KEY contractNo (contractNo)
    )
    AS
    SELECT contract_code AS contractNo
        , SUM(DISTINCT IF(v.diff > 0, 1, IF(v.diff <= 0, -1, NULL))) AS diff
        , CASE 
            WHEN SUM(DISTINCT IF(v.diff > 0, 1, IF(v.diff <= 0, -1, NULL))) = -1 THEN '维保内'
            WHEN SUM(DISTINCT IF(v.diff > 0, 1, IF(v.diff <= 0, -1, NULL))) = 0 THEN '部分保外'
            WHEN SUM(DISTINCT IF(v.diff > 0, 1, IF(v.diff <= 0, -1, NULL))) = 1 THEN '维保外'
            ELSE NULL
        END AS warrantyStatusName
        , min(warrantyStartTime) AS warrantyStartTime
        , max(warrantyEndTime) AS warrantyEndTime
        , max(wg.sort) AS warrantyGrade
        , min(IF(wg.sort = 3, fs.begin_date, NULL)) AS warrantyGradeStartTime
        , max(IF(wg.sort = 3, fs.end_date, NULL)) AS warrantyGradeEndTime
        , group_concat(DISTINCT wg.gradecode ORDER BY wg.sort, wg.gradecode SEPARATOR '||') AS gradecodes
        , group_concat(DISTINCT wg.gradename ORDER BY wg.sort, wg.gradecode SEPARATOR '||') AS gradenames
        , group_concat(DISTINCT concat(IFNULL(fs.con_xb, v.contract_code), ':', wg.gradename, '(', Date(IFNULL(fs.begin_date, warrantyStartTime)), '~', Date(IFNULL(fs.end_date, warrantyEndTime)), ')') 
        	ORDER BY fs.begin_date, warrantyStartTime 
        	SEPARATOR '
'
    	) AS gradedesc
        , max(sg.id) IS NOT NULL AS hasRenewal
        , group_concat(DISTINCT concat(rs.con_xb, ':', sg.gradename, '(', Date(IFNULL(rs.begin_date, warrantyStartTime)), '~', Date(IFNULL(rs.end_date, warrantyEndTime)), ')') 
        	ORDER BY rs.begin_date 
        	SEPARATOR '
'
    	) AS renewalDesc
        , max(v.itemName REGEXP '^LIS.*[0-9]+Y') AS hasLiscense
        , group_concat(DISTINCT IF(v.itemName REGEXP '^LIS.*[0-9]+Y', trim(v.item), NULL)
            ORDER BY v.item
            SEPARATOR '||'
        ) AS liscenseCodes
        , group_concat(DISTINCT IF(v.itemName REGEXP '^LIS.*[0-9]+Y', concat(trim(v.item), ':', v.describe_), NULL)
            ORDER BY v.item
            SEPARATOR '
'
        ) AS liscenseDesc
        , MAX(IF(ol.itemCode IS NOT NULL, 1, 0)) AS wafService
        , min(IF(ol.itemCode IS NOT NULL, warrantyStartTime, NULL)) AS wafServiceStartTime
        , max(IF(ol.itemCode IS NOT NULL, warrantyEndTime, NULL)) AS wafServiceEndTime
        , group_concat(DISTINCT ol.itemCode SEPARATOR '||') AS itemCode
        , group_concat(DISTINCT ol.itemDesc SEPARATOR '||') AS itemDesc
    FROM view_warranty v
        LEFT JOIN pm_order_data_from_sap os
        ON os.contractNo = v.contract_code
            AND orderType = 0
        LEFT JOIN pm_order_line_from_sap ol
        ON ol.orderNumber = os.orderNumber
            AND os.orderType = ol.lineType
            AND ol.itemCode IN ('01100477', '01100478', '53010740', '53010741')
            AND ol.itemCode = v.item
        LEFT JOIN fb_warranty_grade wg
        ON v.gradeCode = wg.gradecode
            AND wg.sort > 0
        LEFT JOIN fb_service fs
        ON fs.grade = wg.gradeCode
            AND fs.barcode = v.barcode
    	--  AND fs.state = 1
        LEFT JOIN fb_service rs ON rs.barcode = v.barcode
        --  AND fs.grade = wg.gradeCode
    	--  AND fs.state = 1
        LEFT JOIN fb_warranty_grade sg ON rs.grade = sg.gradeCode
        --  and sg.sort > 0 
    -- where 
    GROUP BY contract_code;
END

-- 原存储过程：dailyWarrantyInfoTable
BEGIN
 

     SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 ;
     SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 ;
     SET @OLD_AUTOCOMMIT=@@AUTOCOMMIT, AUTOCOMMIT=0 ;
    
 
    DROP TABLE IF EXISTS view_warranty_temp ;
    CREATE TABLE IF NOT EXISTS view_warranty_temp ENGINE = MYISAM DEFAULT CHARSET=utf8 
    
    SELECT 
        vws.*, fi.`describe_` AS describe_2, fi.`itemname` AS itemName2, ppfs.`agentName`, ppfs.`finalCustomerName` 
    FROM
        view_warranty_source vws 
        LEFT JOIN fb_items fi 
        ON 1 = 0 
        LEFT JOIN `pm_project_property_from_sms` ppfs 
        ON 1 = 0 
    WHERE 1 = 0 ;
    
    
    
    INSERT /*+append parallel(view_warranty view_warranty_source)*/ INTO view_warranty_temp
    SELECT 
        *, NULL, NULL, NULL, NULL 
    FROM
       `view_warranty_source`;
    
    
    ALTER TABLE `view_warranty_temp`   
      ADD COLUMN `newId` BIGINT(20) NOT NULL AUTO_INCREMENT FIRST, 
      ADD PRIMARY KEY (`newId`),
      ADD INDEX contract_code(`contract_code`),
      ADD INDEX barcode(`barcode`),
      ADD INDEX comBarCode(`comBarCode`),
      ADD INDEX office_code_marketCode_systemId(`office_code`, `marketCode`, `systemId`),
      ADD INDEX marketCode_systemId(`marketCode`, `systemId`),
      ADD INDEX warrantyEndTime(`warrantyEndTime`),
      ADD INDEX item(`item`),
      ADD INDEX gradeCode(`gradeCode`),
      ADD INDEX diff(`diff`),
      -- ADD INDEX packdate(`packdate`),
      ADD INDEX barcode2(`barcode2`);
      
    
    UPDATE 
        view_warranty_temp ws 
        LEFT JOIN fb_items fi 
        ON fi.`item` = ws.`item2` 
    SET ws.`itemName2` = fi.`itemname`,
        ws.`describe_2` = fi.`describe_`
    WHERE ws.`item2` IS NOT NULL;
    
    
    UPDATE 
        `pm_project_property_from_sms` ppfs 
        INNER JOIN `pm_order_data_from_sap` pods 
        ON ppfs.`orderExecNumber` = pods.`orderExecNumber` 
        INNER JOIN view_warranty_temp vws 
        ON vws.`contract_code` = pods.`contractNo`
    SET vws.agentName = ppfs.agentName,
    vws.finalCustomerName = ppfs.finalCustomerName;

    
    RENAME table view_warranty TO view_warranty_old,
                 view_warranty_temp TO view_warranty,
                 view_warranty_old TO view_warranty_temp;
       
    SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS ;
    SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS ;
    SET AUTOCOMMIT=@OLD_AUTOCOMMIT ;
     
    CALL `dailyWarrantyContractStateTable`();
    CALL dailyProjectInfoCacheTable();
    
    
    
    END
