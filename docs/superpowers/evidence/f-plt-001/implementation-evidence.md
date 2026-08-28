# F-PLT-001 Implementation Done 证据

## 1. 边界与绑定

- Feature：F-PLT-001 统一文件身份与版本管理
- 规格基线：`2efd8c476430d77ce2003c6e9fe300a335eac6a7`
- 实现范围：Technical Plan Task 1～10；本证据不声明 Deployment、SIT、UAT 或 Release 通过
- 实现提交：本文所在提交（独立复审以提交完整哈希为准）
- 验收日期：2026-08-27

Task 10 验收期间发现并完成四项正向链路修正：工期详情补齐待审候选版本、变更响应补齐 `customerEvidenceReferenceKey`、草稿摘要回源完整详情，以及材料绑定/解绑成功后刷新父级草稿版本。未修改 `yudao-framework`、`yudao-module-bpm`、既有 Yudao `FileApi`/`FileClient` 或受管规格快照。

## 2. 隔离基础设施

- Compose project：`npdms-50eb-fplt001-t10`
- 数据库：`npdms_fplt001_t10`；单租户 BPM 整改复验使用重新创建的清洁库 `npdms_fplt001_t10_r1`，MySQL 8.4.10，宿主端口 23319
- Redis：7.4.9，宿主端口 26389
- ClamAV：1.4.6，宿主端口 23311
- Flyway：全新空库 V1→V95 共 95 条迁移全部成功；六张 PLT 文件表存在
- Quartz：V93 的 `fileOutboxDeliveryJob` 唯一且启用，cron 为 `0/30 * * * * ?`；最终应用启动后 QRTZ Job/Trigger 自动存在并触发投递

隔离验收通过 INFRA 既有文件配置公开管理接口创建并启用数据库文件客户端，避免依赖无效的示例 S3 配置；未直接修改配置或文件表。Flowable 流程通过既有模型创建/部署公开 API 建立。单租户整改复验中，模型、流程定义和流程实例均自然使用 Flowable `NO_TENANT_ID`（数据库 tenant 标签为空），SOL/PLT/PROJ 业务事实继续使用受信 tenant `0`；未直接修改任何 `ACT_`/`FLW_` 表，也未修改 BPM 基础模块或框架代码。

## 3. 后端全链验证

运行环境：JDK 25.0.1、Maven 3.8.6。

### PLATFORM 文件全链

命令：

```text
mvn.cmd -pl pms-module-platform -am -Dtest=FileArtifactEndToEndMySqlIntegrationTest -DskipITs=false -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：20/20 PASS，18 模块 Reactor BUILD SUCCESS。聚合覆盖：

- 初始化、真实存储完成、Artifact/Version/Reference、inspect/revalidate、访问授权；
- ADD_VERSION、旧版本不可变、精确 Reference CAS、同键重放、异载荷冲突与并发单胜；
- detach、同槽位重绑、可用性变化/恢复、归档业务幂等；
- 上传回执重放、终止补偿和已有 Version 时禁止删除；
- 稳定游标、权限/租户/范围负向、失败审计和成功事实无重复。

### SOL 客户延期材料全链

命令：

```text
mvn.cmd -pl pms-module-engineering -am -Dtest=DurationChangeCustomerEvidenceEndToEndTest -DskipITs=false -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：21/21 PASS，真实 MySQL、Flowable、SOL Mapper、PLT 文件 API、PROJ 参与人事实与事务边界均装配。覆盖无材料与客户依据材料分支、提交和 APPROVE/REJECT/CANCEL、权限/范围/当前角色变化失败关闭，以及 BPM/SOL 同事务提交或共同回滚。

### 其他后端结果

- 真实 ClamAV 聚焦验证：8/8 PASS，覆盖规范成功、EICAR、协议异常与不可用失败关闭。
- 最终应用构建：`mvn.cmd -pl yudao-server -am -DskipTests package`，35 模块 BUILD SUCCESS。
- 浏览器生成的 `FileVersionCommitted` 与 `FileReferenceAttached` 各 2 条，均由 Quartz 投递为 `DELIVERED`；失败退避与同一 `eventId` 到期重领由 Task 5 的真实最终应用证据覆盖。

## 4. 真实浏览器验收

验收项目：`projectId=992201100001`，项目编码 `FPLT001-T10-BROWSER`。使用外部 Chrome DevTools 浏览器执行，符合内置浏览器不可用于自动化时的既有授权。

### 正向主线

