# 179 基础数据同步执行报告

## 2026-10-29 P22 清空重导

用户明确要求仅清空并重新导入 179 的 P22 数据。清理前核对租户 1 的 340882 条范围、明细及 P22 `PRODUCT_SCOPE` 绑定一一对应；验收绑定、物料交换引用、范围版本及范围事件均为空，未发现其他任务绑定指向这些范围。随后在单事务中删除上述三类各 340882 条，原 P22/P31 失败回执及本次核对的其余既有回执保留。重导使用新的范围和明细 ID，不覆盖旧回执；原记录和来源身份可在受保护备份中追溯。

专门的单快照加密备份位于 `E:\CodexTaskArtifacts\project-detail-followup\179-backups\p22-before-reset-20261029-194621`，包含原范围、明细、绑定各 340882 条和 6 条既有回执；认证解密及与清理前逐行摘要一致的检查通过。三张目标表各取 3 条在连接级临时表中完成恢复样本验证，未进行全量恢复演练。另有完整数据库加密备份 `E:\CodexTaskArtifacts\project-detail-followup\179-backups\backup-179-20261029-194120`，认证解密及完成标记通过。首次专项备份因摘要序列化格式不一致未通过校验，未用于清理，失败产物保留。

清空后的预览 Run `2115773427975192577` 为 `PREVIEW_READY`，读取 343858、计划新增 340882，预览未写入范围；正式执行 Run `2115773619818463233` 为 `COMPLETED`，读取 343858、新增 340882，更新／未变／删除均为 0。执行回执将 339894 条确认为已分配；234 条重复、452 条超量、302 条缺订单，共 988 条保持未分配，不阻断同步。2976 条零数量来源、14293 条来源部门为空继续按既定边界处理。

[独立对账](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-reset-20261029/postflight.json)确认范围、明细和来源绑定各 340882 条；与清理前备份比较，340882 个来源键以及项目、订单、产品、数量和部门编码等受检字段一致。已确认范围中重复项目订单组及订单超量均为 0；部门匹配 326589、订单行匹配 340580，部门和订单匹配错误均为 0。302235 条已确认范围属于关闭项目，结束时间为空，继续计入订单占用。原 6 条既有 P22/P31 回执逐行摘要保持，原失败回执仍为 `FAILED`；不将旧 Run 改记为本次成功。

[设备归属摘要核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-reset-20261029/device-ownership-postflight.json)中 3486084 条设备的受检归属及审计字段与 P37 后基线一致。执行采用仅连接 179 的独立 59286 测试实例，验证了 Quartz、设备解析和事件后台任务隔离；没有扩权或操作 59191。上述结果不替代 P33 安装地址真实浏览器验收；下方此前“340882 条旧范围仍未确认”仅为当时重跑的历史时点。


