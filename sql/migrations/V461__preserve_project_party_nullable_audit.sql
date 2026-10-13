-- Approved: use the existing party fields; preserve an unknown historical actor as NULL.
ALTER TABLE proj_project_party
  MODIFY creator VARCHAR(64) NULL DEFAULT '' COMMENT '创建人',
  MODIFY updater VARCHAR(64) NULL DEFAULT '' COMMENT '更新人';
