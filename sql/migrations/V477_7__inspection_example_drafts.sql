-- 十类示例草稿：只展示配置入口和分类，不是正式生产规则包。
-- 原资料缺少独立《DPtech设备巡检判定信息表》，本地AST尚无权威产品类型映射。
-- 因此不填造设备命令、判定阈值、产品编码、安全审核或发布事实。
-- 示例只落本地业务基线租户1；后续由授权维护人员按权威资料补齐并正常审核发布。
DROP PROCEDURE IF EXISTS inspection_example_drafts;
DELIMITER $$
CREATE PROCEDURE inspection_example_drafts()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM system_tenant WHERE id=1 AND deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_EXAMPLE_TENANT_MISSING';
  END IF;
  CREATE TEMPORARY TABLE inspection_example_categories (
    ordinal_no int PRIMARY KEY, category_code varchar(32), category_name varchar(128)
  );
  INSERT INTO inspection_example_categories VALUES
    (1,'BASIC','基础'),(2,'OPERATING_STATUS','运行状态'),(3,'LOG','日志'),
    (4,'BUSINESS_STATUS','业务状态'),(5,'REDUNDANCY','冗余'),(6,'ROUTING','路由'),
    (7,'SECURITY','安全'),(8,'FORWARDING_CHANNEL','转发通道'),(9,'LOAD_BALANCING','负载均衡'),
    (10,'TRAFFIC_CLEANING','流量清洗');
  IF EXISTS (SELECT 1 FROM inspection_example_categories e LEFT JOIN system_dict_data d
    ON d.dict_type='pms_inspection_rule_category' AND d.value=e.category_code AND d.deleted=b'0' AND d.status=0
    WHERE d.id IS NULL OR d.label<>e.category_name) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_EXAMPLE_CATEGORY_SOURCE_MISMATCH';
  END IF;
  IF EXISTS (SELECT 1 FROM inspection_example_categories e JOIN srv_inspection_rule r
    ON r.id=970485000+e.ordinal_no OR (r.tenant_id=1 AND r.detection_id=CONCAT('EXAMPLE-INS-',e.category_code))
    WHERE r.id<>970485000+e.ordinal_no OR r.tenant_id<>1
      OR r.detection_id<>CONCAT('EXAMPLE-INS-',e.category_code)
      OR r.rule_name<>CONCAT('【示例草稿】',e.category_name,'检测') OR r.deleted<>b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_EXAMPLE_RULE_IDENTITY_CONFLICT';
  END IF;
  IF EXISTS (SELECT 1 FROM inspection_example_categories e JOIN srv_inspection_rule_revision r
    ON r.id=970485100+e.ordinal_no OR (r.tenant_id=1 AND r.rule_id=970485000+e.ordinal_no AND r.revision_no=1)
    WHERE r.id<>970485100+e.ordinal_no OR r.tenant_id<>1 OR r.rule_id<>970485000+e.ordinal_no
      OR r.revision_no<>1 OR r.deleted<>b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INS_EXAMPLE_REVISION_IDENTITY_CONFLICT';
  END IF;
  INSERT INTO srv_inspection_rule (id,tenant_id,detection_id,rule_name,version,creator,updater,deleted)
  SELECT 970485000+e.ordinal_no,1,CONCAT('EXAMPLE-INS-',e.category_code),CONCAT('【示例草稿】',e.category_name,'检测'),
    0,'inspection-example-20261015','inspection-example-20261015',b'0'
  FROM inspection_example_categories e WHERE NOT EXISTS (SELECT 1 FROM srv_inspection_rule r WHERE r.id=970485000+e.ordinal_no);
  INSERT INTO srv_inspection_rule_revision
    (id,tenant_id,rule_id,revision_no,status_code,rule_name_snapshot,inspection_item,description,
     category_code,category_name_snapshot,sort_order,version,creator,updater,deleted)
  SELECT 970485100+e.ordinal_no,1,970485000+e.ordinal_no,1,'DRAFT',CONCAT('【示例草稿】',e.category_name,'检测'),
    CONCAT(e.category_name,'检测配置示例'),
    '仅供配置演示，未发布，不能用于正式巡检。命令、预期结果、阈值、严重度和适用产品须由权威资料补齐；不得将此示例当作DPtech正式判定规则。',
    e.category_code,e.category_name,e.ordinal_no*10,0,'inspection-example-20261015','inspection-example-20261015',b'0'
  FROM inspection_example_categories e WHERE NOT EXISTS (SELECT 1 FROM srv_inspection_rule_revision r WHERE r.id=970485100+e.ordinal_no);
  DROP TEMPORARY TABLE inspection_example_categories;
END$$
DELIMITER ;
CALL inspection_example_drafts();
DROP PROCEDURE inspection_example_drafts;
