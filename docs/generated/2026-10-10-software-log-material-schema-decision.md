# 软件日志功能依赖：正式材料表结构核对

状态：APPROVED_AND_APPLIED。2026-10-11用户批准仅补齐既有V398的version列，已通过指定Compose/Flyway入口执行。未绕过材料锁或授权。

## 已确认的运行问题

本任务59282调用既有日志下载接口，正式NativeGeneratedFileService使用DeliveryMaterialMapper读取plt_delivery_material，因正式模型包含version、实际库没有该列而失败，错误明确为Unknown column version。该依赖同样影响正式材料登记，不能通过省略并发版本或绕过文件Owner来规避。

## 结构来源

现有sql/migrations/V398__delivery_material_version.sql已定义version BIGINT NOT NULL DEFAULT 0，用于公共材料操作CAS，与file_version_no和business_revision_no含义不同。指定任务库没有V398成功回执。它是仓库既有正常材料功能的定义，本轮未新设计；依用户所有新增列须裁决的要求，落入当前库前仍单独提交。

## 整表35个当前字段

|字段|类型|可空|默认|含义|
|---|---|---|---|---|
|id|bigint|NO|NULL|主键|
|owner_module|varchar(64)|NO|NULL|来源实体所属模块|
|entity_type|varchar(64)|NO|NULL|来源实体类型|
|entity_id|bigint|NO|NULL|来源实体ID|
|type_code|varchar(64)|NO|NULL|材料类型编码|
|material_kind|varchar(16)|NO|FILE|材料证据锚 FILE=文件版本 / BUSINESS_RESULT=业务成果|
|requirement_id|bigint|YES|NULL|绑定的模板冻结要求（模板行材料直连要求）|
|project_id|bigint|YES|NULL|项目上下文（供项目级汇总）|
|business_object_type|varchar(64)|YES|NULL|业务成果对象类型（BUSINESS_RESULT 行，统一目录 entityType）|
|business_object_id|varchar(256)|YES|NULL|业务成果对象ID（type/objectId/resultId/formedAt 复合身份）|
|business_revision_no|bigint|YES|NULL|业务成果修订锚（如方案基线版本；无修订对象为 NULL）|
|file_reference_id|bigint|YES|NULL|统一文件引用ID（BUSINESS_RESULT 行为空）|
|file_artifact_id|bigint|YES|NULL|文件工件ID（BUSINESS_RESULT 行为空）|
|file_version_no|int|YES|NULL|文件版本号（BUSINESS_RESULT 行为空）|
|file_sha256|varchar(64)|YES|NULL|文件内容摘要（BUSINESS_RESULT 行为空）|
|file_name|varchar(255)|YES|NULL|文件名称（BUSINESS_RESULT 行为空）|
|title|varchar(255)|YES|NULL|材料标题|
|source_kind|varchar(16)|NO|UPLOAD|来源 UPLOAD/GENERATED/ASSOCIATED|
|status|varchar(16)|NO|ACTIVE|状态 ACTIVE/WITHDRAWN|
|tenant_id|bigint|NO|0|租户|
|creator|varchar(64)|YES||创建者|
|create_time|datetime(3)|NO|CURRENT_TIMESTAMP(3)|创建时间|
|updater|varchar(64)|YES||更新者|
|update_time|datetime(3)|NO|CURRENT_TIMESTAMP(3)|更新时间|
|deleted|bit(1)|NO|b'0'|逻辑删除|
|archive_status|varchar(32)|NO|NOT_REQUIRED|归档补偿状态 NOT_REQUIRED/PENDING_COMPENSATION/ARCHIVED/INVALID|
|archive_failure_code|varchar(64)|YES|NULL|归档失败原因编码|
|archive_retry_count|int|NO|0|归档补偿重试次数|
|archive_time|datetime(3)|YES|NULL|归档完成时间|
|source_identity_key|char(64)|YES|NULL|新登记来源版本身份SHA256（旧行保持NULL）|
|business_type_code|varchar(128)|YES|NULL|稳定业务类型/来源目录编码，不是模板要求编码|
|source_owner_module|varchar(64)|YES|NULL||
|source_entity_type|varchar(64)|YES|NULL||
|source_entity_id|bigint|YES|NULL||
|source_revision_id|bigint|YES|NULL||

## 唯一候选新增字段

|字段|类型|可空|默认|正式用途|
|---|---|---|---|---|
|version|BIGINT|否|0|公共材料操作的并发版本；不表示文件版本或业务修订|

建议在指定任务库应用已存在的V398，使正式材料表为36列；全部既有35列、材料ID、状态、文件锚、来源关系和历史审计保持，不执行业务行更新。本表无DPPMS字段承接，不增加镜像、表或扩展。实际执行须核对35列数据及现存回执，依既有数据库迁移入口实施；不补造原历史操作版本。

正常手工登记、日志解析回填和历史查询均已实际验收；随后659条历史及6条批次完成批准范围内的限定纠正。

## 2026-10-11实际迁移核验

V398成功回执已写入。执行前236条材料（比原预览多2条既有到货登记）纳入完整保护；原35列结构、全部236条原字段值、369条原回执逐值一致，新增version全部为0。原234条预览保留，新增两条未被更新。执行预检读事务曾持有结构锁，已仅释放本次零写入预检连接；脚本修正为启动Flyway前提交读取事务。

列依赖解决后，真实下载发现日志材料Owner别名与文件锚的接入缺口：device-log-file对应DeviceConfigurationLogFile。现已复用既有日志文件策略及正式来源解析器完成校验，保留Owner锁、材料锁、范围复核和文件锚校验。另复用现有回执服务的请求来源规则修正下载票据地址，避免59282票据被送到59191；存储配置及文件不改。材料Owner相关15项测试及回执/下载32项测试通过，没有增加表、列或权限。

正常日志经正式API上传，19082真实浏览器完成档案查询、解析、当前版本刷新、历史查询和前后比较；不是通过浏览器文件选择器上传。解析器1.3.2回填BOOT=Basic Bootloader 17.05.16，新增一条日志解析事实及一条具名历史。既有50条解析事实保持原值。证据：任务私有software-native-log-data-verification-20261011.json及software-native-v6-history-comparison-browser-20261011.txt。
