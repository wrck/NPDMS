# 自动归集交付件仅保留查看

来源：2026-10-22 需求方要求交付件清单中的自动归集材料与业务成果不显示提交按钮，并确认自动归集项始终只查看（含尚无材料、同时配置手工来源的项），纯手动项保留提交。

## 实现

- 项目实例、任务工作台与交付件详情透传 `automaticCollection`。判定复用当前有效冻结计划中的自动来源和原生任务归集规则，以及验收报告/满意度既有绑定；清单按项目批量读取，不按交付件逐项请求。
- 阶段、任务、门禁及交付件清单将自动项入口改为“查看/查看材料”。详情隐藏上传、关联成果表单和提交材料按钮，保留历史材料及原文件访问入口。
- 不迁移数据库，不改冻结配置、自动归集写入、生命周期、服务端权限或历史记录；`automaticCollection` 仅为视图标识。

## 验证

- 后端 109 项定向测试通过：首次验收模块 62 项，修正新增视图字段涉及的两个项目测试夹具后，项目模块 47 项通过。范围包括自动来源与原生任务匹配、纯手动区别、无材料判定、租户/项目隔离、历史访问、既有归集守卫、计划历史保护与工作台字段传递。
- 前端 3 个定向测试文件共 51 项通过：交付件弹窗、阶段门禁结果、项目流程面板。覆盖混合来源在有/无材料时均隐藏提交、保留历史显示和纯手动上传/成果关联。
- 全量 `pnpm ts:check` 未通过，共 11 处错误，位于本次未修改的 `EntityForm.vue`、`with-device-logs.vue`、`solutionRequirementSources.runtime.spec.ts`、`TaskBusinessPanel.recovery.runtime.spec.ts`；本次交付件文件未报类型错误。
- 本次变更范围的 `git diff --check` 通过。
- 后端完整构建、冻结校验和 `worktree-backend.ps1 -ValidateOnly` 均通过；冻结清单为 `.run/build/releases/ce24df1e-9d33-4ca4-90c6-beac96383bdd/manifest.json`，保留 `local,dac` 配置和 22 项既有修复校验。
- 真实浏览器已复现调整前问题：项目 `992203060191` 的“经审批施工计划”已归集“已批准施工计划”成果，清单仍有“提交与查看”，弹窗仍显示上传/关联成果及“提交材料”。
- 按本次明确批准部署后，真实浏览器使用现有登录会话验证：上述项目的施工计划、配置日志、满意度交付件清单均改为“查看”，详情无上传/关联成果表单和“提交材料”按钮。施工计划原成果仍显示；配置日志历史 PDF 的文件名、V1、版本历史、预览/下载入口仍显示。未触发文件下载，不宣称文件已落盘。
- 项目 `2109733009617453057`（吉林省纪检监察外网省级平台建设）的施工计划仍在进行中，交付件尚无提交记录；清单与详情仍只保留查看，验证了未形成材料时也不出现提交入口。纯手动提交回归由前述组件运行测试验证，本次未新建或提交业务测试数据。
- 满意度任务业务区另观察到 `OWNER_FACT_UNAVAILABLE`，但其交付件查看正常；该业务区未记为验收通过，未扩大本次修复范围。现象见 `after-satisfaction-dialog.txt`，具体原因未在本次定位。

复现及验证日志位于 `.run/deliverable-auto-readonly-20261022/`。后端定向测试使用该目录 `run-tests.ps1`（固定工具链、独占工作树锁）；前端命令为 `pnpm exec vitest run --config vitest.pms-file.config.ts`，指定上述三个测试文件。后端冻结打包使用 `scripts/worktree-build.ps1 -Target backend -MavenArguments @('-DskipTests')`。

## 部署边界

需求方已明确批准本次重启，使用上述冻结清单经 `scripts/worktree-backend.ps1 -ApprovedRestart` 部署到 59191，保留 `local,dac`，后端健康检查通过（本次启动 PID 21376）。48182 未启停，部署后其 readiness 只读检查通过；未在本次执行设备解析业务。真实浏览器验证发生在本次部署之后；证据保留于上述日志目录的 `after-plan-list.txt`、`after-plan-dialog.png/.txt`、`after-configuration-dialog.png/.txt`、`after-satisfaction-dialog.txt`、`after-empty-dialog.png/.txt`。
