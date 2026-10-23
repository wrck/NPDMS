-- Transaction-only technical fixture. The caller inserts tasks and always rolls back this connection.
-- V485 draft 970485101 is ONLY an existing FK anchor: it is neither published nor executable.
-- No rule seed, platform/asset source, schema or committed data is changed.
INSERT INTO srv_inspection_attempt
  (id,tenant_id,task_id,attempt_no,mode,status_code,started_by,started_at)
VALUES
  (9701015201,1,9701015001,1,'ONLINE','ACTIVE',2,'2026-10-15 08:00:00'),
  (9701015202,1,9701015002,1,'ONLINE','ACTIVE',2,'2026-10-15 08:00:00');

INSERT INTO srv_inspection_task_device
  (id,tenant_id,task_id,device_id,serial_number,product_type_code,product_type_name,product_model)
VALUES
  (9701015111,1,9701015001,9701015101,'CONTRACT-SN-A','contract-fixture','契约测试类型','CONTRACT-MODEL-A'),
  (9701015112,1,9701015002,9701015102,'CONTRACT-SN-B','contract-fixture','契约测试类型','CONTRACT-MODEL-B');

INSERT INTO srv_inspection_task_rule_snapshot
  (id,tenant_id,task_id,attempt_id,device_id,rule_revision_id)
VALUES
  (9701015301,1,9701015001,9701015201,9701015101,970485101),
  (9701015302,1,9701015002,9701015202,9701015102,970485101);

INSERT INTO srv_inspection_execution
  (id,tenant_id,task_id,attempt_id,device_id,purpose_code,status_code,submitted_by,submitted_at,completed_at,
   collection_execution_id,collection_task_id,collection_result_version,collection_file_version_id,collection_status)
VALUES
  (9701015401,1,9701015001,9701015201,9701015101,'COLLECT','COMPLETE',2,'2026-10-15 08:00:00','2026-10-15 08:01:00',
   9701015491,'contract-collection-a',1,9701015492,'SUCCEEDED'),
  (9701015402,1,9701015002,9701015202,9701015102,'COLLECT','COMPLETE',2,'2026-10-15 08:00:00','2026-10-15 08:01:00',
   9701015493,'contract-collection-b',1,9701015494,'SUCCEEDED');

INSERT INTO srv_inspection_collection_receipt
  (id,tenant_id,task_id,execution_id,collection_execution_id,collection_task_id,result_version,file_version_id,
   external_status,actor_id,received_at)
VALUES
  (9701015411,1,9701015001,9701015401,9701015491,'contract-collection-a',1,9701015492,'SUCCEEDED',2,'2026-10-15 08:01:00');

INSERT INTO srv_inspection_detection_result
  (id,tenant_id,task_id,attempt_id,execution_id,rule_snapshot_id,device_id,rule_revision_id,outcome,
   measured_value,measured_unit,regex_matched,output_summary,detail_code,create_time)
VALUES
  (9701015501,1,9701015001,9701015201,9701015401,9701015301,9701015101,970485101,'ABNORMAL',
   15,'ms',b'1','CONTRACT-OUTPUT-A','THRESHOLD_NOT_MET','2026-10-15 08:02:00');

INSERT INTO srv_inspection_report_revision
  (id,tenant_id,task_id,attempt_id,revision_no,source_code,status_code,template_version,input_digest,generated_by,generated_at)
VALUES
  (9701015601,1,9701015001,9701015201,1,'PLATFORM','GENERATING','contract-test',REPEAT('a',64),2,'2026-10-15 08:03:00');

INSERT INTO srv_inspection_report_result (id,tenant_id,task_id,report_id,result_id)
VALUES (9701015611,1,9701015001,9701015601,9701015501);

INSERT INTO srv_inspection_report_file
  (id,tenant_id,task_id,report_id,format_code,artifact_id,file_version_no,reference_key,material_id,file_name,sha256)
VALUES
  (9701015701,1,9701015001,9701015601,'PDF',9701015791,1,'contract-report-v1',NULL,'contract-v1.pdf',REPEAT('b',64));
