-- F-AST-001: 固化设备公司、办事处归属；存量由 AST Owner 分批重建，不推测缺失归属。
ALTER TABLE ast_device
  ADD COLUMN company_id BIGINT NULL COMMENT '所属公司 ID',
  ADD COLUMN company_name VARCHAR(255) NULL COMMENT '所属公司名称',
  ADD COLUMN department_id BIGINT NULL COMMENT '所属办事处/部门 ID',
  ADD COLUMN department_code VARCHAR(64) NULL COMMENT '所属办事处/部门编码',
  ADD COLUMN department_name VARCHAR(255) NULL COMMENT '所属办事处/部门名称',
  ADD COLUMN organization_source VARCHAR(20) NOT NULL DEFAULT 'UNRESOLVED' COMMENT 'PROJECT/CONTRACT/UNRESOLVED',
  ADD COLUMN organization_updated_at DATETIME(3) NULL COMMENT '归属重建时间',
  ADD INDEX idx_device_organization (tenant_id, deleted, company_id, department_id, id);
