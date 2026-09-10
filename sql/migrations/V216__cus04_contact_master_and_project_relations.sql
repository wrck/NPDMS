-- CUS-04 revision 020: customer contact master and project-local contact records.
-- Preserve legacy contact identities and rows; this is a forward copy, never a destructive move.
CREATE TABLE cus_customer_contact (
  id BIGINT NOT NULL AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  department VARCHAR(64) NULL,
  title VARCHAR(64) NULL,
  mobile VARCHAR(32) NULL,
  phone VARCHAR(32) NULL,
  email VARCHAR(128) NULL,
  primary_flag BIT NOT NULL DEFAULT b'0',
  status TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  version INT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT NOT NULL DEFAULT b'0',
  tenant_id BIGINT NOT NULL DEFAULT 0,
  active_primary_customer_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN deleted=b'0' AND status=0 AND primary_flag=b'1' THEN customer_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cus_contact_primary (tenant_id, active_primary_customer_id),
  KEY idx_cus_contact_customer (tenant_id, customer_id, id),
  CONSTRAINT ck_cus_contact_status CHECK (status IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Reuse the original navigation identity for the customer-owned workbench; legacy data is retained.
UPDATE system_menu SET component='pms/customer/contacts/index', component_name='PmsCustomerContactsWorkbench',
  updater='demo-s1-contacts', update_time=NOW()
WHERE component='pms/project/customer-contact/index' AND deleted=b'0';

INSERT INTO cus_customer_contact
(id,customer_id,name,department,title,mobile,phone,email,primary_flag,status,remark,version,
 creator,create_time,updater,update_time,deleted,tenant_id)
SELECT id,customer_id,name,department,title,mobile,phone,email,primary_flag,status,remark,version,
 creator,create_time,updater,update_time,deleted,tenant_id
FROM pms_customer_contact;

CREATE TABLE cus_project_customer_contact_relation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  customer_contact_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  department VARCHAR(64) NULL,
  title VARCHAR(64) NULL,
  mobile VARCHAR(32) NULL,
  phone VARCHAR(32) NULL,
  email VARCHAR(128) NULL,
  role_code VARCHAR(64) NULL,
  primary_flag BIT NOT NULL DEFAULT b'0',
  primary_set_time DATETIME NULL,
  status TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  version INT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT NOT NULL DEFAULT b'0',
  deleted_by BIGINT NULL,
  deleted_at DATETIME NULL,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  active_primary_project_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN deleted=b'0' AND status=0 AND primary_flag=b'1' THEN project_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  -- The source identity remains occupied after soft deletion: default import cannot resurrect it.
  UNIQUE KEY uk_cus_project_contact_source (tenant_id, project_id, customer_contact_id),
  UNIQUE KEY uk_cus_project_contact_primary (tenant_id, active_primary_project_id),
  KEY idx_cus_project_contact_customer (tenant_id, customer_id, id),
  CONSTRAINT ck_cus_project_contact_status CHECK (status IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cus_contact_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  customer_contact_id BIGINT NOT NULL,
  project_relation_id BIGINT NULL,
  project_id BIGINT NULL,
  action_code VARCHAR(32) NOT NULL,
  before_values JSON NULL,
  after_values JSON NULL,
  actor_user_id BIGINT NOT NULL,
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_cus_contact_history_master (tenant_id, customer_contact_id, id),
  KEY idx_cus_contact_history_project (tenant_id, project_id, project_relation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
