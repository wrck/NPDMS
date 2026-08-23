# V1.8 组织与地点真实浏览器验收记录

执行日期：2026-08-23
状态：`FEATURE_BROWSER_ACCEPTANCE_PASSED`

## 范围与环境

- 工作树：`M:\AICoding\CodexData\worktrees\50eb\NPDMS`
- 分支：`codex/v1-8-feature-revalidation-50eb`
- 后端：`http://127.0.0.1:58081`
- 前端：`http://127.0.0.1:18082`
- 隔离基础设施：MySQL `23306`、Redis `26379`，Compose project `npdms-50eb-location-v18`
- 执行角色：租户 1 的 `admin`（用户 ID 1）
- 浏览器：真实外部 Chrome，由 Playwright 驱动。内置浏览器仍为优先入口，但本次会话未提供可调用控制接口；经用户明确批准后改用外部浏览器。

本次仅使用当前工作树的应用与隔离数据，未连接或操作 `npdms-t8-mysql-1`。

## 验收结果

### 1. 组织与地点管理

访问 URL：

- `http://127.0.0.1:18082/system/company`
- `http://127.0.0.1:18082/system/dept`
- `http://127.0.0.1:18082/customer-asset/asset-address`
- `http://127.0.0.1:18082/customer-asset/asset-site`
- `http://127.0.0.1:18082/customer-asset/area-department`

输入与可见结果：

- 公司 `V18-ACCEPT-CO / V1.8浏览器验收公司` 创建并保持。
- 部门（办事处）`OFFICE-V18-ACCEPT / V1.8浏览器验收办事处` 创建并保持，统一字段为 `system_dept.code`。
- 地址 `中国 / 浙江省 / 杭州市 / 拱墅区 / 湖墅南路V1.8验收88号` 创建并保持。
- 同一地址创建 `SITE-V18-ACCEPT-A`、`SITE-V18-ACCEPT-B` 两个站点，站点未绑定公司或办事处。
- 站点 A 创建六级位置树：`V18-CAMPUS -> V18-BUILDING -> V18-FLOOR -> V18-ROOM -> V18-RACK -> V18-U`。
- 创建精确映射 `330105 + DISTRICT -> OFFICE-V18-ACCEPT`。

刷新后公司、部门编码、完整地址、两个站点、六级树和映射仍可见。相关保存、分页、树查询和映射接口 HTTP 均为 200，业务码为 0。

逐操作证据：

| 操作 | 输入与参与者 | 请求结果 | 刷新后的可见结果 |
| --- | --- | --- | --- |
| 创建公司 | `admin`；`V18-ACCEPT-CO` | `POST /admin-api/system/companies/create`；HTTP 200 / code 0 | 公司分页仍显示 ID `930801`、编码和名称 |
| 创建办事处 | `admin`；`OFFICE-V18-ACCEPT` | `POST /admin-api/system/dept/create`；HTTP 200 / code 0 | 部门页仍显示 ID `930802`，`code` 未丢失 |
| 创建地址与两个站点 | `admin`；区县 `330105`、A/B 站点 | `POST /admin-api/pms/asset-locations/maintain`；HTTP 200 / code 0 | 地址 ID `930815`；站点 ID `930813`、`930814` 均可见 |
| 创建六级位置树 | `admin`；`V18-CAMPUS` 至 `V18-U` | 每级 `POST /admin-api/pms/asset-locations/maintain`；HTTP 200 / code 0 | `GET .../sites/tree?siteId=930813` 返回六级，末级 ID `930831` |
| 保存并解析办事处映射 | `admin`；`330105 + DISTRICT` | `POST .../area-department-mappings/save`；HTTP 200 / code 0 | `GET .../resolve?areaCode=330105&areaLevel=DISTRICT` 返回 `OFFICE-V18-ACCEPT` |

上述行均在保存后重新加载对应页面。截图按实际页面分别记录：

- 公司编码：[company-code.png](screenshots/company-code.png)
- 办事处部门编码：[office-code.png](screenshots/office-code.png)
- 地址：[address-admin.png](screenshots/address-admin.png)
- 两个站点与六级位置树：[location-admin.png](screenshots/location-admin.png)
- 区划—办事处映射：[area-office-mapping.png](screenshots/area-office-mapping.png)

### 2. 多站点项目与人工指派

访问 URL：`http://127.0.0.1:18082/pms/project-management/project-master-detail?projectId=920025`

输入与可见结果：

