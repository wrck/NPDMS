-- PRE-04: promote the eight established UI fields into SOL-owned business columns.
-- Keep source extension rows, frozen bindings, revision identity, timestamps and versions unchanged.

ALTER TABLE sol_requirement_analysis
    ADD COLUMN transmission_current_options JSON NULL,
    ADD COLUMN traffic_new_connections TEXT NULL,
    ADD COLUMN traffic_concurrency TEXT NULL,
    ADD COLUMN traffic_throughput TEXT NULL,
    ADD COLUMN business_device_details JSON NULL,
    ADD COLUMN ip_management_resources TEXT NULL,
    ADD COLUMN ip_public_resources TEXT NULL,
    ADD COLUMN operations_management_options JSON NULL;

UPDATE sol_requirement_analysis r
JOIN plt_entity_extension_value e ON e.tenant_id = r.tenant_id AND e.deleted = b'0'
    AND e.owner_module = 'SOL' AND e.entity_type = 'REQUIREMENT_ANALYSIS'
    AND e.entity_id = r.id AND e.revision_id = 0
SET
    r.transmission_current_options = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRANSMISSION_CURRENT_OPTIONS')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRANSMISSION_CURRENT_OPTIONS')) = 'NULL' THEN NULL ELSE JSON_EXTRACT(e.values_json, '$.TRANSMISSION_CURRENT_OPTIONS') END,
    r.traffic_new_connections = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_NEW_CONNECTIONS')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_NEW_CONNECTIONS')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_NEW_CONNECTIONS')) END,
    r.traffic_concurrency = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_CONCURRENCY')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_CONCURRENCY')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_CONCURRENCY')) END,
    r.traffic_throughput = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_THROUGHPUT')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_THROUGHPUT')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_THROUGHPUT')) END,
    r.business_device_details = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.BUSINESS_DEVICE_DETAILS')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.BUSINESS_DEVICE_DETAILS')) = 'NULL' THEN NULL ELSE JSON_EXTRACT(e.values_json, '$.BUSINESS_DEVICE_DETAILS') END,
    r.ip_management_resources = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.IP_MANAGEMENT_RESOURCES')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.IP_MANAGEMENT_RESOURCES')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.IP_MANAGEMENT_RESOURCES')) END,
    r.ip_public_resources = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.IP_PUBLIC_RESOURCES')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.IP_PUBLIC_RESOURCES')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.IP_PUBLIC_RESOURCES')) END,
    r.operations_management_options = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.OPERATIONS_MANAGEMENT_OPTIONS')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.OPERATIONS_MANAGEMENT_OPTIONS')) = 'NULL' THEN NULL ELSE JSON_EXTRACT(e.values_json, '$.OPERATIONS_MANAGEMENT_OPTIONS') END;

ALTER TABLE sol_requirement_analysis_revision
    ADD COLUMN transmission_current_options JSON NULL,
    ADD COLUMN traffic_new_connections TEXT NULL,
    ADD COLUMN traffic_concurrency TEXT NULL,
    ADD COLUMN traffic_throughput TEXT NULL,
    ADD COLUMN business_device_details JSON NULL,
    ADD COLUMN ip_management_resources TEXT NULL,
    ADD COLUMN ip_public_resources TEXT NULL,
    ADD COLUMN operations_management_options JSON NULL;

UPDATE sol_requirement_analysis_revision r
JOIN plt_entity_extension_value e ON e.tenant_id = r.tenant_id AND e.deleted = b'0'
    AND e.owner_module = 'SOL' AND e.entity_type = 'REQUIREMENT_ANALYSIS'
    AND e.entity_id = r.entity_id AND e.revision_id = r.id
SET
    r.transmission_current_options = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRANSMISSION_CURRENT_OPTIONS')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRANSMISSION_CURRENT_OPTIONS')) = 'NULL' THEN NULL ELSE JSON_EXTRACT(e.values_json, '$.TRANSMISSION_CURRENT_OPTIONS') END,
    r.traffic_new_connections = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_NEW_CONNECTIONS')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_NEW_CONNECTIONS')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_NEW_CONNECTIONS')) END,
    r.traffic_concurrency = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_CONCURRENCY')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_CONCURRENCY')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_CONCURRENCY')) END,
    r.traffic_throughput = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_THROUGHPUT')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_THROUGHPUT')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.TRAFFIC_THROUGHPUT')) END,
    r.business_device_details = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.BUSINESS_DEVICE_DETAILS')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.BUSINESS_DEVICE_DETAILS')) = 'NULL' THEN NULL ELSE JSON_EXTRACT(e.values_json, '$.BUSINESS_DEVICE_DETAILS') END,
    r.ip_management_resources = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.IP_MANAGEMENT_RESOURCES')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.IP_MANAGEMENT_RESOURCES')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.IP_MANAGEMENT_RESOURCES')) END,
    r.ip_public_resources = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.IP_PUBLIC_RESOURCES')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.IP_PUBLIC_RESOURCES')) = 'NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(e.values_json, '$.IP_PUBLIC_RESOURCES')) END,
    r.operations_management_options = CASE WHEN JSON_TYPE(JSON_EXTRACT(e.values_json, '$.OPERATIONS_MANAGEMENT_OPTIONS')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.values_json, '$.OPERATIONS_MANAGEMENT_OPTIONS')) = 'NULL' THEN NULL ELSE JSON_EXTRACT(e.values_json, '$.OPERATIONS_MANAGEMENT_OPTIONS') END;
