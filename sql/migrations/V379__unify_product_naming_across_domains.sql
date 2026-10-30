-- V379: 全库同含义字段统一命名为产品/物料双读法规范名（接续 V378 订单行更名裁决）
-- 裁决（2026-09-29）：业务表中与规范名同含义的字段一并更名，数据原样保留：
--   com_delivery_scope:  item_code→product_code、item_desc→product_desc
--                        （索引 idx_scope_item 更名 idx_scope_product，列集不变）
--   imp_arrival_line:    model_code→product_model（到货行物料型号；索引列引用随更名自动更新）
--   imp_eng_material_requisition / imp_eng_external_procurement:
--                        material_code→product_code、material_name→product_name
--   imp_eng_external_procurement: model→product_model（型号；与 specification 规格并存为两个不同属性）
-- 保留（非同含义或有规格背书）：imp_eng_material_requisition/imp_eng_external_procurement.specification
--   （规格/规格型号，与型号不同属性，UI 标签即"规格"）；imp_eng_material_exchange_serial.item_code
--   （同表 product_code 为换货产品快照、item_code 为订单行物料快照，两个不同业务事实，09-24 规格
--   明确保留）；imp_eng_risk/kno_announcement_check/plt_authorization/plt_collection_template.device_model
--   与 pms_equipment_retired.model（设备型号，设备维度快照字段，见 open-questions Q-NAMING-20260929-001）；
--   cut_*/sol_preparation_* 的 item_code/item_name 为审批项/工勘项语义；bpm_process_definition_info.simple_model
--   为设计器模型 JSON；外部来源表 pm_*_from_erp 源列名与 fcom001_v70_* 历史表不属业务命名统一范围。
ALTER TABLE `com_delivery_scope` DROP INDEX `idx_scope_item`;
ALTER TABLE `com_delivery_scope`
  CHANGE COLUMN `item_code` `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL
    COMMENT '产品编码/物料编码（订单行）',
  CHANGE COLUMN `item_desc` `product_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL
    COMMENT '产品描述/物料描述';
ALTER TABLE `com_delivery_scope`
  ADD INDEX `idx_scope_product` (`tenant_id`, `product_code`, `scope_status`, `project_id`);
ALTER TABLE `imp_arrival_line` DROP CHECK `chk_imp_arrival_line_scope`;
ALTER TABLE `imp_arrival_line`
  CHANGE COLUMN `model_code` `product_model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '产品型号/物料型号（原model_code）';
-- CHECK 约束不随列更名自动更新（MySQL 会拒绝更名被约束引用的列），按原条款以新列名重建：
ALTER TABLE `imp_arrival_line`
  ADD CONSTRAINT `chk_imp_arrival_line_scope` CHECK (
    (`scope_type` = 'DEVICE'
      AND `device_id` IS NOT NULL AND `device_assignment_version` IS NOT NULL
      AND `order_line_id` IS NULL)
    OR (`scope_type` = 'ORDER_MODEL_QUANTITY'
      AND `device_id` IS NULL AND `order_line_id` IS NOT NULL
      AND (`product_code` IS NOT NULL OR `product_model` IS NOT NULL))
  );
ALTER TABLE `imp_eng_material_requisition`
  CHANGE COLUMN `material_code` `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '产品编码/物料编码',
  CHANGE COLUMN `material_name` `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
    COMMENT '产品名称/物料名称';
ALTER TABLE `imp_eng_external_procurement`
  CHANGE COLUMN `material_code` `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '产品编码/物料编码',
  CHANGE COLUMN `material_name` `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
    COMMENT '产品名称/物料名称（含服务名称）',
  CHANGE COLUMN `model` `product_model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '产品型号/物料型号（原model）';
