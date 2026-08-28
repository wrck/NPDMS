# F-PROJ-003 项目子树授权与统一数据范围验收记录

> 验收日期：2026-08-24
> 规格基线：PRD V1.8，锁定规格提交 `69b2b48320a858f91d938ae6914146681c97fb0a`
> 证据状态：`IMPLEMENTATION_DONE`
> 边界：仅关闭 F-PROJ-003 Implementation Done，不进入 Deployment、SIT、UAT 或 Release

## 自动化与架构验证

- `mvn.cmd -Ppms-test-unit -DskipITs=true test`：全 Reactor `BUILD SUCCESS`；F-PROJ-003
  相关平台授权 4 项、平台命令 6 项、项目授权应用 6 项、授权守卫 6 项、项目范围 9 项通过。
- `mvn.cmd -Ppms-test-contract -DskipITs=true test`：全 Reactor `BUILD SUCCESS`；项目契约套件
  30 项通过，其中项目授权控制器契约 3 项通过。
- `mvn.cmd -Ppms-test-integration -DskipITs=false test`：全 Reactor `BUILD SUCCESS`；项目模块
  12 项、工程模块 2 项、资产模块 2 项真实 MySQL 集成测试通过。
- `AuthorizationGrantMySqlTest`：7/7，通过生效区间、撤权历史、幂等冲突、并发唯一、版本冲突、
  稳定分页和数据库区间约束。
- `ProjectTreeAuthorizationMySqlTest`：5/5，通过角色不穿透、后代授权与撤权、到期与空主体、
  树移动重算以及 VIEW 不提升为 MANAGE。
- `ProjectTreeMoveConcurrencyMySqlTest`：1/1，通过同树版本并发移动只有一个成功事实。
- 授权与范围实现不装配 Redis 缓存，专项 MySQL 测试切片未依赖 Redis 仍全部通过；授权收缩
  直接读取 PLT 当前事实，不存在旧缓存继续放行路径。
- 生产源码架构扫描未发现跨模块引用目标模块 `service` 或 `dal`。扫描命中仅存在于真实 MySQL
  集成测试的测试装配中；PROJ 生产代码通过 `pms-module-platform-api` 调用授权能力。

## 真实 MySQL 事实

- 浏览器最终撤权事实：`plt_authorization_grant.id=2091916726910586882`，资源
  `992002000000`，动作 `PROJECT_VIEW`，范围 `CURRENT_PROJECT`，状态 `REVOKED`，版本 `1`。
- 当前树事实：根项目 `992002000000` 的活动树版本为 `2`，节点数 `35`、路径数 `504`；旧版本
  `1` 仍保留，范围计算以最新活动版本执行。
- V79 组合事实 `992003100001`～`992003100006` 覆盖当前项目、全部后代、ACTIVE、EXPIRED、
  REVOKED、VIEW、MANAGE、未来生效及不同用户；测试产生的临时事实均由测试清理。
- 负向错误分类：最小权限服务经理访问无权项目返回业务码 `1014003000`（项目不存在，避免泄露
  名称与业务明细）；并发、幂等冲突及版本冲突由上述 MySQL 与契约测试断言。

## 真实浏览器与响应式验证

内置浏览器交接无法形成稳定的自动化控制链后，按用户明确授权使用系统 Edge，并由 Codex
内置 Playwright 运行。使用仅含项目查询、授权查询、授权管理和撤权权限的服务经理账号执行：

1. 打开项目授权面板，切换范围筛选为 `PROJECT_AND_DESCENDANTS`，请求参数和结果均正确。
2. 访问无权项目被 `1014003000` 拒绝；页面未调用无权限的系统用户查询接口。
3. 创建当前项目 VIEW 授权，刷新后保持 ACTIVE；撤权携带 `If-Match: 0`，刷新后保持 REVOKED。
4. 创建与撤权请求均携带不同的 `Idempotency-Key`，撤权事实最终落库为版本 1。
5. 1440、1024、768、320 四档视口分别验证；前三档使用表格，320 使用卡片，无页面级横向
   溢出，无残留菜单悬浮层，控制台错误、页面异常和未解释失败响应均为 0。

## AC 逐项结论

| AC | 结论 | 主要证据 |
|---|---|---|
| AC-FPROJ003-001 | PASS | 范围服务测试与真实 MySQL 后代授权收缩 |
| AC-FPROJ003-002 | PASS | 角色不自动扩大范围；浏览器无权项目返回 `1014003000` |
| AC-FPROJ003-003 | PASS | 授权守卫、控制器契约和无副作用负向测试 |
| AC-FPROJ003-004 | PASS | 撤权、到期、历史保留及浏览器刷新保持 |
| AC-FPROJ003-005 | PASS | 活动树版本 2、移动重算及并发移动测试 |
| AC-FPROJ003-006 | PASS | 幂等重放/冲突、并发唯一、`If-Match` 版本冲突 |
| AC-FPROJ003-007 | PASS | 当前 PROJ 入口统一范围单测；空范围不扩权 |
| AC-FPROJ003-008 | PASS | 生产源码边界扫描及 API 依赖方向 |
| AC-FPROJ003-009 | PASS | Edge 完整授权闭环与四档响应式验证 |

## 实施提交链

- `e4c7a3e`：归位平台命令事实所有权。
- `38de348`：实现授权事实与生命周期。
- `a836e51`：发布项目子树范围契约。
- `c9f5411`、`66cbee6`：增加授权管理入口并统一当前项目入口范围。
- `c2648c5`：初始化授权字典、菜单和组合示例事实。
- `c1c76d5`：增加响应式授权维护界面。
- `6722782`：修复小屏折叠菜单状态与首屏主题初始化，恢复 UI 验收前提。
- `1ae5f2a`：修复新增范围依赖后的 MySQL 测试切片。

结论：F-PROJ-003 的 AC-FPROJ003-001～009 均有 V1.8 重新检查、改造和验证证据，当前
Feature 达到 `IMPLEMENTATION_DONE`。AUT-01/AUT-02 完整 OA 申请以及尚未实施业务对象接入
`ProjectScopeApi` 仍按已批准范围留给后续 Feature，不反向阻断本 Feature。
