# 现场工勘刷新完成依据修复

来源：本会话用户报告项目 `992203060191` 现场工勘刷新提示“业务完成依据暂不可用”。本记录仅为专项验证证据，不修改规格、审批状态或历史。

## 现场事实与原因

- 真实浏览器在独立诊断页复现：任务 `8800017924661870054` 的 `business/refresh` 返回 `completed=false, unknown=true`，随后 `business/context` 返回 `links=[]`。任务保持进行中。
- 只读数据库核对：该项目于 `2026-10-20 11:26:43.116` 重开；工勘 `2112503023339057154` 创建于 `19:15:43.830`、确认于 `19:19:33.458`，当前已归档，属于重开后的记录。另一条工勘已驳回，不应参与自动关联。
- 当前实体 `SiteSurveyTaskBusinessObjectProvider.handlingCreatedAt` 复用 `SiteSurveyEntityMapper.selectTaskObject`，但 XML 的 `taskFactColumns` 漏选 `create_time`。重开保护因拿到空创建时间而排除了有效记录，导致当前任务没有关联依据。旧工勘 Mapper 不是本次运行路径，不作修改。
- 已只读核对当前运行 JAR 内的实体 XML，同样缺少 `create_time`，确认不是仅工作树代码问题。

## 最小修复与验证

- 仅在当前实体 Mapper 的事实投影补齐 `create_time`；没有放松 WHERE 条件、权限、项目范围、重开时间边界、当前轮次校验或完成状态判断；未直接修改业务表数据及不可变历史。
- 新增 `SiteSurveyEntityTaskMapperTest` 执行真实实体 XML 与 Owner provider：创建时间读取、自动关联过滤、跨租户/跨项目/删除记录拒绝、完成读取行锁。修复前 2 项断言失败（实际创建时间为 null），修复后全部通过。
- 同跑 `SiteSurveyTaskBusinessObjectProviderTest`、`ProjectReopenBusinessEvidenceTest`、`ProjectTaskBusinessAssociationServiceTest`，合计 **55 项通过**。日志：`.run/site-survey-completion-before.log`、`.run/site-survey-completion-after.log`；执行入口 `.run/test-site-survey-completion.ps1` 使用固定工具链与工作树独占锁。
- 浏览器操作仅使用既有刷新命令进行问题复现；没有创建、确认、归档工勘或直接置任务完成。独立诊断页已关闭。
- 完整后端构建通过，22 项既有修复基线通过。新冻结产物为 `40144e1c-6847-4d50-86aa-8de0bcc5ddf6`，清单 `.run/build/releases/40144e1c-6847-4d50-86aa-8de0bcc5ddf6/manifest.json`；构建日志 `.run/site-survey-completion-build.log`。未替换旧冻结包。
- `scripts/worktree-backend.ps1 -Manifest <上述清单> -ValidateOnly` 部署预检通过（`.run/site-survey-completion-preflight.log`）。确认运行监听仍为 59191/PID 304、48182/PID 17780；项目工勘任务仍为 IN_PROGRESS/version 1，两条工勘记录状态与版本未变。

## 交付边界

遵守用户此前“暂不重启，保留产物”的决定。本次未重启 59191，48182 不动，未提交或推送，保留其他会话改动及旧冻结包 `35fad14c`。修复后的真实浏览器完成验收需要后续明确批准部署后执行；本次仅确认故障复现和修复的定向检查。
