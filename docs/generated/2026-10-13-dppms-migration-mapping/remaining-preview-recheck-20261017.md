# 剩余任务逐项复核与真实预览（2026-10-17）

仅在指定 C: 工作树及 59284 / 24306 / npdms_reverify_20261014 执行。未执行迁移、普通表 DDL、业务 apply、范围发布或正式绑定补建，未操作 59191。保留旧失败回执；预览中的 CREATED 仅为预测动作。

## 本轮修复

- P14：原运行 2110581639507447809 为 2026-10-15 遗留 preview=true、VALIDATING，阻塞了新预览。正式 `/runs/{id}/recover` 在作用域锁、运行归属、版本和既有等待条件下将其标记进程中断并释放任务；没有直接改状态或回写业务表。关联预览 2111138236187492354 已 PREVIEW_READY/read465804（CREATED464702、ISSUE1102）。该既有线现保留不再操作；后续为另建独立直写任务。
- P18：原整表归并查询在实际 MySQL 5.7 源库的完整查询中误选历史规范来源，不能再归因为缺正式合同数据。不同执行形态曾返回不一致事实；未认定具体服务端 bug。改为对同一类型、去历史后编码、合同号的完整身份分别计数并按既有编码顺序明确选择创建/更新规范来源；未缩小到当前批次、未改变当前优先或审计冲突规则。完整只读查询 82676 行、110.49 秒，状态误选 0；五组 MySQL 5.7 纯 SELECT 用例覆盖当前优先、创建时间并列人员冲突、更新时间并列人员冲突、历史补缺及最新项目禁用，均通过；49 项相关 Java 检查通过。
- P18 第一次修复预览 2111141279159238657 FAILED/read0，根因是驱动 60 秒 socket 等待。核对发现原 P18 queryTimeoutSeconds=0，不能误用 P25 的原 180 秒说法；现显式设为 180 秒，既有读取器同步设置连接等待。第二次运行 2111141902793523202 已全量 PREVIEW_READY/read82676/ISSUE82676，无运行错误。正式项目绑定数量为 0；已有结果样本原因全部 PROJECT_BINDING_MISSING，不作为正式项目合同业务验收通过。旧回执和失败候选均保留。
- 已保存任务 SQL 和仓库 P18-direct.json。独立包已构建，包内资源与工作树一致，P25 已验证的读取器及模板未变化。已随双线任务修复包加载到 59284（PID33288），未操作59191。

## 逐项剩余状态

|任务|真实结果及具体阻塞|依据与处理边界|
|---|---|---|
|P01|最近 PREVIEW_READY/read211；既有8主管待正式账号关联|沿既有 P01组织 → P04账号 → 同一P01主管回补流程；预览不补建正式数据。本轮复用原主管证据。|
|P04|FAILED/read5015；正式范围部门310500002001缺失|已按原裁决恢复391→88 ENUM映射及adoptExisting=true的仅预览标志；原始391证据保留。最终Run2111146915108438017重现已知28个范围部门未正式迁入的前置，不能再把391映射丢失归因为待裁决。模板回归22项通过，已加载59284。|
|P08|FAILED/read612；字典审计需3个原账号，其中2个在当前平台不存在|只读核对532条字典来源，保留已裁决542/system例外；不造账号或替换管理员。|
|P10|FAILED/read0；cus_customer_master.erp_customer_code缺失|既有V481包含此列；当前Flyway最高V473。不得通过删掉已批准ERP字段规避。|
|P14|既有线关联预览2111138236187492354已PREVIEW_READY/read465804|CREATED464702、ISSUE1102；既有线停止操作。独立直写线及新Run见双线核查报告。|
|P15|PREVIEW_READY/read3848169，CREATED3847355、ISSUE814|10000条保存结果包含全部814个唯一ISSUE，均为CONTRACT_CUSTOMER_CONFLICT：同公司/合同的来源客户编码或名称不一致，沿原规则保留冲突。不是运行异常或泛指正式数据未齐。|
|P16|FAILED/read0；用户正式迁移/来源审计绑定未齐|实际INT绑定只有组织类，账号绑定0；不能用P04预览预测ID替代正式身份。|
|P18|本轮PREVIEW_READY/read82676，全部ISSUE|实现/配置故障已修；仍缺P16正式项目来源绑定。|
|P19|PREVIEW_READY/read265547，全部ISSUE|正式项目绑定0；保存结果500条全部PROJECT_BINDING_MISSING（样本覆盖，不伪称全量原因明细）。|
|P20|PREVIEW_READY/read7818，全部ISSUE|保存7818条结果均PROJECT_BINDING_MISSING；不自动造项目。|
|P21|FAILED/read1190；目标project_id仍NOT NULL|proj_project_lease_product / proj_project_product_configuration均未应用V481的可空缓存契约；不补虚假项目ID。|
|P22|PREVIEW_READY/read343858，全部ISSUE|正式项目绑定0；保存结果10000条均PROJECT_BINDING_MISSING（原因明细覆盖部分）。|
|P23|独立TABLE_MAPPING缓存任务2111147253500690434，PREVIEW_READY/read35501/CREATED35501|Run2111147253739765761；旧Owner任务保留。缓存直写不依赖旧Owner审计绑定；本批来源审计时间NULL为0，不能把NOT NULL列说成当前预览阻塞。maxRows保持10000、整表缓存读取按既有引擎执行，10项相关检查通过；未清空或刷新业务缓存。|
|P30|PREVIEW_READY/read0，不能算设备业务闭合|正式Owner只读取source_system=DPPMS的发货事实；当前发货表2条均为FAST001_TEST_MES，DPPMS为0。保留P15正式数据前置，不拿测试记录替代。|
|P31|FAILED/read0；V482发布范围表缺失|int_sync_project_scope_publication / member尚不存在；其后还需要P16非预览完整成功所发布的不可变范围，预览不发布。|
|P33|FAILED/read0；同上|保留发布范围及原安装历史规则，不绕过范围查询。|
|P35|FAILED/read0；缺V482与正式八属性定义|冻结extensionDefinitionId为空；当前唯一定义为SOL/REQUIREMENT_ANALYSIS，没有AST/SoftwareVersionRecord八属性定义，不借用其他模块定义或代为发布。|
|P37|FAILED/read0：LICENSE_EXPLICIT_APPROVAL_AND_OWNER_CONTEXT_REQUIRED；整表实现仍未闭合|冻结approvedLicenseIds/Facts/ownerProjectId均空。现有SECURE_LICENSE reader限制≤200及单Owner，不能称为475468条整表入口。原完整16列/受控加密规则不变；本轮只COUNT来源475468，licenseCode读取0，不借用旧153条试点，不猜Owner或取消项目授权。|

以上明确区分技术运行完成、业务ISSUE和正式执行前置，不把PREVIEW_READY视为业务迁入完成。未变化的P11/P12/P13/P24/P25/P34已有真实预览证据继续有效，未重复运行。

证据：D:/CodexTaskArtifacts/project-detail-followup/remaining-recheck-20261017/。P18终态及计数核对在P18-real-preview-180/；P14恢复前后、关联运行和终态在P14/；其余逐项只读核对在相应任务命名JSON中。P18前后业务表计数与绑定数量/最大ID一致；该检查不是全字段比较。

## 双线任务与历史操作核清

完整逐项归属、历史修改、来源库45任务对比、既有22任务保护及24项独立直写任务见 [历史任务核查与双线对照](task-history-dual-track-audit-20261017.md)。本页先前的391/P23入口归因已以上述真实结果纠正；旧运行原始证据保留。
