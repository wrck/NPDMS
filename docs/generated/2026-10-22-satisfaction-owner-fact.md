# 满意度任务业务区 Owner 事实查询修复

用户请求：继续排查并修复满意度业务区的 `OWNER_FACT_UNAVAILABLE`，完成后使用真实浏览器验收。

## 根因与修复边界

- 复现环境：当前工作树后端 `59191`、前端 `19191`、`npdms-domain-test-mysql-1 / npdms_domain_test`。
- 项目 `992203060191`（一汽大众_互联网出口IPS防护项目）的满意度任务 `8800017924661870063` 已关联两条本项目调查；调查办理人为杨凯添，当前登录用户为本项目服务经理周文成。
- 同一登录会话下，独立“满意度调查”入口可以查看已有完工证明、100 分评价及客户签字；任务业务区却显示 `OWNER_FACT_UNAVAILABLE`。
- `SatisfactionTaskBusinessObjectProvider.inspect` 已校验当前登录身份、满意度查询权限和 `PROJECT_VIEW` 项目范围，随后又调用仅允许调查办理人的旧 `SatisfactionTaskManagementService.get`。这一额外限制抛出 `SATISFACTION_TASK_NOT_FOUND`，被任务业务区转换为通用的 Owner 事实不可用。
- 修复仅调整任务业务区的只读事实检查，复用既有 Owner 事实读取与租户、项目、逻辑删除、结果归属校验。旧办理人查询、锁定命令、完成判定、候选对象查询及实际业务写接口保持原有授权。
- 不变更 PRD、数据库结构、历史答卷、评分、签字、关联关系或任务状态，不新增权限、角色或示例数据。

适用来源：`specs/features/F-ACC-002-satisfaction-questionnaire-result-and-deliverable-sync.md` 中已批准的满意度实体绑定和统一入口约束；`TaskBusinessObjectProvider` 的 Owner 查询与锁定命令分离契约。

## 验证

1. 失败复现：以真实 `SatisfactionTaskManagementService` 配合受控 Mapper 数据，复现“项目有查看权限、调查办理人是另一用户”的关联事实查询。修复前用例在 `get` 中抛出 `SATISFACTION_TASK_NOT_FOUND`，与页面错误链路一致。
2. 修复后定向测试 **63 项通过**：新增只读事实回归 8 项，原满意度事实提供方 7 项、办理查询 3 项、业务成果来源 8 项、结果事实 API 2 项、项目任务业务服务 35 项。
3. 拒绝测试覆盖缺查询权限、空或其他项目范围、伪造操作者/租户、缺失/跨项目/跨租户/已删除调查，以及结果租户和调查归属不符。未产生结果的调查返回真实的未完成事实。旧办理人查询及锁定命令继续拒绝非办理人。
4. 测试通过固定 JDK/Maven/工作树依赖仓库并持有工作树独占锁执行：`.run/satisfaction-owner-fact-20261022/run-tests.ps1`；失败复现附加 `-ReproduceOnly`。本次生产代码仅调整只读调用，无前端源码或 SQL 变更；未重复运行无关的前端全量检查。
5. `scripts/worktree-build.ps1 -Target backend -MavenArguments @('-DskipTests')` 全部模块构建成功，22 项既有修复基线校验通过。冻结发布清单：`.run/build/releases/b61b6f8e-f298-4cad-8789-dc010f01b5d9/manifest.json`；固定入口的 `-ValidateOnly` 部署前校验通过。
6. 经用户本次明确批准，使用 `scripts/worktree-backend.ps1 -Manifest <上述清单> -ApprovedRestart` 部署到 59191，保持 `local,dac`，PID `38196`，后端就绪检查通过。未操作 48182 的启停，部署后独立 DAC 就绪检查通过。
7. 真实浏览器沿用服务经理周文成的原登录会话：
   - 原异常项目的已完成满意度任务恢复业务列表，`OWNER_FACT_UNAVAILABLE` 消失；已完成任务没有新增、编辑、发起调查或重新推送入口。
   - 从任务业务区打开已确认记录，显示原 `2026-10-22 15:38:30` 提交的 100 分达标评价，10 项冻结评分保持禁用，原客户签字图片实际加载成功（600×270）。
   - 原满意度和完工证明交付文件仍显示 V1；点击满意度文件“版本历史”，显示原创建时间、V1、24 KB 和可用状态。关闭详情后重新打开，原评价和签字仍正常。
   - 当前项目 `2109733182766710786`（吉林省公安厅森林公安局网络改造项目）的未开始满意度任务正常加载空列表，保留“所属阶段不允许办理此任务”的提示及只读限制。
8. 浏览器验收后只读核对原项目调查、结果和关联的身份、状态及版本摘要，与验收前完全一致。未新建调查、代填签字、推进任务、更新评分或登记新文件；文件验证覆盖查看版本历史，未执行下载落盘验收。

临时验证证据：`.run/satisfaction-owner-fact-20261022/`，含失败/通过测试日志、构建及部署日志、修复前后页面截图和 DOM、详情重开、文件版本历史及前后 Owner 数据摘要。截图 `after-task.png` 为原异常任务修复后实际页面。
