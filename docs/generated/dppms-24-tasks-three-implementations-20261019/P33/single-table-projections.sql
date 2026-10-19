-- P33 原表字段→正式承接的单表审阅投影（生成报告，非现有任务SQL）
-- 每个SELECT只读取一张原表；不在报告中执行。正式目标/转换逐字段见field-mapping.md。
-- raw__别名明确保留原值，尚未运行Java/Owner转换、加密、授权、引用解析或目标前值合并。
-- 该投影不取代A/B/C实际来源范围SQL；项目/账号/许可范围须由实际任务的前置核验固定。
-- id/租户/审计/版本/密文等生成列不可通过原ID或原文伪造；没有拼造INSERT SELECT跨库写入。

-- SOURCE pm_project_shipment; dialect=MYSQL
SELECT
  -- pm_project_shipment.id -> int_sync_binding / plt_migration_*（技术追溯）; 技术身份
  s.`id` AS `raw__id`,
  -- pm_project_shipment.projectId -> imp_eng_installation.project_id; 业务字段/正式关系
  s.`projectId` AS `raw__projectId`,
  -- pm_project_shipment.barcode -> imp_eng_installation.equipment_id；ast_device.sn（精确关联）; 业务字段/正式关系
  s.`barcode` AS `raw__sn`,
  -- pm_project_shipment.itemCode -> 正式ast_device_shipment.product_code及现有产品目录；本批948条逐项一致; 业务字段/正式关系
  s.`itemCode` AS `raw__itemCode`,
  -- pm_project_shipment.itemModel -> 正式产品目录关联；本任务不新增安装型号镜像; 业务字段/正式关系
  s.`itemModel` AS `raw__itemModel`,
  -- pm_project_shipment.itemName -> ast_product_official_info.product_name（目录关联读取）; 目录引用；待移除旧标签依赖
  s.`itemName` AS `raw__product_name`,
  -- pm_project_shipment.receiveName -> 正式com_shipment_package.receiver_name，经精确发货/装箱单关系核对; 业务字段/正式关系
  s.`receiveName` AS `raw__receiveName`,
  -- pm_project_shipment.emsNum -> 正式com_shipment_package.express_no; 业务字段/正式关系
  s.`emsNum` AS `raw__emsNum`,
  -- pm_project_shipment.emsCompany -> 正式装箱单carrier_name/已批准carrier_code，按实际原值及既有转换核对，不能互相替代; 业务字段/正式关系
  s.`emsCompany` AS `raw__emsCompany`,
  -- pm_project_shipment.packdate -> 正式发货及装箱单shipment_time，不推导安装日期; 业务字段/正式关系
  s.`packdate` AS `raw__packdate`,
  -- pm_project_shipment.contractNo -> 正式发货contract_no及合同关系，不盲目删-C后缀; 业务字段/正式关系
  s.`contractNo` AS `raw__contractNo`,
  -- pm_project_shipment.installAddress -> imp_eng_installation.install_location；imp_eng_installation.location_snapshot ＋ ast_address.full_address; 业务字段/正式关系
  s.`installAddress` AS `raw__full_address`,
  -- pm_project_shipment.chProjectId -> 本轮暂不迁入; 已批准暂缓
  s.`chProjectId` AS `raw__chProjectId`,
  -- pm_project_shipment.chContractNo -> 本轮暂不迁入; 已批准暂缓
  s.`chContractNo` AS `raw__chContractNo`,
  -- pm_project_shipment.transferProjectId -> 本轮暂不迁入; 已批准暂缓
  s.`transferProjectId` AS `raw__transferProjectId`,
  -- pm_project_shipment.transferContractNo -> 本轮暂不迁入; 已批准暂缓
  s.`transferContractNo` AS `raw__transferContractNo`,
  -- pm_project_shipment.transferFlag -> 本轮暂不迁入; 已批准暂缓
  s.`transferFlag` AS `raw__transferFlag`,
  -- pm_project_shipment.createTime -> imp_eng_installation.create_time；imp_eng_installation.install_time；imp_eng_installation.effective_from; 业务字段/正式关系
  s.`createTime` AS `raw__createTime`,
  -- pm_project_shipment.createBy -> imp_eng_installation.creator; 业务字段/正式关系
  s.`createBy` AS `raw__createBy`,
  -- pm_project_shipment.updateTime -> imp_eng_installation.update_time; 业务字段/正式关系
  s.`updateTime` AS `raw__updateTime`,
  -- pm_project_shipment.updateBy -> imp_eng_installation.updater; 业务字段/正式关系
  s.`updateBy` AS `raw__updateBy`
FROM `pm_project_shipment` s;
