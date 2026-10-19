# P25 原审核范围查询优化与真实预览

仅优化 DPPMS_WARRANTY_RENEWAL_DIRECT 的 sources[0].sql。保持 projectTypes=10、projectIds/deviceSns 为空及 180 秒查询超时；不执行迁移、索引或普通表 DDL，不操作 59191，不重启服务。

原查询先汇总全量设备 SN，再展开出货关系并去重，导致 180 秒超时。候选查询从 fb_service 出发，以 EXISTS 检查 SN 的有效出货记录，并以合同与包单的半连接限制范围；项目仍取相同二进制类型/编码下最大的 projectId，再检查 disabled/effectiveTo。保留全部续保历史、原字段、原 renewalSourceKey 与重复行，不按 SN 取最新记录。

只读验证：原、新项目合同集合各 82,309 个且精确一致；候选完整 COUNT 返回 453,443，耗时 127.63 秒。连接级临时表的 6 组等价性验证覆盖重复关系/续保行、最新项目、最新禁用项目、过期项目、RMA、合同类型及范围筛选，逐行和重复数量一致。临时表随连接关闭销毁，未修改普通业务表。

真实预览：2111123722268209153，preview=true、full=true、adoptExisting=false、confirmPreparation=false。已保存任务配置，无需重启；模板资源同步保存相同 SQL。终态 FAILED，QueryTimeoutException，读取 0；该版 COUNT 成功未转化为正式预览通过。后续继续优化，尚未完成。

证据目录：D:/CodexTaskArtifacts/project-detail-followup/direct-remediation-20261016/；范围证明 P25-scope-equivalence.json，边界用例 P25-fixture-equivalence.json，完整查询 P25-v6-readonly-proof.json，运行回执及终态 P25-optimized-preview/。

其余未闭合项沿用原审核结论：P04 组织别名、P08/P23 正式账号审计绑定、P10/P21 的 V481、P16 用户来源绑定、P18 合同正式身份历史、P31/P33/P35 的 V482（P35 还缺正式扩展定义）、P37 冻结定义中的批准范围与 Owner 上下文。P19/P22 虽预览完成但全部 ISSUE，不计业务验收通过。禁止以本次优化授权补建这些正式数据或执行迁移。

## 2026-10-17 流式读取修复复验

仅 SQL 优化的运行 2111123722268209153 仍在读取 0 行时超时。不同 SQL 执行路径及会话缓冲的有界只读诊断均未让完整排序在 175 秒内返回首行，未采用这些后续候选，也未保存任何连接会话配置。相同 v6 SQL 不排序时 3.33 秒返回首行；有界诊断至 175 秒已读取 283,315 行，诊断随后按自身 MAX_EXECUTION_TIME 限制取消，不能写成全量查询通过。

修复限定在 P25 DIRECT、完整读取、RESTART_ALL 且非复合键来源：不追加全量主键 ORDER BY，改为在该次来源读取内保存原键集合以检查跨批非相邻重复。原键生成、空值/长度校验、重复拒绝、快照、批容量及提交边界保持；未修改其他适配器或 CHECKPOINT_KEY 路径。键集合需要与本次来源键数量成比例的内存，不缓存整行或 TEXT 载荷。

定向检查 SpringJdbcStreamingReaderTest + DirectWarrantyTransactionTest：13 通过、1 跳过（未启用 MySQL 专项用例），覆盖乱序原键、跨批非相邻重复失败和原有事务边界。冻结包 D:/CodexTaskArtifacts/project-detail-followup/P25-query-remediation-20261017/runtime-candidate/yudao-server.jar，包内读取器与已测输出、P25 资源与工作树一致，构建成功。

依已有仅重启 59284 加载修复包的授权切换该实例。数据库仍为 24306/npdms_reverify_20261014，Redis 24379/14，local,dac，Flyway/定时任务/批启动关闭；未操作 59191。真实预览运行 2111129869134020609，原范围/180 秒限额不变，已完成全量 PREVIEW_READY，read=453443，CREATED=453443，error_message=NULL。证据目录 D:/CodexTaskArtifacts/project-detail-followup/P25-query-remediation-20261017/real-preview/。

### 最终核对

运行 2111129869134020609：PREVIEW_READY；preview=true、full_snapshot=true、adopt_existing=false；453,443 条全部为 CREATED 预览结果，无异常；总耗时 347.893 秒（含分批校验/证据处理，不是单条查询等待）。目标业务表计数及 int_sync_binding 数量/最大 ID 前后一致，证据 real-preview/zero-write-verification.json。计数检查不是全字段比较；未调用 apply 或准备动作。任务参数、180 秒查询限额及源连接配置未修改。P25 查询超时闭合；其余已确认前置依赖保留，不表示所有任务业务验收闭合。
