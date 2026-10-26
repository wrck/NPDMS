# 项目序列号清单修复验证记录

## 2026-10-25 修复包加载后的真实浏览器验收（序列号清单通过）

用户明确批准仅重启59284加载修复包，19284保持运行。已按原PID/端口和冻结清单核对后执行：新后端PID47064，local,dac，health=UP；运行包SHA-256为8f708eaa8d0f6c69f743cb29baa03bf0263ec0dd346d7e45b6a95a68a0cf7eac。前端19284仍为PID41276，原48182仍为PID9060。没有启停其他服务、执行DDL、数据同步或设备归属/安装历史写入。

使用19284真实浏览器和已真实登录的系统管理员会话，完成页面点击、请求与业务响应、可见表格、翻页、筛选、重置及展开明细验证。

| 项目 | 页面与接口总数 | 首/次页 | 首/次页HTTP耗时 | 结果 |
| --- | --- | --- | --- | --- |
| 58469 | 13488台 | 各20行、各20个不同设备，首行SN不同 | 11.429 / 11.572秒 | HTTP200、业务码0，无加载失败 |
| 59086 | 10627台 | 各20行、各20个不同设备，首行SN不同 | 10.482 / 10.234秒 | HTTP200、业务码0，无加载失败 |

总数沿既定并集规则：58469为旧清单13486台加2台当前归属设备，59086为旧清单10621台加6台当前归属设备。浏览器验证总数、前两页及下述查询样例；全量设备集合一致性由前述真实SQL对比证明，没有声称逐页检查全部设备。

- 58469以实物SN020101440238800042检索，实际返回发货SN02010640D238000034，1台，约11.546秒；展开显示合同33120240906A02及实物产品02010144，项目地址为空保持为空。
- 58469无匹配SN检索显示0台、无加载错误；重置恢复13488台及20行。
- 59086第二页展开显示合同31120231230A03及所选SN02051429N241000012。
- 59086检索未归属设备02011727D238000014，返回1台、业务码0，约11.083秒；接口当前projectId为空、projectShipments仅含59086。验收后只读回查ast_device.project_id仍为空，没有为展示清单而改写归属。重置恢复10627台及20行。
- 后端就绪（05:00:44）后的浏览器控制台error为0。重启窗口中旧页面轮询曾有1条Network Error（04:59:55），原始记录保留，未混记为修复包就绪后的故障；既有el-link弃用warning不属于本次修复。
- 页面仍有既有“当前执行计划读取失败”，此前计划API业务码1014023010；属于另一路计划状态问题，未在本次修改计划、审批或历史。本次通过范围为项目序列号清单，不宣称项目详情全部业务模块完成验收。

证据位于仓库外D:/CodexTaskArtifacts/project-detail-followup/project-serial-list-20261024：browser-acceptance-passed.json、browser-58469-page1.png、browser-58469-dual-sn.png、browser-59086-page1.png、browser-59086-expanded.png、browser-59086-unassigned.png、runtime-isolation-final.json。启动和隔离证据保留原始PID及冻结包SHA。首次30秒失败及修复前后的原生SQL证据继续保留在下节，不再作为当前未解决阻断。

## 2026-10-25 当前方案

### 首次独立启动的浏览器失败与修复（已解决）

用户已批准独立后端59284、前端19284，要求不影响现有服务。已启动candidate-business（SHA-256 fc1f74ce605267dd2d7feb9d8b25f2dc6080c2e6359450cf127adfd7303a5b4d），local,dac，178测试数据库/Redis DB15，后端PID17628、前端PID41276。启动前检测到的48182服务为PID9060；未启停其他服务。后端health=UP，Quartz待机且执行作业数0；已通过登录页真实登录系统管理员。

58469的项目详情及序列号页可以打开，但两次清单请求均在30秒被浏览器取消，页面显示“序列号加载失败，请重新查询”。因此本轮浏览器验收未通过，不能将前述SQL检查写成页面通过。证据：仓库外browser-first-timeout.json/png及runtime-business/runtime-sanitized.log。

定位：完整分页约10.102秒，本页20台设备的发货明细查询约21.481秒；COM包裹来源键上COLLATE使唯一索引只能按tenant_id扫描，估算每次73150行。只读对比试验将COLLATE移至查询值一侧，明细从21.776秒降至0.00975秒，20条全部字段完全一致，命中tenant_id/source_system/source_record_key唯一索引（每次1行）。最终修复限定在有设备ID集合的本页上下文，项目级查询保留原unicode比较；最终MyBatis复验分页10.005秒、明细0.02672秒。证据：context-package-index-trial.json、context-before-explain.json、context-trial-explain.json、context-scoped-index-performance.json。

