# F-PM02 项目树与详情验收记录

> 验收日期：2026-08-21
> 证据状态：`V1_7_ACCEPTANCE_EVIDENCE`
> 当前结论：`V1_8_REVALIDATION_REQUIRED`（不得根据现有实现或本记录判定 V1.8 已实现）

## 自动化验证

- 规格快照校验：`py -3.13 scripts/validate_specification_baseline.py` 通过。
- 项目模块测试：`mvn -pl pms-module-project -am test` 通过；上游 42 个、项目模块 115 个测试均为 0 失败。
- Flyway：隔离 MySQL 8.4 环境已执行并校验至 V62；V61/V62 为前向迁移，示例数据使用高段 ID 与 `creator='seed'`。
- 清单校验：`py -3.13 scripts/validate_implementation_baseline_inventory.py` 纳入最终门禁。

## 真实浏览器验收

Chromium 在宿主机前后端和 Docker MySQL/Redis/Flyway 环境完成以下闭环：

1. 根项目详情、直接下级按需加载、非直接后代不预加载。
2. 下挂子项目生成永久序号编码 `PJT-DEMO-920001-SP000006`，刷新后数据仍存在。
3. 子项目继承冻结模板版本并实例化 S0～S6 阶段及任务、里程碑、交付件、门禁。
4. 子树移动后父项目与树路径刷新并持久化；根项目移动到后代被业务错误码 `1014024009` 拒绝。
5. 混合空权重/人工权重被拒；整组设置 20/50/30 后汇总为 34%，移动测试子项目并恢复 60/40 后汇总为 44%。
6. 最终复验：控制台错误/警告 0，失败请求 0，HTTP 错误响应 0。

## 已记录的问题

- 当前全量前端 `ts:check` 仍被仓库既有跨模块类型错误阻断；本 Feature 修改文件未出现在错误清单中。
- Quartz 周期性报告缺少 `QRTZ_TRIGGERS` 表；不影响 F-PM02 API 与浏览器闭环，作为基础设施基线问题后续处理。
- 受管 `docs/traceability/requirement-matrix.md` 在锁定规格提交中仍为 PM-02 `NOT_STARTED`。V1.8 必须从当前首个 Feature 开始按工程链重新审计，本记录只能作为复用、改造或退役判定的证据。
