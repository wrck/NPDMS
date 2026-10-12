-- 2026-10-12需求方整表裁决：复用项目联系人，不制造购货方客户主档。
ALTER TABLE cus_project_customer_contact_relation
  ADD COLUMN project_party_id BIGINT NULL COMMENT '联系人所属的项目最终客户相关方',
  ADD COLUMN contact_code VARCHAR(45) NULL COMMENT '联系人业务编码',
  ADD COLUMN registration_source VARCHAR(2) NULL COMMENT '登记来源：1项目信息、2手工成员',
  ADD COLUMN effective_from DATETIME(3) NULL COMMENT '联系人区间开始',
  ADD COLUMN effective_to DATETIME(3) NULL COMMENT '联系人区间结束',
  MODIFY COLUMN customer_id BIGINT NULL,
  MODIFY COLUMN customer_contact_id BIGINT NULL,
  ADD CONSTRAINT ck_cus_project_contact_affiliation CHECK (
    (customer_id IS NOT NULL AND customer_contact_id IS NOT NULL)
    OR (customer_id IS NULL AND customer_contact_id IS NULL
        AND project_party_id IS NOT NULL AND primary_flag=b'0')),
  ADD CONSTRAINT ck_cus_project_contact_interval CHECK (
    effective_from IS NULL OR effective_to IS NULL OR effective_to>=effective_from);