> 2026-10-29 后续用户要求 P22 导入按已分配处理，并明确历史 `project_product_line` 的数量是已确认数量；重复、超量、缺订单等异常保持未分配且不阻断同步。用户明确同步逻辑不处理已完成的历史数据，关闭项目的已分配数量也继续占用订单数量；新同步规则已在工作树实现并经本地定向验证。规则实现时未回改或重跑 179 P22；随后按用户要求完成了一次重跑，结果见下。179 原有 340882 条范围仍未确认，当前不计入已确认占用；新规则不会自行改变它们。详见[后续裁决记录](../decisions/open-questions.md#q-base-p22-confirmed-allocation-20261029--历史范围按已分配承接的冲突与前置)。

> P22 新规则的本地验证：45 项定向测试通过，覆盖混合异常和有效候选、订单容量、关闭项目、租户隔离及历史保护；最终分组 SQL 的 30000 条合成记录探针通过。该验证未使用真实业务数据，不代表 179 新执行或浏览器验收。

> 本次工作树 Maven 编译和打包成功，但规定脚本的正式冻结阶段因既有 `.run/dac-host/verify-deployed-repairs.py` 缺失失败；没有可发布的冻结产物，也未切换现有服务。[构建日志](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-allocated-review-20261029/worktree-build-final-current.log)。

> **2026-10-29 P22 重跑**：按用户要求，仅对 179 的 P22 进行预览及正式执行。预览 Run `2115757296539725826` 为 `PREVIEW_READY`，执行 Run `2115757810836893697` 为 `COMPLETED`；两次均读取 343858 条，新增、更新、删除均为 0，340882 条已有范围不变。执行后只读核查仍为 302 条缺订单、14293 条部门来源为空，零数量 2976 条继续待处理，340882 条旧范围仍未确认。逐行指纹确认原范围、明细、P22 绑定及 4 条既有回执均未变化，原 P22/P31 失败回执保留；本次没有新范围，不能据此次重跑声称新记录的自动确认已在 179 实际验证。[运行回执](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-rerun-20261029/rerun-receipts.jsonl)、[前后指纹](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-rerun-20261029/history-after.json)。

> 重跑使用仅供 179 测试的本地端口 59286，校验了当前代码产物，排除 Quartz，且用进程检查确认设备解析和事件后台任务未实例化；执行后该临时实例已停止，已有 59285 服务未切换。前期隔离启动因临时配置导致的失败日志保留；一次尝试发现设备任务被实例化后立即停止，该尝试前只读核查所有租户待处理设备解析任务为 0。前置检查时 59191 的监听 PID 为 `35092`，重跑后观察到 PID 为 `31956`、进程创建于 18:47:29；本次未对 59191 执行启停操作，PID 变化原因未核实。

<!-- 179 followup results start -->

## 2026-10-29 后续裁决与实施

用户随后授权实施 P31 日期归一、P22 部门编码承接，以及 P37 在同租户、未删除设备中按原始 SN 唯一精确匹配补空设备引用。本节记录后续实施；下文为 14:35 首轮执行时的历史结果，不应作为当前状态覆盖本节，也不改写原失败回执。

- **P31**：在来源 SQL 中仅将结束早于开始的结束时间归一为开始时间，空值、相等和正常日期不改；已有原始历史按原指纹保护。预览 Run `2115713675509698561` 和执行 Run `2115713852995866625` 成功，写入并绑定 381,195 条。[独立核验](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-20261029/p31-postflight.json)确认 152 条倒置来源均归一至开始时间，目标无倒置、无用户引用或已确认授权；源端未修改，152 条来源原区间另存为受保护副本。原失败 Run `2115688284023775234` 保留；该回执未记录数据库错误详情，不能追认当时的准确错误。
- **P22**：预览 Run `2115714573526962177` 和执行 Run `2115714731165683713` 成功；343,858 条读取、340,882 条范围及明细写入，来源绑定各 340,882 条。[独立核验](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-20261029/p22-business-postflight.json)确认按唯一精确部门编码回填 326,589 条、按受保护订单键回填 340,580 条，未发现错配或确认分配；2,976 条零数量、14,293 条源部门为空、302 条缺订单行继续暂缓。源部门编码非空而主档缺失时保留编码、ID 待补且不阻断；本次实际数据无此类未匹配编码。首次预览失败 Run `2115642402226135042` 保留。
- **P37**：`DeviceReference` 预览 Run `2115716525203722241` 和执行 Run `2115716749611569153` 成功；读取 475,468 条，更新 419,271 个空设备引用，56,197 条待补（其中 1,232 条源 SN 为空），未新增或删除许可记录。[独立核验](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-20261029/p37-business-postflight.json)未发现跨租户、已删除设备或 SN 不一致，来源绑定及不同目标身份均为 475,468 条。3486084 条设备归属字段、475468 条许可受保护字段（含合同 ID、密文和审计列）和绑定身份的前后摘要一致；绑定指纹在同一事务内按新设备引用更新。许可合同 ID 不按合同号推测。

179 独立 Flyway 清单已追加并执行 V516.6，25 项 validate 通过；原有业务 CHECK、历史失败回执及原 V506 失败副本保留。新备份认证解密及 520 张表、239 个触发器的覆盖检查通过，尚未做恢复演练。后续使用 59285 的当前源码测试专用产物；Maven 构建成功，但正式发布冻结仍因原有 DAC 校验脚本缺失失败，不能用于 59191。隔离命名空间新增 13 个 Quartz 任务和 13 个触发器、1 个锁记录，原有 Quartz 表记录摘要不变，未清理新增测试记录。当前 24 项任务均有至少一条成功的正式执行回执、无活动任务，P38 未注册；这不代表待补项或浏览器验收通过。[最终状态核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-20261029/followup-final-state.json)。详见 [Flyway 信息](2026-10-29-179-flyway-info.md)、[后续执行回执](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-20261029/followup-runs.jsonl)。

后续代码定向验证：`NewHistoryTaskMySqlTest` 21 项、`P22DepartmentPostfillMySqlTest` 20 项、`LicenseBaseSyncTest` 6 项及 `LicenseDeviceReferenceMySqlTest` 5 项均通过，包含倒置日期与旧历史重放、部门唯一匹配与缺失保留、许可关联及并发回滚的原生 MySQL 检查。新迁移的隔离演练、失败历史拒绝和约束保留结果见 [P22 演练](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p22-20261029/rehearsal.json)。测试不替代 P33 地址原文的真实浏览器验收。

本轮未处理 P18 的 1,847 条待补合同引用、P15 的来源发货行缺项，以及 P33 的 5 条设备未匹配和 2 条串货暂缓；P33 安装地址原文浏览器验收仍未完成。P37 未匹配 SN 的记录与全部合同引用继续保留原始事实，不以唯一合同号补关联。

<!-- 179 followup results end -->

## 范围和约束

目标为 `172.17.90.179:13306/npdms`，租户 1；本地工作树为 `fix/dppms-migration-consolidation-20261012`。本报告记录 2026-10-29 的结构迁移、同步环境检查和真实执行回执。用户在验证期间要求后续只执行和检测，不修复发现的问题；不操作 59191，不重跑 178 已完成的任务，P18 缺项和 P22/P33 既有待补、暂缓边界继续保留。

执行结束后的只读原因分析、关联缺项分类与待批准处理方案见[179 同步问题分析](2026-10-29-179-sync-failure-analysis.md)；本次分析未实施修复。

## 迁移与环境

179 采用用户确认的[独立 Flyway 清单](../../sql/releases/test-v498/recovery-179/README.md)。先保存原 V506 失败行和错误回执，再按确认执行 repair；原 V506 没有伪记成功。继续执行 V506.1、V507～V516 和本次 V516.1～V516.5，最终版本为 V516.5，24 个清单文件的 Flyway validate 通过。结构演练、加密备份、各迁移回执以及执行前后表和约束检查详见[Flyway 信息记录](2026-10-29-179-flyway-info.md)。备份完成标记、认证解密和覆盖检查通过，尚未做恢复演练。

通过当前工作树后端正式授权接口，配置 DPPMS 和 CRM 两个来源，正式连接测试均成功，注册本次 24 项 BASE 任务。原有 P38 不在本次注册和执行范围。后端运行在隔离的 `127.0.0.1:59284`，目标主从连接均为 179；自动 Flyway、Quartz 调度与后台作业关闭。工作树编译成功，但正式发布冻结因缺少既有修复校验基线失败；当前使用的包仅用于 179 本地测试，不能作为 59191 发布包。未操作 59191。

首次启动产生的原 Quartz 时间字段变化已在用户提出只执行前复原，并经 11 张 Quartz 表对照备份确认原记录一致；本次独立调度命名空间保留 13 条测试任务记录，最终检查无该命名空间的调度器状态记录，隔离测试服务保持运行，未清理这些条目。正常正式登录更新了原管理员的 `login_ip`、`login_date`、`update_time`，其余原列与迁移前加密备份一致；这些真实登录审计值未回滚。首次正式同步写入前对 2,926 条既有业务、菜单、授权、连接及登录日志记录建立逐行摘要，管理员上述三个登录审计字段不纳入不变断言。

179 已部署页面的真实浏览器检查：可进入“迁移与同步”，页面没有“基础同步任务”页签；“连接管理”显示加载失败。请求 HTTP 200，但应用码 500，提示本地集成数据保存或查询失败，不能按 HTTP 200 判成功，也不能据此认定来源数据库本身连接失败。该页面属于 179 已部署版本，不等同于本地当前工作树后端。只观察并记录，没有修改远端部署。[浏览器证据](../../.run/base-sync-remote-test/flyway-179-sync-20261029/browser-179-observation.json)，[截图](../../.run/base-sync-remote-test/flyway-179-sync-20261029/browser-179-connection-failure.png)。

MySQL 报告版本为 8.4.11。`127.0.0.1:59284/actuator/health` 返回 JSON `UP`；179 同路径返回的是 HTML，无法据此判断其后端健康。当前账号无法核实 179 主机的剩余磁盘空间；信息架构中的表及索引大小只代表估算的数据库占用，不是文件系统空闲空间。

## 24 项运行结果

首次预览均已完成：21 项 `PREVIEW_READY`，3 项 `FAILED`，分别为 P04 缺少正式主部门 `162019`、P16 缺少正式公司 `001`、P22 部门编码 `162022` 未解析。P30 当时读取 0 条，P33 读取 1,035,329 条但因设备前置未就绪计划插入 0 条；两项预览状态均不能视为有效业务同步完成。P33 同时保留两条串货暂缓及 28,388 条空安装地址过滤告警。P18 原始公司及合同关联缺项按回执保留。

P15 预览明确记录 `P15_SOURCE_PREVIEW:FORMAL_BASE_P14_P24_INPUTS_NOT_VERIFIED`：即使在 P14/P24 执行后重新预览，也不能把该预览当作正式前置校验通过。正式执行才检查 P24 完成回执，订单关系仍须结合实际运行结果核对。

P15 长事务的过程观察保存在[运行观察](../../.run/base-sync-remote-test/flyway-179-sync-20261029/progress-observations.jsonl)；其中表行数取自 InnoDB 统计估算，可能包含未提交数据，不能代替正式回执或精确进度。

P19 的首次预览在正式项目尚未建立时耗时 9.39 秒；P16 完成后的预览耗时 808.59 秒，两次预览覆盖的正式项目范围不同。运行中一次[线程采样](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p19-preview-performance-observation.json)位于项目当前管理范围解析调用；单次采样不是完整性能剖析，也不能据此断定全部耗时原因。本次只记录现象，不调整权限、实现或数据库。

首次正式同步写入前完成新一份[加密备份](../../.run/base-sync-remote-test/backup-179-20261029-111430/coverage.json)，覆盖目标 520 张表、239 个触发器；备份解密认证和摘要通过，检查确认 24 条首次预览回执均已包含。未进行备份恢复演练。[预览日志](../../.run/base-sync-remote-test/flyway-179-sync-20261029/preview-24.jsonl)、[备份核验](../../.run/base-sync-remote-test/flyway-179-sync-20261029/execution-backup.json)。

本轮执行已结束：24 项首次预览均有回执，正式执行 22 项 `COMPLETED`、1 项 `FAILED`（P31），P22 暂缓而未正式写入，因此整体不能判定为全部通过。预览成功仅证明所覆盖的读取和预览规则，并不代表业务验收完成；任务 `COMPLETED` 也不代表其全部来源关系和暂缓字段已迁移。每项实际写入最多一次，原失败回执保留。

最终[只读状态核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/final-state.json)：Flyway 最后一项为成功的 V516.5；24 项 BASE 均已注册，P38 未注册，无运行中任务；首次预览的 P04、P16、P22 失败回执及 P31 正式失败回执仍在。2,926 条已有记录的受保护摘要没有变化，这项检查仅覆盖基线所列记录和字段，不代表所有数据库记录逐行无变化。

P14 实际执行后独立只读核对：`SALES_ORDER` 来源绑定 92,890 条，`SALES_LINE` 来源绑定 387,154 条，合计 480,044 条；正式订单行中 `order_id IS NULL` 为 1,580 条，与回执的待补引用告警一致。该检查不替代所有字段逐项对账。

P10/P11/P12 实际执行后独立只读核对：租户 1 的未删除客户为 59,544 条、产品为 35,004 条；产品数量与 P11 首插 8,441 条加 P12 补充 26,563 条一致。P11 的 2 条更新按回执单独保留，不并入新增数。该检查未逐条核对全部业务字段。

P15 正式提交后的[独立计数核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p15-business-counts.json)：租户 1 未删除 DPPMS 发货记录和 P15 独立 `SHIPMENT_EVENT` 绑定均为 4,283,326 条；其中 1,794,084 条已有订单行引用，2,489,242 条为空，与 `P15_SHIPMENT_ORDER_LINE_SOURCE_LINE_MISSING:2489242` 告警一致。合同、部门和订单合同关系其余缺项依实际回执保留，该计数不证明各字段逐项一致。

P16 正式提交后的[独立计数核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p16-business-counts.json)：项目主档及其 `PROJECT_MASTER` 绑定各 79,429 条，`PROJECT_ORIGINAL` 绑定 79,429 条，`PROJECT_ALIAS` 绑定 79,442 条；另有 75,383 条最终客户绑定。数量一致不代替项目字段和授权范围逐项验收。

P18 正式提交后的[独立计数核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p18-business-counts.json)：82,381 条 DPPMS 项目合同关系均保留原合同号，其中 80,534 条有正式合同引用、1,847 条继续保持空引用，与回执一致；来源公司和桥接缺项继续按告警留待处理。

P19 正式提交后的[独立计数核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p19-business-counts.json)：186,118 条 DPPMS 来源相关方记录对应 186,118 条独立绑定；四项已暂缓的历史扩展属性没有以该完成状态核销。

P25 正式提交后的[独立计数核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p25-business-counts.json)：租户 1 未删除续保历史 462,213 条，与 P25 的 `WARRANTY` 来源绑定数量相同。

P30 首次预览时没有正式发货输入，读取 0 条；P15 提交后的预览读取并计划新增 3,486,084 条，并保留合同组织未解析告警。正式执行前按租户复核设备表为空。

P30 正式提交后的[独立计数核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p30-business-counts.json)：3,486,084 条设备和 `DEVICE_BASE` 来源绑定数量一致，项目归属和客户归属均为空、两类归属版本均为 0。新建设备中 3,232,139 条带合同组织投影，253,945 条组织未解析；这些是新记录的组织字段，不应被描述为全部为空。执行前该租户设备表为空，未覆盖既有设备。

P31 前置后预览通过，但首次正式执行 `FAILED`，Run `2115688284023775234` 的回执只记录 `UncategorizedSQLException`；[失败后核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p31-rollback-counts.json)中成员表和 P31 来源绑定均为 0，与执行前一致。只读[结构对比](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p31-schema-comparison.json)发现 179 比 178 多一条 `chk_proj_member_dates` 有效期 CHECK，列定义和外键相同；[首批 500 条来源核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p31-first-batch-interval-check.json)没有结束早于开始的区间。由于回执没有保留具体 SQL 错误，不能把该结构差异认定为本次失败根因；没有重试、改表或修改来源数据。

P22 的首次预览失败回执保留；179 现有订单行外键 `fk_scope_order_line` 及 `order_source_system`、`order_company_code`、`order_type`、`department_code`、`department_name`、`department_version` 六列非空约束与待补引用的承载方式存在冲突。其结构处理不能沿用仅对 178 批准的变更，故本次没有正式写入 P22，也没有删除外键、放宽列或伪造引用。

P33 在项目和设备前置完成后的预览读取 1,035,329 条，正式执行新增 1,035,322 条；[独立计数](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p33-business-counts.json)确认安装记录和来源绑定各 1,035,322 条、地址为空 0 条，5 条设备未匹配和 2 条串货未建立绑定并继续暂缓。回执另有 220 组最新原始时间相同、28,388 条空地址在读取后过滤。8 条正常样例的来源和目标安装地址[逐字相等](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p33-address-samples.json)，但这只是抽样数据核对。

P33 的真实浏览器检查未完成地址原文验收：179 已部署页面中，项目 84571 没有安装记录；来源 ID 为 1 的正常样例属于项目 84，但项目页面显示无设备序列号。设备管理页按样例 SN 最终查询到 1 条设备；其“位置与变更轨迹”显示 `UNRESOLVED`、来源安装记录为空及暂无位置变更历史，当前页面未显示可核对的安装地址。[脱敏观察](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p33-browser-location-observation.json)、[页面截图](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p33-device-location-unresolved.png)。这些页面观察不能证明失败原因，也不能用数据库抽样替代浏览器验收；未修改设备归属或页面。

P35 正式提交后的[独立绑定核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p35-business-counts.json)：16,821 条软件变更批次绑定和 1,569,757 条软件版本记录绑定均各自对应相同数量的未删除目标记录和不同目标 ID，合计 1,586,578 条；回执中的 8 项扩展属性仍暂缓。此核对不代表各版本字段已逐条验收。

P37 正式执行读取并新增 475,468 条许可历史；[独立绑定和封装核对](../../.run/base-sync-remote-test/flyway-179-sync-20261029/p37-business-counts.json)中，来源绑定、未删除目标记录、不同目标 ID 与标注 `AES-256-GCM` 的有效 JSON 封装均为 475,468 条。该检查仅核对封装格式与算法标签，未解密或认证全部内容。回执明确保留 475,468 条设备关联待补、475,468 条合同关联待补，以及 1 条来源许可内容为空；没有记录许可明文。

<!-- 179 execution matrix -->

统计时间：2026-10-29 14:35:46 +0800。首次预览 24 项；已登记执行结果或暂缓 24 项，其中实际 `COMPLETED` 22 项、实际 `FAILED` 1 项。整套执行已结束。

| 任务 | 首次预览状态及 Run | 前置变化后预览状态及 Run | 实际执行状态及 Run | 实际读/插/更/未变/删 |
|---|---|---|---|---|
| BASE_P01 | PREVIEW_READY<br>`2115639612061868033` | — | COMPLETED<br>`2115643521165778945` | 209/209/0/0/0 |
| BASE_P04 | FAILED<br>`2115639614662336513` | PREVIEW_READY<br>`2115643527201382402` | COMPLETED<br>`2115643535317360642` | 12803/12776/0/27/0 |
| BASE_P08 | PREVIEW_READY<br>`2115639617669652481` | PREVIEW_READY<br>`2115643565054976001` | COMPLETED<br>`2115643570817949698` | 612/611/0/1/0 |
| BASE_P10 | PREVIEW_READY<br>`2115639619708084226` | — | COMPLETED<br>`2115643577453338626` | 59544/59544/0/0/0 |
| BASE_P11 | PREVIEW_READY<br>`2115639709654933506` | — | COMPLETED<br>`2115643796999987202` | 8443/8441/2/0/0 |
| BASE_P12 | PREVIEW_READY<br>`2115639712242819074` | — | COMPLETED<br>`2115643865631383554` | 34976/26563/0/8413/0 |
| BASE_P13 | PREVIEW_READY<br>`2115639716789444609` | — | COMPLETED<br>`2115643893380898817` | 99895/81152/0/18743/0 |
| BASE_P14 | PREVIEW_READY<br>`2115639767850901506` | — | COMPLETED<br>`2115644200424923138` | 572934/480044/92890/0/0 |
| BASE_P24 | PREVIEW_READY<br>`2115640070126002178` | — | COMPLETED<br>`2115645451107655681` | 81547/81540/0/7/0 |
| BASE_P15 | PREVIEW_READY<br>`2115640096709500929` | PREVIEW_READY<br>`2115645695300034561` | COMPLETED<br>`2115648068311408641` | 9288442/4801366/1969975/2517101/0 |
| BASE_P16 | FAILED<br>`2115642238199488514` | PREVIEW_READY<br>`2115670101749489666` | COMPLETED<br>`2115670599273635842` | 158871/79429/0/79442/0 |
| BASE_P18 | PREVIEW_READY<br>`2115642245535326210` | PREVIEW_READY<br>`2115672032387620865` | COMPLETED<br>`2115672103216832513` | 82676/82381/80534/295/0 |
| BASE_P19 | PREVIEW_READY<br>`2115642316481978369` | PREVIEW_READY<br>`2115672311111704577` | COMPLETED<br>`2115675703427031041` | 186118/186118/0/0/0 |
| BASE_P20 | PREVIEW_READY<br>`2115642356650827778` | PREVIEW_READY<br>`2115679230794752001` | COMPLETED<br>`2115679323308515330` | 7818/7818/0/0/0 |
| BASE_P21 | PREVIEW_READY<br>`2115642400493887490` | — | COMPLETED<br>`2115679407072960513` | 1190/1190/0/0/0 |
| BASE_P22 | FAILED<br>`2115642402226135042` | — | DEFERRED | — |
| BASE_P23 | PREVIEW_READY<br>`2115642413831774209` | — | COMPLETED<br>`2115679413683183617` | 35501/35501/0/0/0 |
| BASE_P25 | PREVIEW_READY<br>`2115642420035149826` | — | COMPLETED<br>`2115679441730494466` | 462213/462213/0/0/0 |
| BASE_P30 | PREVIEW_READY<br>`2115642481427177474` | PREVIEW_READY<br>`2115679935093891073` | COMPLETED<br>`2115681368518254593` | 3486084/3486084/0/0/0 |
| BASE_P31 | PREVIEW_READY<br>`2115642483050373121` | PREVIEW_READY<br>`2115688150766542850` | FAILED<br>`2115688284023775234` | 0/0/0/0/0 |
| BASE_P33 | PREVIEW_READY<br>`2115642574532337666` | PREVIEW_READY<br>`2115688308740808705` | COMPLETED<br>`2115688725931450370` | 1035329/1035322/0/0/0 |
| BASE_P34 | PREVIEW_READY<br>`2115643041198989313` | — | COMPLETED<br>`2115690463249915905` | 165801/165801/0/0/0 |
| BASE_P35 | PREVIEW_READY<br>`2115643055858081793` | PREVIEW_READY<br>`2115690610105081857` | COMPLETED<br>`2115690884785856513` | 1586578/1586578/0/0/0 |
| BASE_P37 | PREVIEW_READY<br>`2115643307264663553` | — | COMPLETED<br>`2115693498856464386` | 475468/475468/0/0/0 |

实际运行与预览记录按独立 Run 区分；计数是任务回执，可能包含同一批次内的后续更新，不等于源表不重复行数。[正式执行日志](../../.run/base-sync-remote-test/flyway-179-sync-20261029/execute-24.jsonl)。

### 失败、暂缓和回执告警

- BASE_P01 实际执行：`P01_DIRECTOR_ASSOCIATION_PENDING:162007003;P01_DIRECTOR_ASSOCIATION_PENDING:162012003;P01_DIRECTOR_ASSOCIATION_PENDING:162022003;P01_DIRECTOR_ASSOCIATION_PENDING:167010;P01_DIRECTOR_ASSOCIATION_PENDING:167020;P01_DIRECTOR_ASSOCIATION_PENDING:167050;P01_DIRECTOR_ASSOCIATION_PENDING:310409001;P01_DIRECTOR_ASSOCIATION_PENDING:310422001`。
- BASE_P04 首次预览：`P04_FORMAL_PRIMARY_DEPARTMENT_MISSING:162019`。
- BASE_P04 前置后预览：`P04_ORIGINAL_READ:3349;EXCLUDED_ACCOUNTS:26;ORPHAN_PROFILES:3`。
- BASE_P04 实际执行：`P04_ORIGINAL_READ:3349;EXCLUDED_ACCOUNTS:26;ORPHAN_PROFILES:3`。
- BASE_P13 实际执行：`P13_OUTSIDE_REVIEWED_PROJECT_SCOPE:2625`。
- BASE_P14 实际执行：`P14_ORDER_REFERENCE_PENDING:1580`。
- BASE_P15 前置后预览：`P15_SOURCE_PREVIEW:FORMAL_BASE_P14_P24_INPUTS_NOT_VERIFIED;P15_ORDER_CONTRACT_CONTRACT_MISSING:92890;P15_SHIPMENT_ORDER_LINE_SHIPMENT_NOT_OWNED:4283326`。
- BASE_P15 实际执行：`P15_SHIPMENT_CONTRACT_UNRESOLVED:42929;P15_SHIPMENT_DEPARTMENT_UNRESOLVED:29393;P15_ORDER_CONTRACT_CONTRACT_MISSING:7;P15_SHIPMENT_ORDER_LINE_SOURCE_LINE_MISSING:2489242`。
- BASE_P16 首次预览：`P16_FORMAL_COMPANY_MISSING:001`。
- BASE_P16 前置后预览：`P16_ALIASES_REQUIRE_NEW_MASTERS:79442`。
- BASE_P18 前置后预览：`P18_COMPANY_SOURCE_PENDING:8;P18_CONTRACT_POSTFILL_NOT_EXECUTED;P18_BRIDGE_CONTRACT_GROUP_MISSING:3320;P18_BRIDGE_CONTRACT_MEMBERS_MISSING:1;P18_BRIDGE_CONTRACT_MEMBER_PROJECT_MISSING:787`。
- BASE_P18 实际执行：`P18_COMPANY_SOURCE_PENDING:8;P18_CONTRACT_REFERENCE_FILLED:80534;P18_CONTRACT_REFERENCE_PENDING:1847;P18_BRIDGE_CONTRACT_GROUP_MISSING:3320;P18_BRIDGE_CONTRACT_MEMBERS_MISSING:1;P18_BRIDGE_CONTRACT_MEMBER_PROJECT_MISSING:787`。
- BASE_P19 前置后预览：`SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P19_EXTENSION_ATTRIBUTES_DEFERRED:shipment_status,shipment_status_updated_at,execution_status,execution_status_updated_at`。
- BASE_P19 实际执行：`P19_EXTENSION_ATTRIBUTES_DEFERRED:shipment_status,shipment_status_updated_at,execution_status,execution_status_updated_at`。
- BASE_P20 前置后预览：`SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED`。
- BASE_P22 首次预览：`P22_DEPARTMENT_CODE_UNRESOLVED:162022`。
- BASE_P22 实际执行：保留 179 现有订单行外键和六列非空约束，待补引用需要的结构变更未在该目标获批准，故暂缓。
- BASE_P30 前置后预览：`P30_DEVICE_ORGANIZATION_UNRESOLVED:RAW_CONTRACT_PRESERVED`。
- BASE_P30 实际执行：`P30_DEVICE_ORGANIZATION_UNRESOLVED:RAW_CONTRACT_PRESERVED`。
- BASE_P31 前置后预览：`SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED`。
- BASE_P31 实际执行：`同步读取或写入失败：UncategorizedSQLException`。
- BASE_P33 前置后预览：`SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P33_TRANSFER_DEFERRED:2;P33_FORMAL_DEVICE_PENDING:5;P33_LATEST_ORIGINAL_TIME_TIED:220;P33_BLANK_ADDRESS_FILTERED_AFTER_READ:28388`。
- BASE_P33 实际执行：`P33_TRANSFER_DEFERRED:2;P33_FORMAL_DEVICE_PENDING:5;P33_LATEST_ORIGINAL_TIME_TIED:220;P33_BLANK_ADDRESS_FILTERED_AFTER_READ:28388`。
- BASE_P35 前置后预览：`SOURCE_PROJECT_IDS_PRESERVED:FORMAL_PARENT_VALIDATION_DEFERRED;P35_EXTENSION_ATTRIBUTES_DEFERRED:conp_bak,conp_change,boot_bak,boot_change,cpld_bak,cpld_change,pcb_bak,pcb_change`。
- BASE_P35 实际执行：`P35_EXTENSION_ATTRIBUTES_DEFERRED:conp_bak,conp_change,boot_bak,boot_change,cpld_bak,cpld_change,pcb_bak,pcb_change`。
- BASE_P37 实际执行：`P37_LICENSE_CODE_EMPTY:sourceId=472122;P37_DEVICE_ASSOCIATION_PENDING:475468;P37_CONTRACT_ASSOCIATION_PENDING:475468`。