1. 项目经理首次录入工期 V1：2026-09-01 至 2026-09-30。
2. 创建 `CUSTOMER_DELAY` 草稿，上传真实 PNG 文件；请求经过统一 PLT 上传、真实 ClamAV 和私有存储，草稿冻结 Artifact/Version/Reference。
3. 提交真实 Flowable 单节点审批；当前主责服务经理在平台 BPM 页面批准并填写意见。
4. 工期成为唯一当前 V2：2026-09-01 至 2026-10-15，共 45 天；界面显示 `PENDING_RECALCULATION`，历史同时保留 V1/V2。

### 单租户 BPM 整改清洁复验

1. 从空库执行 V1→V95 后启动最终应用，在 `yudao.tenant.enable=false` 下通过 `/admin-api/bpm/model/create` 和 `/admin-api/bpm/model/deploy` 创建、部署 `pms-sol-duration-change`；模型和定义的 Flowable tenant 标签均为空。
2. 项目经理在真实浏览器为 `projectId=992201100001` 录入 V1（2026-09-01 至 2026-09-30），创建 `CUSTOMER_DELAY` 草稿，上传真实 PNG 并从页面提交审批；运行中流程实例的 tenant 标签为空，SOL/文件业务事实 tenant 为 `0`。
3. 服务经理从平台 BPM 待办页进入该审批任务，随后通过公开 BPM 审批命令完成终态；未直接改写 Flowable 表。历史流程实例 tenant 标签为空且已结束，运行实例为 0。
4. 项目经理重新打开项目工期页，唯一当前工期自然成为 V2（2026-09-05 至 2026-10-10，共 36 天），变更为 `APPROVED`，`pending_change_id` 清空，计划重算状态为 `PENDING_RECALCULATION`；页面错误和 `console.error` 为 0。浏览器截图：`task10-r1-clean-browser.png`。

### 失败关闭与恢复

1. 创建并提交第二笔 `CUSTOMER_DELAY` 变更。
2. 在隔离验收库将其已冻结文件可用性事实从 `AVAILABLE/0` 推进为 `INVALIDATED/1`，模拟审批前文件事实变化。
3. 服务经理在真实 BPM 页面批准时收到“客户依据文件事实暂不可用”；SOL 保持 `PENDING_APPROVAL`、当前工期仍为 V2、Flowable 任务仍活动、无成功终态审计。
4. 恢复隔离夹具后，申请人在真实页面撤回；最终数据为 APPROVED 1 条、WITHDRAWN 1 条，两个 Artifact 均 ACTIVE、两个 Version 均 AVAILABLE。

### 浏览器运行质量

- 320 / 768 / 1024 / 1440 四档宽度：页面根宽度与 viewport 一致，无页面级横向溢出，工期面板与主要动作可见。
- 320 宽历史抽屉：宽度与 viewport 一致，V1/V2 均可见。
- 真实主题切换：卡片背景由浅色切换至深色，文字同步变化，恢复浅色后样式正常；无主题变量闪失或溢出。
- 页面错误、未处理 Promise 和 `console.error`：0。
- 观察资源 250 项：外部资源请求 0，外部 Iconify 请求 0，响应和页面中无永久文件 URL。

## 5. 前端与治理验证

- Node：v24.11.1
- pnpm：9.15.5（前端安装根执行 `corepack pnpm --version`）
- `pnpm-lock.yaml` SHA-256：`a060b9b0dce5a1ba5b537aa18112bb45693d545a4013ec9bb0f4ff00d23e23f4`
- 组件/运行时聚焦测试：3 个测试文件，15/15 PASS。
- `corepack pnpm ts:check`：PASS，0 error。首次运行因当时浏览器和 JVM 共同占用内存而退出；关闭专用验收浏览器后以相同代码重新运行通过。
- 定向 ESLint、Stylelint：PASS。
- `corepack pnpm build:local`：PASS，8291 modules；仅有既有 lightningcss `*zoom:1` 兼容警告。
- `scripts/validate_specification_baseline.py`：PASS。
- `git diff --check`：PASS。

## 6. 结论

F-PLT-001 的文件身份、不可变版本、精确引用、上传/访问、解绑重绑、可用性、归档、补偿、审计和四类 Outbox 已形成可运行的正向闭环；F-SOL-001 客户延期材料分支已通过真实 PROJ + SOL + PLT + Flowable + 浏览器验收。Task 10 可提交独立 Implementation Done 复审；正式状态仅在独立 GO 后回写。