修复后31项查询回归通过（含6项原生MySQL），tests-package-index-scoped.log。代码只改两处SQL资源及H2排序规则适配；没有DDL、数据同步或归属/历史写入。新candidate-package-index完整clean package通过（backend-build-package-index.log，3分32秒），SHA-256为8f708eaa8d0f6c69f743cb29baa03bf0263ec0dd346d7e45b6a95a68a0cf7eac，构建前后源码一致；相对当前candidate-business仅两份SQL资源变化，其余11059项执行内容/资源一致，39项pom.properties仅时间戳不同。标准59191冻结仍因历史校验脚本缺失失败，未降低校验。当时运行进程仍为原candidate-business，新修复尚未加载；后续加载与验收结果见顶部。按项目规则，每次重启须明确批准，新候选和受保护启动预检已通过，restart-package-index.ps1默认仅核对原PID/端口及新包，不启停服务；后续已获明确批准并完成仅59284重启及上述序列号浏览器验收，19284保留。

另有独立既有问题：项目顶部显示“当前执行计划读取失败”，计划API返回业务码1014023010、消息“当前项目计划不可修改或设计内容无效”；本轮未修改计划或审批状态，不将其计为序列号查询失败原因。

目标正式资产：[F-AST-001](../../../tasks/features/F-AST-001.md)。按需求方最后裁决，项目清单修复使用现有业务表和字段；撤除所有新增source_列、新清单表、P39及P16原利润中心扩展，不需要新增DDL。安装使用imp_eng_installation现有project_id/equipment_id/status/install_location；项目借货使用sales_type/department_code及既有编码映射；发货、包裹、合同、双SN和产品均来自现有业务表。

P33仅删除来源SQL的非空地址条件，Java过滤、校验、绑定指纹和时间线逻辑保持HEAD原实现。SQL能读到NULL/空/空白地址，不表示这些记录已同步。此前18条时间线接续实现已按收敛要求撤除；早期原始来源冲突诊断不等同于经过现有Java过滤后的实际运行阻断，不再要求重复裁决。

### 当前验证

- tests-business-tables.log：57项通过（AST36、IMP9、INT12），包括本轮业务表查询、空权限/跨租户/去重、安装选择与批量维护、历史防接管，以及P33来源读取空地址而原Java仍过滤的真实MySQL检查。
- tests-business-department.log：3项通过，新增确认311202映射162102的借货匹配，同时排除其他部门；NULL/空白利润中心不臆造过滤或去掉-L。
- frontend-business-tests.log：6项清单组件测试通过。完整vue-tsc类型检查及Vite打包通过（frontend-business-types.log、frontend-release-build.log）；初次本机内存分配失败后串行重跑通过。
- business-real-comparison.json：直接查询178现有业务表执行完整清单CTE，不需要临时表或新增安装来源投影。旧、新设备集合：58469均13486台；59086均10621台；84571均0台。所有样本遗漏0、新增0，单次CTE分别8.395、7.444、1.625秒。这是清单集合证据，不是HTTP或浏览器响应证据。
- 借货真实样例此前为空，非空分支由夹具验证。产品名称使用当前官方产品主档，可能与旧系统显示标签不同，不覆盖已有CRM产品。
- business-full-mapper-check.json：使用现有业务表的完整原生MySQL Mapper复验通过，覆盖计数/分页/项目名筛选/只读列表/上下文、未归属设备详情可见、空权限和跨租户拒绝。58469共13488台、59086共10627台，分别包含旧清单外2台、6台当前归属设备；计数8.727秒、7.467秒。未经过HTTP和真实浏览器。完整Maven clean package通过（backend-build-business.log，3分37秒）；构建前后源码哈希均为a9394301aeb4df4c52cf7097af89d835143f786304180a2e2c52bd0e627014b7。标准冻结因历史工作树缺少.run/dac-host/verify-deployed-repairs.py失败，未降级该校验。

### 首次独立候选（现已由candidate-package-index取代）

candidate-business/manifest.json保存本次完整工作树候选，SHA-256为fc1f74ce605267dd2d7feb9d8b25f2dc6080c2e6359450cf127adfd7303a5b4d。对照先前独立完整基线逐项核对24处差异：22项本任务代码/资源，以及仅换行不同的P16/P33目标XML（归一换行后原文一致）；11036项其他执行内容/资源逐字节一致。P16/P33 Java类与基线一致。40项元数据为39项pom.properties时间戳及已审查的AST依赖现有engineering-api。所有旧P39、新增来源列及时间线接续类均不在该包。

独立59284启动预检通过，使用既有受保护DAC环境及local,dac、178测试数据库与Redis DB15；已按后续明确批准启动该候选。候选清单deploymentReady=false，不是59191正式发布清单；待明确独立运行批准及真实浏览器验收。准备入口为仓库外start-business.ps1，默认仅预检，启动需明确ApprovedStart。

### 证据及交付边界

原始脚本和日志在仓库外D:/CodexTaskArtifacts/project-detail-followup/project-serial-list-20261024。当前方案未操作持久化DDL、同步或设备归属。独立应用已按批准启动，性能失败修复后已再次获批重启59284并完成上述序列号清单浏览器验收。

