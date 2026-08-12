# PMS 项目实施约束

## 事实来源

- `specs/001-project-delivery-platform/` 是需求与接口的唯一事实来源。
- `tasks/plan.md` 和 `tasks/todo.md` 分别定义实施方案、任务依赖与检查点。
- 本项目禁止使用项目记忆补全需求、设计或验收结论；不确定事项必须回到仓库文档或标记为【待确认】。

## 技术基线

- 后端基线：`yudao-boot-mini` `master-jdk25`，锁定提交见 `docs/upstream-sources.md`。
- mini 缺失的 Yudao 模块：仅从 `YunaiV/ruoyi-vue-pro` 的锁定提交按需导入。
- 共享文件以 mini 为准，不得用完整仓库整体覆盖。
- 首期为模块化单体，由 `yudao-server` 统一装配；PMS 业务模块统一使用 `pms-module-*`。
- Yudao 平台接口保持上游定义；新增 PMS Business API 遵循 `/api/v1/pms/...` 规范。

## 运行与验证

- Docker Compose 是开发、构建、迁移、启动和验收的权威运行入口。
- 不要求宿主机预装 JDK、Maven、Node.js、数据库或 Redis。
- 配置模板不得包含真实凭据；本地默认值只能用于隔离开发环境。
- 每项自研任务遵循失败测试、最小实现、重构与完整验证顺序。
- 编译、静态页面或 API 单测不能替代业务验收；UI 闭环必须由真实浏览器完成。

## 领域边界

- 项目组合与项目父子树是两个独立模型。
- 项目树和任务 WBS 都不得实现为固定层级。
- 模块间不得依赖目标模块的 `-biz`、Service、Mapper、Repository 或直接访问其业务表。
- 无稳定跨模块调用方时，不创建空的 `-api` 模块。
