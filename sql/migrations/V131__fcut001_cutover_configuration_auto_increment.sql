-- F-CUT-001: MySQL runtime uses the platform AUTO id strategy.
ALTER TABLE `cut_cutover_configuration_revision`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `cut_cutover_checklist_item_definition_revision`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `cut_cutover_checklist_binding_rule_revision`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT;