- 项目 `V1.8多站点浏览器验收项目` 同时绑定站点 A、B，站点 A 为唯一主站点，地点状态为 `RESOLVED`。
- 页面显示 `330105 -> OFFICE-V18-ACCEPT` 候选建议；授权人员人工确认后，一级服务经理指派成功，刷新后保持。
- 项目 `V1.8待维护地点浏览器验收项目` 仅保留兼容地点文本，状态为 `UNRESOLVED`，显示“待维护”，不产生自动办事处建议；授权人员在项目级范围内人工指定办事处和服务经理成功，刷新后保持。

关键项目分页、项目站点、映射解析和服务经理指派接口 HTTP 均为 200，成功操作业务码为 0。

逐场景证据：

| 场景 | 输入与参与者 | 关键请求结果 | 刷新后的可见结果 |
| --- | --- | --- | --- |
| 多站点项目 | `admin`；项目 ID `920025`；站点 A/B，A 为主 | `POST /admin-api/pms/projects`；HTTP 200 / code 0；`GET /admin-api/pms/projects/920025/sites` HTTP 200 | 详情页仍显示两个站点、一个主站点和 `RESOLVED` |
| 映射建议与人工确认 | `admin`；`330105 + DISTRICT` | `GET .../area-department-mappings/resolve`；HTTP 200 / code 0；返回 `OFFICE-V18-ACCEPT` | 指派弹窗显示建议，同时办事处选择仍可手工调整 |
| resolved 项目指派 | `admin`；项目 `920025`、一级服务经理、办事处编码 `OFFICE-V18-ACCEPT` | `POST /admin-api/pms/projects/920025/actions/assign-manager`；HTTP 200 / code 0，时间 `2026-08-23T14:17:11Z` | 刷新详情页后指派仍存在 |
| fallback 项目 | `admin`；项目 ID `920026`，仅兼容地点文本 | `POST /admin-api/pms/projects`；HTTP 200 / code 0 | 刷新后仍为 `UNRESOLVED` 和“待维护”，无站点、无自动建议 |
| fallback 人工指派 | `admin`；项目 `920026`、手工办事处与服务经理 | `POST /admin-api/pms/projects/920026/actions/assign-manager`；HTTP 200 / code 0，时间 `2026-08-23T14:20:12Z` | 刷新后人工指派仍存在 |

项目截图按实际可见状态分别记录：项目列表与筛选结果、详情基础信息、resolved 项目的区划建议和可手工调整入口、fallback 项目的无建议人工确认提示。项目站点关系及刷新持久化由 `GET /projects/{id}/sites` 与页面重载记录共同支撑，不把单张截图表述为全部场景证据。

测试前置：当前基础平台已有公司—部门授权关系模型和 API，但尚无对应管理页面，因此为 `admin` 在隔离数据库预置了同一行公司—部门授权关系。该前置不作为浏览器功能证据，并登记为后续基础平台管理 UI 缺口。

截图：

- 项目列表：[project-multi-site.png](screenshots/project-multi-site.png)
- 项目详情基础信息：[project-multi-site-detail.png](screenshots/project-multi-site-detail.png)
- 区划映射建议与人工调整：[project-mapped-assignment.png](screenshots/project-mapped-assignment.png)
- fallback 无自动建议、仅人工确认：[project-fallback-assignment.png](screenshots/project-fallback-assignment.png)

### 3. 工勘、安装与设备位置历史

访问 URL：

- `http://127.0.0.1:18082/pms/engineering/preparation/eng-site-survey`
- `http://127.0.0.1:18082/pms/engineering/execution/eng-installation`
- `http://127.0.0.1:18082/customer-asset/equipment`

输入与可见结果：

- 工勘 `SURVEY-V18-BROWSER-001` 现场新建地址、站点 `SITE-V18-SURVEY` 和位置 `SURVEY-ROOM / V1.8工勘新增机房`，状态为 `RESOLVED`；刷新后保持。
- 工勘完成后设备 `SN-V18-BROWSER-001` 仍为 `UNRESOLVED`，没有 `LOCATION_EFFECTIVE` 历史，证明工勘不会提前改变设备当前位置。
- 安装 `INSTALL-V18-BROWSER-001` 开始并完成后，设备当前位置生效为 `V1.8工勘新增机房`。
- 安装 `INSTALL-V18-BROWSER-002` 开始并完成后，设备当前位置迁移为 `V1.8验收U位`；前一安装区间的 `effective_to` 等于第二次生效时间。
- 设备详情刷新后显示当前位置 `V1.8验收U位`，并显示两条 `LOCATION_EFFECTIVE` 历史。
- 对第二笔安装构造陈旧版本更新，接口 HTTP 200、业务码 `1011005003`；刷新后安装位置仍为 `V1.8验收U位`，没有部分结果。

