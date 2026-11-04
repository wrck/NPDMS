-- 正式环境基础同步任务配置。用户明确授权；仅注册，不运行同步。
-- 基于178只读快照去重：SPMS 23项、CRM 2项；保留参数，不复制运行和绑定历史。
-- 在 B531 后、V532 前使用独立版本 531.2；撤回的 531.1 不使用。
SET NAMES utf8mb4;
CREATE TEMPORARY TABLE migration_task_guard (ok TINYINT NOT NULL CHECK (ok=1));
INSERT INTO migration_task_guard SELECT IF(COUNT(*)=1,1,0) FROM system_tenant WHERE id=1 AND deleted=b'0';
INSERT INTO migration_task_guard
SELECT IF(COUNT(*)=1,1,0) FROM int_sync_connection c JOIN infra_data_source_config d ON d.id=c.data_source_id AND d.deleted=b'0'
WHERE c.tenant_id=1 AND c.deleted=b'0' AND c.name='SPMS正式连接' AND d.name='SPMS正式数据源' AND d.username='sdp'
AND d.url='jdbc:mysql://spms.dptech.com:3306/dpspms?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true';
INSERT INTO migration_task_guard
SELECT IF(COUNT(*)=1,1,0) FROM int_sync_connection c JOIN infra_data_source_config d ON d.id=c.data_source_id AND d.deleted=b'0'
WHERE c.tenant_id=1 AND c.deleted=b'0' AND c.name='CRM正式连接' AND d.name='CRM正式数据源' AND d.username='pms'
AND d.url='jdbc:sqlserver://172.17.74.18:1433;databaseName=crm_MSCRM;encrypt=true;trustServerCertificate=true';
SET @migration_spms=(SELECT c.id FROM int_sync_connection c JOIN infra_data_source_config d ON d.id=c.data_source_id WHERE c.tenant_id=1 AND c.deleted=b'0' AND d.deleted=b'0' AND c.name='SPMS正式连接' AND d.name='SPMS正式数据源');
SET @migration_crm=(SELECT c.id FROM int_sync_connection c JOIN infra_data_source_config d ON d.id=c.data_source_id WHERE c.tenant_id=1 AND c.deleted=b'0' AND d.deleted=b'0' AND c.name='CRM正式连接' AND d.name='CRM正式数据源');
CREATE TEMPORARY TABLE migration_expected_base_tasks (
 id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(128) NOT NULL, task_code VARCHAR(16) NOT NULL,
 source_system VARCHAR(32) NOT NULL, target VARCHAR(8) NOT NULL, batch_size INT NOT NULL, max_rows INT NOT NULL,
 UNIQUE KEY uk_expected(target, task_code)
);
INSERT INTO migration_expected_base_tasks(id,name,task_code,source_system,target,batch_size,max_rows) VALUES
  (8996100000000000001, '公司与部门独立目录', 'BASE_P01', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000002, '账号、历史任职、角色及公司部门范围', 'BASE_P04', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000003, '系统字典类型及字典项', 'BASE_P08', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000004, 'CRM客户主档', 'BASE_P10', 'CRM', 'crm', 500, 1000000),
  (8996100000000000005, 'CRM产品主档', 'BASE_P11', 'CRM', 'crm', 1, 10000),
  (8996100000000000006, 'DPPMS缺失产品补齐', 'BASE_P12', 'DPPMS', 'spms', 500, 100000),
  (8996100000000000007, '项目执行单头', 'BASE_P13', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000008, 'ERP销售订单与订单行', 'BASE_P14', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000009, '合同及全量发货历史', 'BASE_P15', 'DPPMS', 'spms', 500, 5000000),
  (8996100000000000010, '项目主档及独立来源身份', 'BASE_P16', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000011, '项目合同关系历史', 'BASE_P18', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000012, '项目相关方及历史状态属性', 'BASE_P19', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000013, '项目真实产品历史清单', 'BASE_P20', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000014, '租赁与配置展示缓存', 'BASE_P21', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000015, '项目产品实施范围历史', 'BASE_P22', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000016, '合同收款计划展示缓存', 'BASE_P23', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000017, '合同回款信息', 'BASE_P24', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000018, '设备续保历史', 'BASE_P25', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000019, '正式发货设备基础承接', 'BASE_P30', 'DPPMS', 'spms', 500, 5000000),
  (8996100000000000020, '项目成员及最终客户联系人历史', 'BASE_P31', 'DPPMS', 'spms', 1000, 1000000),
  (8996100000000000021, '设备安装历史', 'BASE_P33', 'DPPMS', 'spms', 500, 5000000),
  (8996100000000000022, '出厂版本历史', 'BASE_P34', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000023, '软件批次及版本历史', 'BASE_P35', 'DPPMS', 'spms', 500, 5000000),
  (8996100000000000024, '整表许可历史', 'BASE_P37', 'DPPMS', 'spms', 500, 1000000),
  (8996100000000000025, '迁移项目模板分配', 'BASE_P38', 'DPPMS', 'spms', 500, 1000000);
-- 软删除或同连接既有参数不一致均停止，不覆盖任何任务或历史。
INSERT INTO migration_task_guard
SELECT IF(EXISTS(SELECT 1 FROM migration_expected_base_tasks e JOIN int_base_sync_task t
 ON t.tenant_id=1 AND t.task_code=e.task_code AND t.connection_id=IF(e.target='crm',@migration_crm,@migration_spms)
 WHERE t.deleted<>b'0' OR BINARY t.name<>BINARY e.name OR BINARY t.source_system<>BINARY e.source_system
 OR t.batch_size<>e.batch_size OR t.max_rows<>e.max_rows),0,1);
INSERT INTO migration_task_guard
SELECT IF(EXISTS(SELECT 1 FROM migration_expected_base_tasks e JOIN int_base_sync_task t ON t.id=e.id
 WHERE NOT(t.tenant_id=1 AND t.task_code=e.task_code AND t.connection_id=IF(e.target='crm',@migration_crm,@migration_spms))),0,1);
INSERT INTO int_base_sync_task(id,tenant_id,name,task_code,source_system,connection_id,batch_size,max_rows,creator,updater)
SELECT e.id,1,e.name,e.task_code,e.source_system,IF(e.target='crm',@migration_crm,@migration_spms),e.batch_size,e.max_rows,
 'production-base-sync-20261102','production-base-sync-20261102'
FROM migration_expected_base_tasks e
WHERE NOT EXISTS(SELECT 1 FROM int_base_sync_task t WHERE t.tenant_id=1 AND t.task_code=e.task_code
 AND t.connection_id=IF(e.target='crm',@migration_crm,@migration_spms));
DROP TEMPORARY TABLE migration_expected_base_tasks;
DROP TEMPORARY TABLE migration_task_guard;
SET @migration_spms=NULL; SET @migration_crm=NULL;