candidate、candidate-optimized、candidate-reuse及此前7c1098669f3633efebf9ea9673738afe80f2443eee9fb16adcb5fb642e7c9216包均对应被替代实现，禁止作为当前运行候选。旧原生安装扩列测试、V517临时迁移和P16补原利润中心测试只保留为撤销方案的历史记录，不作为本轮通过依据。标准59191冻结缺少历史工作树校验文件的问题不得以降低校验解决。

## 已撤销P39方案的历史验证（不作为本轮放行依据）

日期：2026-10-24。目标正式资产：[F-AST-001](../../../tasks/features/F-AST-001.md)。状态：代码与离线验证完成，运行部署及真实浏览器验收待执行。

## 实现边界

按需求方要求恢复旧 PMS-struts `ProjectAction.checkShipmentInfo`、`ProjectDaoImpl` 和 `sql-map-project-config.xml` 中生效的 `query-shipmentinfo-bycontractno` 逻辑。新增 BASE_P39 将已发布 P16 项目的清单来源同步至 AST 自有 `ast_project_shipment_entry`；安装地址为空仍保留、双 SN 原值保留，借货沿旧合同/利润中心规则，保留转出信息。只读项目设备列表与分页使用当前归属或清单关联并集，每台设备一行；来源明细附加至本页已授权项目上下文。没有写入设备当前项目、指派版本或 P33 安装历史。

## 已验证

- 后端 56 项定向测试通过，覆盖同步预览、事务替换、重放、末批失败与容量超限回滚、来源身份拒绝，以及空权限、跨租户、无归属设备、分页去重和上下文授权。
- 前端 6 项测试、完整类型检查及完整构建通过。独立安装锁文件声明的依赖用于验证，原共享 node_modules 保持不变。
- 对照真实旧库 32 个项目，旧生效 SQL 与新来源 XML 的 17 个字段及重复次数全部一致，见同目录两份 `source-legacy-*.json`。58469 为 13,486 行、59086 为 10,621 行；大量空安装地址均保留。84571 在旧来源也为空。被抽查的实际借货项目清单为空，非空借货及转出分支由夹具验证，不能据此宣称真实借货页面验收。
- 真实 MySQL 连接内建立临时表，使用实际 P39 目标 XML 写入 24,107 条来源记录，并执行实际设备查询 XML。两个项目去重后总数分别为 13,488 和 10,627（包含原当前归属设备），均包含 2 台当前未归属设备；列表、项目名过滤、上下文、详情可见性、空权限、跨租户与实物 SN 检查通过。现场数据会被其他同步改变，这些是当时快照。
- 初版计数约 33.63/34.02 秒，超过前端 30 秒超时；修复为按项目索引构建候选设备并集后，同样结果的计数降至 1.193/0.173 秒，见 `real-mysql-query-check.json`。

## 验证限制与部署准备

MySQL 临时表不允许重复打开，验证适配器为每个表引用建立独立临时副本；未使用该夹具证明完整授权 CTE 分页、HTTP 或页面性能。连接关闭后临时表释放，没有持久化 DDL/DML。

完整构建沿工作树 `scripts/worktree-build.ps1` 和互斥锁。该历史独立工作树缺少标准 59191 冻结所需运行基线；根工作树校验器也与原独立 59284 基线不兼容。未降低标准校验、未创建虚假运行状态。本次候选沿已存在的独立 59284 全量构建基线逐项核对，不能用作正式 59191 发布包。

最终 Maven 全量构建成功；标准发布冻结步骤因缺失 `.run/dac-host/verify-deployed-repairs.py` 失败，不能将整条标准发布流程写为成功。独立候选另存为仓库外 `candidate-optimized/yudao-server.jar`，SHA-256 为 `3214a48830eeb069e4e2214e47a0b907cc11cfc4e42a6781c6caec83ed2075e9`：与原独立全量基线相比，仅18个本任务 AST/P39 类和资源变化，10,939个其他可执行/资源条目一致；依赖元数据仅构建时间注释变化。构建前后源码摘要一致，保护凭据及候选只读启动预检通过，未启动服务。

待明确批准后，在既有测试库 `172.17.90.178:13306/npdms` 落地 V517 新读模型表，启动独立 59284 候选，登记并预览/执行 BASE_P39，再用真实项目完成接口和浏览器验收。执行前需复核迁移版本、既有表、同步配置、端口及设备归属保护摘要；新表就绪前不启动依赖它的候选。既有 BASE 作业配置及 59191 不在操作范围内。

原始脚本与日志保存在仓库外 `D:/CodexTaskArtifacts/project-detail-followup/project-serial-list-20261024`：`test.ps1`、`run-mysql-check.ps1`、`compare-source.py`、`compare-transfer.py`、`build.ps1`、`freeze-optimized.py`。该目录可能包含运行环境辅助信息，不整体提交。
