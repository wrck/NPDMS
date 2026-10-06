# 公共框架临时 Git 检查点（WIP，2026-10-06）

基线：`fda3bb55b0fb8e1fbd6f44f44cf4dbc21d015721`；临时分支：`integration/unified-framework-20261006`。本检查点只包含本公共任务已在当前云工作树落地的源码、测试与脱敏生成证据，不包含未到达本环境的原生首包，不是完整验收或部署许可。

父任务转达用户 09:12 对“两云任务各推临时集成分支，再汇总到 fix 联合验证”的明确答复“允许”。本次仅提交并普通 push 自身临时分支，随后远端回读 exact SHA。禁止 force、master 合并、部署、生产库/真实历史迁移；四禁写文件及资产配置日志服务未改动。

## 当前限定结果

`wip-test-results.json` 与 `wip-source-files.json` 对应同一 68 源文件快照。命令退出 1；32 项中 30 通过、1 失败、1 跳过，不能称整体通过：

- CAS H2 8 项：7 通过、1 失败。通过项包括 SQL 版本单胜者并发、相同摘要重放/不同摘要拒绝、原权限/当前 Owner 在重放前重验、跨租户/未登录/角色权限拒绝、模板/归档/已有 fulfillment 拦截、实际审计/outbox 失败后全部回滚及重试、外层事务回滚、冻结锚/历史 fixture 保留。权限端口及非项目原生 Owner 为受控 fixture，实际账本/审计/outbox/MyBatis/Spring 事务为生产实现。
- 失败：`staleActiveMaterialCannotBeAssociatedAfterWithdrawal`。旧 `DeliveryFulfillmentService.associate` 接受在撤回前读取的 ACTIVE 对象，当前未锁材料行和重读状态，可把已撤回材料写入 fulfillment。本 checkpoint 保留失败断言，未修改旧 fulfillment 服务。后续必须让关联与撤回使用同一材料行锁并检查当前状态，再验证并发。
- purpose SPI 5 项通过。默认 `NATIVE_FROZEN` 保持原生语义，即使目录同名；Owner 显式 `CATALOG` 后先授权，再组合目录与 Owner 类别/媒体交集及较小大小上限。未知/未授权拒绝。尚未为存量原生 Owner 注册新的目录用途。
- MIME 内容 12 项：11 通过、1 实际 ClamAV 条件跳过。未知提示分支只允许 log/cfg/conf/txt 的用途已允许 text/plain、字节 Tika 检测为 text/plain 且 UTF-8/控制字符检查通过；空或 octet-stream 声明不能授权任意二进制。伪扩展 MZ/二进制/PDF、用途白名单与明确 MIME 不匹配被拒绝；旧明确文本用例保留。
- 旧文件初始化 6 项、Spring 循环装配 1 项通过；初始化空 MIME 的新 HTTP/存储完整路径尚未补独立验收。

第一轮 SPI/MIME红测试、初次 CAS fixture 编译重载歧义、Mockito 装配时调用委派 insert 造成额外审计/outbox 行的失败均保存在本地 `.run/cloud-20261006/`；后两项 fixture 已修复。当前日志/报告哈希可复核，但原始日志/XML不随 Git 发布。

## 旧稳定证据与新快照的边界

旧 51 文件快照的 209 通过+2 浏览器条件跳过、普通 Tomcat 21 请求、前端 27 项及声明表单/文件槽位 Chromium 都保留在 `verification.json`，该报告明确标记 `HISTORICAL_PRE_THREE_CAPABILITIES_51_FILES`。它们证明三项实施前的修复，不证明当前 CAS/SPI/MIME。旧 `source-files.json` 为完整原 51 文件路径与哈希，`wip-source-files.json` 为当前 68 文件。

当前尚未运行新 MySQL CAS/迁移/并发套件、完整42模块 JAR打包及 Tomcat安全链、Chromium与新MIME端到端、原生Owner/purpose接入、全仓回归。需在整合后独占环境重验；不得合计各轮测试数量。

V398 在新增前再次检索本环境 sql 和迁移登记未发现占用，保留给本公共任务。迁移仅增加材料 `version BIGINT DEFAULT 0`；当前只在 H2现有材料fixture执行，不改历史SQL，不是生产/完整迁移通过。原生包未取得，合入前还须检查其序号与文件重叠。

## 保留失败与未接入项

- 原始完整迁移仍在 V374 第72行 MySQL1267排序规则冲突失败，未 repair/跳版本/改历史SQL。
- 较早全后端5848项：78失败、69错误、467跳过；未对当前快照重跑。项目活动失败留给独立活动。
- 原生operationAdapters 7项HTTP mock/路由失败；旧CUSTOMER_DELAY静态断言失败均保留。
- 全仓vue-tsc因4GiB堆上限失败，范围检查仍有3项未改动文件既有错误。
- 79个目录身份中34个Owner待适配；KNO公告只有租户只读适配，无交付写入/用途/生命周期合同，继续fail closed。
- 工勘、旧培训链接等原生入口没有全部闭环，不能宣称所有真实业务交付件已归集。
- Library首包完整helper调用及一次相同重试均network失败，本地无ZIP字节，未验实际ZIP/补丁hash、未审查41文件、未应用。Git临时分支是本轮新授权交接方式；本公共检查点不自行上传Library，也不包含别任务的未取得成果。

## 发布审查与排除

提交源码、测试、前向SQL及本目录生成报告/逐用例结果/哈希。扫描未发现字面私钥、GitHub/OpenAI令牌、长Bearer或凭据赋值；保护路径与基线逐字一致或持续缺席。原始运行数据、SQL/Redis fixture数据、真实登录令牌/密码、HTTP原始记录、原始XML/log、截图、`.run/`补丁ZIP及Library helper、所有target/node_modules/build产物不提交。无本任务独占服务仍在运行，共享MySQL/Redis及其他任务进程未触碰。
