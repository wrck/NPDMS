# 基础平台与首批业务实施执行计划

## Global Constraints

- 上游 mini 固定为 `e6d814cb59cfc204f02aa2516799073382aba801`。
- 上游完整后端固定为 `a6558325b0f09017f531f1e5891613ef9b468132`。
- 上游 Vue3 管理端固定为 `2d028c8f7a14dd2e532ac1a76d1fdf58840dc621`。
- Yudao 平台接口、鉴权、响应和错误语义以平台源码为准；新增 PMS Business API 使用 `/api/v1/pms/...`。
- BPM 必须作为同一平台的完整迁移单元导入：源码、依赖、SQL、菜单权限、前端增量和装配保持同一锁定版本。
- 使用 JDK 25、MySQL 8.4 LTS、Redis、Docker Compose；Flyway CLI 在 Docker 中运行，应用不增加 Flyway 运行时依赖。
- 前端位于 `yudao-ui/yudao-ui-admin-vue3/`，响应式 Web 优先，不提前实现移动端或桌面客户端。
- 不创建 PMS 页面或业务代码，直至基础平台构建、迁移、启动、登录和真实浏览器冒烟验收通过。
- 不提交密码、密钥、真实凭据或生产连接信息。

## Task 4: T-CP-004 从完整仓库导入 BPM 完整迁移单元

从锁定的完整后端仓库提交导入 BPM 源码、依赖、SQL、菜单权限及必要装配。共享文件冲突时以 mini 基线为主，只引入 BPM 所需最小差异；根 POM 与 `yudao-server` 显式启用 BPM。逐文件记录来源和兼容补丁。验证 `mvn -pl yudao-module-bpm -am test`，并为后续启动后的流程定义创建与查询验证保留可执行入口。

## Task 5: T-CP-005 建立完整 Vue3 管理端基线

从锁定的官方 Gitee Vue3 管理端提交，将完整前端导入 `yudao-ui/yudao-ui-admin-vue3/`。保持上游平台 API 和权限语义；确认 system、infra 与 BPM 页面及 API 增量完整；不得创建 PMS 页面。验证依赖安装、lint 和生产构建。

## Task 6: T-CP-006 建立数据库迁移、本地配置与 Docker 基线

建立 Docker Compose 的 MySQL 8.4 LTS、Redis、Flyway CLI、JDK 25 后端与前端运行环境。空库可初始化，重复迁移有历史和校验和门禁；配置模板无真实凭据。验证数据库、Redis、后端和前端连接，并运行后端完整校验。

## Task 9: T-CP-009 创建测试与追溯骨架

建立单元、集成、契约、Playwright E2E、性能与安全测试目录和最小可执行健康测试；测试结果可关联任务与 FR；Playwright 版本与官方容器镜像精确一致。

## Task 10: T-CP-010 完成基础工程验收

按开发文档从 Docker 环境构建、迁移、启动、登录和停止。通过真实浏览器按钮操作验证登录、基础菜单、system、infra 与 BPM 的关键平台功能；检查浏览器控制台、HTTP 响应和业务成功码，无未处理错误。基础平台确认无误后才允许开始 PMS 业务需求。

## Task 11: T-V1-PLT-001 首批业务管理能力

在基础平台验收通过后，按已确认规格实施本地认证复用、组织同步与角色权限的 PMS 业务接入；平台能力不重复建设，新增业务权限按 Yudao 规范实现。保持 REQ 到测试证据的追溯，并以真实浏览器完成授权与无权场景验证。
