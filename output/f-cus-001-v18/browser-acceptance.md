# F-CUS-001 V1.8 浏览器验收

验证日期：2026-08-26

结论：`PARTIAL PASS`。当前真实数据可执行路径通过；依赖 CRM 权威分类目录或可删除客户的数据路径保持未完成。

## 隔离环境

- 前端：`http://127.0.0.1:18084`
- 后端：`http://127.0.0.1:58081`
- 页面运行于独立 Chrome 上下文 `fcus001-58081`。
- 浏览器请求实际访问 `http://localhost:58081/admin-api`。

## 已通过闭环

- 新客户工作台加载 5 条迁移客户，`GET /admin-api/pms/customers?pageNo=1&pageSize=10` 返回 HTTP 200。
- 客户 `1005` 仅更新简称成功，版本从 `0` 变为 `1`；刷新后数据库保持 `short_name=成都智慧验收`。
- 客户 `1005` 停用成功，版本从 `1` 变为 `2`，状态从 `ENABLED` 变为 `DISABLED`。
- 客户 `1005` 删除被 PROJ/AST 引用守卫稳定阻止，业务码为 `1014001004`；数据库版本、生命周期和软删除标记均无副作用。
- 旧客户页面明确显示“客户历史（只读）”和数据截止说明，只提供详情操作，不提供创建、编辑、停用、删除或恢复操作。
- 旧页面仍显示迁移前简称“成都智慧”，不显示当前主档简称“成都智慧验收”，证明历史快照与当前 CUS 主档分离。
- “前往新客户工作台”跳转到动态菜单前端路径 `/customer-asset/customers`；后端资源路径仍为 `/pms/customers`。
- 320×844、768×900、1024×900、1440×900 四档真实设备视口均满足 `scrollWidth=clientWidth`，无页面级横向溢出，客户 `CUST-005` 可见。
- 320×844 下新工作台与旧历史页控制台均无 error；客户列表与旧历史列表请求均返回 HTTP 200。

## 响应式证据

- 320×844：`innerWidth=320`，`scrollWidth=320`，`clientWidth=320`，`overflowX=false`。
- 768×900：`innerWidth=768`，`scrollWidth=768`，`clientWidth=768`，`overflowX=false`。
- 1024×900：`innerWidth=1024`，`scrollWidth=1024`，`clientWidth=1024`，`overflowX=false`。
- 1440×900：`innerWidth=1440`，`scrollWidth=1440`，`clientWidth=1440`，`overflowX=false`。

本轮通过设备指标仿真真实达到 320 像素宽度，关闭了此前普通窗口最小宽度为 500 像素的验收缺口。

## 未完成与阻断

- 平台客户创建、临时客户创建：`cus_market_relation` 当前为 0 条，且没有可用 CRM 权威四级分类来源。禁止猜测或伪造 `marketCode/systemCode/expendCode/industryCode`，因此无法合法完成创建闭环。
- 删除成功与恢复：现有客户均为迁移业务数据，客户 `1005` 存在 PROJ/AST 引用并被守卫阻止；当前没有合法创建的无引用客户，也没有可用于恢复的已删除客户。
- CRM 字段只读与合法四级级联选择：受同一权威分类目录缺失阻断。
- 截图文件未落盘：浏览器截图接口拒绝写入当前工作树，返回工作区白名单限制。DOM 快照、网络状态、控制台状态和设备视口数据已完成验证，但本记录不将截图标记为已生成。

本记录只证明当前可执行浏览器路径，不构成完整 UAT、发布 Gate 或 Release GO。
