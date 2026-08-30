-- F-CUT-001 / CUT-09 / CUT-10: risk and survey matrix data contract.
ALTER TABLE `cut_cutover_checklist_item_definition_revision`
  ADD COLUMN `business_category_code` varchar(64) DEFAULT NULL
    AFTER `item_type_code`,
  ADD KEY `idx_cut_config_item_category`
    (`tenant_id`, `configuration_revision_id`, `item_type_code`,
     `business_category_code`, `status_code`, `sort_order`);

ALTER TABLE `cut_cutover_checklist_binding_rule_revision`
  ADD COLUMN `required_result` bit(1) DEFAULT NULL AFTER `priority`;
