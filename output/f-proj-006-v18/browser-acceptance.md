# F-PROJ-006 V1.8 浏览器验收

结论：`PASS`。Task 10 所缺的正向治理动作已在真实 Chromium 中完成；本文件作为 Feature Done 复审输入，不自行替代独立裁决。

## 验收环境

- 前端：当前工作树宿主机 Vite，`http://127.0.0.1:18082`。
- 后端：当前提交构建的 `yudao-server.jar`，隔离端口 `58081`。
- 基础设施：当前工作树专用 Compose 项目 `npdms-50eb-fproj006`，MySQL `13308`、Redis `16381`。
- 数据库：空卷从 V1 前向迁移至 V87，Flyway 返回 `Successfully applied 87 migrations`。
- 浏览器：Chromium `151.0.7922.34`，1440×900。
- 验收项目：叶项目 `992002000030`；本地夹具仅增加管理员主责服务经理、当前项目 `PROJECT_MANAGE`、四个稳定治理权限和原因值 `BROWSER_ACCEPTANCE`。
- 运行配置：BPM 已知流程定义键为 `project-progress-policy`；治理令牌使用未写入仓库的一次性本地签名值。

## 正向闭环

1. 初始项目为 `ACTIVE / S0 / UNASSIGNED / version=0`，治理历史为空。
2. `ROLLBACK` 守卫通过，冻结 5 个必需 Provider；提交成功并追加快照 1，当前主责服务经理区间结束。
3. `EXCEPTION_CLOSE` 守卫通过；提交成功并追加快照 2，项目进入 `EXCEPTION_CLOSED`。
4. `REOPEN` 守卫通过；消费快照 2 并追加关联快照 3，项目恢复 `ACTIVE / S0 / UNASSIGNED / version=3`。
5. 页面刷新后仍显示 3 条治理历史，动作顺序、状态变化、原因、操作者和时间均可见。

三次守卫 GET 与三次动作 POST 均为 HTTP 200；`consoleErrors`、`pageErrors`、HTTP `>=400` 均为 0。机器结果与最终截图：

- `output/f-proj-006-v18/browser/fproj006-task10-positive-chain.json`
- `output/f-proj-006-v18/browser/fproj006-task10-positive-chain.png`

## 物理事实核验

- `proj_project_stage_snapshot`：恰有 `ROLLBACK / EXCEPTION_CLOSE / REOPEN` 三条，重开记录的 `related_snapshot_id` 指向异常关闭快照。
- `plt_operation_audit`：三条 `SUCCESS`，操作码分别为 `PROJECT_ROLLBACK / PROJECT_EXCEPTION_CLOSE / PROJECT_REOPEN`。
- `plt_outbox_event`：`ProjectStageChanged / ProjectClosed / ProjectStageChanged` 各一条，均无重试。
- `plt_idempotency_record`：三个命令范围均为 `COMPLETED`。

## 环境观察

长期运行的 `npdms-50eb-mysql-1` 仍停在 V85，且其已执行 V85 校验和与当前仓库不一致；本次没有执行 `repair`、没有手工补跑 V86/V87，也已清除误加到该库的验收夹具。该历史环境漂移不用于本次通过结论，后续应按数据库基线治理单独处理。

原四档响应式、主题持久化和失败关闭证据保持在同目录，未重复执行。
