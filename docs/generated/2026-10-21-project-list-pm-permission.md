# 项目经理打开项目列表出现权限提示的局部修复

来源：用户反馈项目经理打开 `/pms/project-management/projects` 提示“没有该操作权限”。仅处理当前项目列表初始化，不调整角色授权、服务端项目范围或其他会话改动。

## 定位

- 使用既有账号 `y06496` 的真实后端登录会话做只读接口核对，返回角色仅 `PROJECT_MANAGER`，具有 `pms:project:query`，没有 `pms:project:create`、`pms:asset-location:query`。
- 项目列表成功返回 4 个可见项目；超期统计、公司/办事处/用户精简列表及客户筛选查询均成功。`/admin-api/pms/asset-locations/sites/page?pageNo=1&pageSize=100` 返回业务 403、“没有该操作权限”。脱敏记录 `.run/project-list-pm-api-readonly.json`，未保存登录凭据或 Token。
- 项目列表 `index.vue` 的 `onMounted` 原先无条件调用 `loadOrganizationAndSites()`，将列表筛选使用的公司、部门与仅供创建向导使用的站点选项一起查询。服务端站点接口保留 `pms:asset-location:query` 校验，项目经理无此权限时拒绝正确；前端不应在浏览列表时调用该接口。

## 修复与保护

- 初始化仅加载公司、部门精简选项，站点列表改为创建向导打开后按需加载。
- 创建向导入口检查既有 `pms:project:create`，站点请求另检查既有 `pms:asset-location:query`；未授权时不发请求。原站点选择及其版本同步保留，原兼容地点录入方式保留。
- 不修改后端授权、项目范围、角色菜单或数据权限，不增加权限键、不处理业务审批或修改业务记录。只读复核角色菜单数量仍为项目经理 168、服务经理 204，二者 `data_scope` 均为 5，与排查前一致。

## 验证及边界

- 新增实际页面组件运行检查：修改前 3 项失败；修改后 **4 项通过**，覆盖列表不访问站点接口、组织筛选仍可用、授权创建时才加载站点及版本、缺少站点权限不请求、缺少创建权限不打开向导。日志 `.run/project-list-pm-ui-before.log`、`.run/project-list-pm-ui-after.log`。
- 既有 `index.spec.ts` 创建相关检查 **7 项通过、2 项既有断言失败**：要求页面含 `resolveAreaDepartment`，以及要求工勘页面仍含“仅保留历史查询”。两个字符串在修改前 HEAD 对应页面即已不存在；本次未改其业务实现或降低断言。日志 `.run/project-list-pm-creation-regression.log`。Node 原生入口另需 `.run/project-list-node-alias.mjs` 将既有 `@/utils` 解析到真实源码。
- 19191 实际提供的 Vite 模块 HTTP 200，已包含新组织加载函数及站点查询权限检查，不再包含原合并加载函数；证据 `.run/project-list-pm-vite-served.json`。这是服务源码加载检查，不能替代浏览器验收。
- 真实浏览器连接恢复发现仍无可用浏览器，未能捕获项目经理页面网络或确认提示消失；记录 `.run/project-list-pm-browser-before.json`。**真实浏览器验收待补**。
- 本次仅改前端，无迁移，无后端构建或部署，不需要重启 59191，未操作 48182。保留其他会话改动和既有冻结产物，未提交、未推送。