数据库反查同时确认：两笔安装时间分别为 `2026-08-23 22:51:35`、`2026-08-23 22:51:54`；设备当前位置指向第二笔安装；被引用的 `V18-U` 和 `SURVEY-ROOM` 编码、名称、树路径及版本保持不变。

逐场景证据：

| 场景 | 输入与参与者 | 关键请求结果 | 刷新/反查结果 |
| --- | --- | --- | --- |
| 工勘维护地点 | `admin`；工勘 ID `30009`、`SITE-V18-SURVEY / SURVEY-ROOM` | `POST /admin-api/pms/eng-site-survey/create`；HTTP 200 / code 0 | 刷新工勘页仍为 `RESOLVED`；设备 `2030` 仍 `UNRESOLVED` 且无位置生效历史 |
| 第一次安装 | `admin`；安装 ID `30018`、工勘新增位置 | `POST .../eng-installation/create`、`PUT .../start?id=30018`、`PUT .../complete?id=30018`；均 HTTP 200 / code 0 | 刷新设备详情，当前位置为 `V1.8工勘新增机房` |
| 第二次安装迁移 | `admin`；安装 ID `30019`、位置 ID `930831` | `POST .../eng-installation/create`、`PUT .../start?id=30019`、`PUT .../complete?id=30019`；均 HTTP 200 / code 0 | 设备当前指针改为 `930831`；前一位置区间 `effective_to=2026-08-23 22:51:54` |
| 历史查询 | `admin`；设备 ID `2030` | `GET /admin-api/pms/equipment/version/list?equipmentId=2030`；HTTP 200 / code 0 | 页面刷新后当前位置为 `V1.8验收U位`，`LOCATION_EFFECTIVE` 两条 |
| 陈旧版本拒绝 | `admin`；安装 `30019` 的旧版本位置更新 | `PUT /admin-api/pms/eng-installation/update`；HTTP 200 / code `1011005003`，时间 `2026-08-23T14:52:46Z` | 重新查询安装与设备，仍指向 `930831`，没有第三条生效历史或部分结果 |

数据库反查明细：安装 `30018` 状态 2、版本 4，安装 `30019` 状态 2、版本 3；设备 `2030` 的 `site_location_id=930831`、来源安装 `30019`、版本 2；两条 `LOCATION_EFFECTIVE` 版本事实均存在。共享位置 `930831`、`930832` 的编码、名称、路径与版本保持验收前值。

截图：

- 设备当前位置与两条变更轨迹：[installation-device-location.png](screenshots/installation-device-location.png)
- 陈旧版本错误消息：[stale-installation-error-message.png](screenshots/stale-installation-error-message.png)

## 验收中发现并修复的问题

- 现场新建站点未提交必填 `site_type`：地点选择器按既有约定提交 `CUSTOMER_SITE`。
- 安装状态流转在 `@Version` 更新前手工增加版本，导致更新 0 行却返回成功：改由 MyBatis-Plus 管理版本并校验影响行数。
- 未选择安装时间时提交空字符串，被解析为 1970 年：改为不提交空时间，由开始安装动作写入当前时间。
- 已有位置引用携带空的编码、名称和类型，意外修订共享位置：前后端统一支持仅 `id + expectedVersion` 的引用语义，并保持响应 DTO 的必填字段契约。

上述后端问题在实现后补充单元或真实 MySQL 回归；前端两个请求构造回归点补充了可执行源码契约测试。当前执行采用用户指定的非 TDD 顺序。

回归结果：

- 前端地点选择器与安装表单契约测试：6/6 通过；相关 Vue/TypeScript 文件定向 ESLint 通过。
- 前端 `pnpm build:prod` 通过；构建仍输出仓库既有的 `%VITE_APP_TITLE%` 和 legacy CSS `*zoom` 警告，不影响产物生成。
- `InstallationLocationServiceTest`：7/7 通过，覆盖状态更新 0 行转业务冲突。
- `InstallationOptimisticLockMySqlIntegrationTest`：1/1 通过，在隔离 MySQL 中由真实 MyBatis-Plus `OptimisticLockerInnerInterceptor` 完成首写版本递增，并拒绝同一旧版本的第二次更新。
- `SiteLocationTreeServiceImplTest`：3/3 通过，覆盖仅引用输入不更新共享位置。

## 结论边界

三组真实浏览器场景和刷新持久化检查通过，证据文件与相关截图齐全。该结论只属于当前 Feature 的实现验收证据，不构成 UAT、发布 Gate、治理 GO 或 Release GO。

全局 `vue-tsc` 的既有 251 项基线错误未在本 Task 中清理，因此本记录不声称全局 type-check 通过；该事项不阻断本 Feature 的浏览器验收，但计划总完成条件第 10 项仍须在后续单独收敛。
