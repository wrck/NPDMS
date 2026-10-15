ALTER TABLE plt_collection_request ADD COLUMN execution_purpose varchar(24) NULL,
  ADD CONSTRAINT ck_collection_execution_purpose CHECK(execution_purpose IS NULL OR execution_purpose IN ('PRECHECK','INSPECTION'));
