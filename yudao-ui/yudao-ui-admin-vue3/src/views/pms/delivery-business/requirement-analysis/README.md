# PRE-04 需求分析 Demo V2 表单内容

本目录交付新增表单内容，不是第二套需求分析页面或表单引擎。唯一生产内容是 `demo-template.json`，可作为既有 PLT 表单草稿的 `formConfJson/formRulesJson` 输入；不包含模板身份、修订号、发布状态、项目绑定或 API 请求。

依据：PRD 的 PRE-04、`specs/features/F-SOL-003-requirement-analysis-versioning.md` 的 BR-FSOL003-002，以及用户提供的 `需求/项目交付/项目交付页面数据DemoV2.html` 中“2.3需求分析”页面。认领见 `tasks/delivery-units/DU-20260909-DELIVERY-DEMO-BUSINESS-UI.md`。本目录不改变 Feature 状态。

## 内容与取舍

- 保留 11 个稳定核心 `Editor`、项目背景/目标/网络拓扑 3 个必填和 11 个对应的 `PmsFileArtifact` 附件槽。附件槽不设置必填数量，不把普通 URL 当成受控文件。
- 增加传输现状与运维要求多选，选项逐项对应 Demo，不预选业务答案。
- 流量的新建/并发/吞吐保持文本输入，可连同单位填写；Demo 没有冻结单位、阈值和精度，不擅自改成有范围限制的数值。
- 管理 IP、公网 IP 资源为独立文本区域，可填写多个地址或网段，不臆造单 IP 限制。
- 运行业务明细为可增删的卡片子表单，包含设备名称、序列号、承载业务名称、业务网段、重要等级、出入接口、客户侧业务负责人、备注八列。序列号用字符串保留前导零；重要等级保持自由填写，不臆造字典。窄屏逐列显示，宽屏两列，避免固定宽度表格溢出。
- 新增字段是核心项的结构化补充，不自动合并或复制到核心富文本，不改变 SCH-01 的显式字段映射。
- 保留正式契约要求的“日志需求”。Demo 的“下载/生成工程交底书”属于 PRE-05，不作为字段或表单事件；保存和完成继续由 SOL Owner 的既有命令处理。

| 新增字段键 | 值 |
| --- | --- |
| `TRANSMISSION_CURRENT_OPTIONS` | 字符串数组：IPv6、分片、MTU、Jumbo、隧道 |
| `TRAFFIC_NEW_CONNECTIONS` / `TRAFFIC_CONCURRENCY` / `TRAFFIC_THROUGHPUT` | 文本，不预设单位 |
| `BUSINESS_DEVICE_DETAILS` | 对象数组，子键见 JSON；增删以整个数组变化进入普通 PATCH |
| `IP_MANAGEMENT_RESOURCES` / `IP_PUBLIC_RESOURCES` | 文本 |
| `OPERATIONS_MANAGEMENT_OPTIONS` | 字符串数组：带内管理、带外管理、SNMP、UMC、第三平台、堡垒机 |

## 交给 p903 的消费边界

由 p903 按既有流程创建/配置草稿、检查兼容、发布、选择精确修订、冻结并注册/装载。本次没有执行这些动作，不覆盖已发布模板、既有项目绑定或完成版。不得把本文件直接覆盖到已冻结修订，也不得用前端测试通过代替服务端发布兼容检查。

既有 `decodeDynamicForm` 无损解析配置；`buildInstanceRuntime` 注入实例/附件上下文；`changedOrdinaryValues` 仅组装普通值变化，受控附件不进入普通 PATCH。既有 `RequirementAnalysisDynamicForm`、需求分析面板和宿主均未修改。管理员接入后仍须完成真实 Owner API、文件权限和任务入口验收。

## 定向验证

在前端根目录执行，默认使用本工作树已安装的依赖：

```powershell
node node_modules/vitest/vitest.mjs run --config src/views/pms/delivery-business/requirement-analysis/browser/config.mjs
node src/views/pms/delivery-business/requirement-analysis/browser/verify.mjs
```

无本地依赖时，可把 `NPDMS_UI_DEPENDENCIES` 设置为已有且版本匹配的前端根目录，并从该目录的 `node_modules/vitest/vitest.mjs` 运行测试。测试只读该依赖安装，不改其源码、lockfile 或依赖；Vite 缓存和截图写入本工作树被忽略的 `.run/delivery-demo/`。

浏览器脚本使用 Playwright Node 包；若它不在上述安装内，可将 `NPDMS_BROWSER_PACKAGES` 设置为含 `playwright` 的 `node_modules` 路径。本轮 Python 环境无 Playwright，故使用宿主已提供的 Node Playwright，不安装新依赖。默认启动独立、无头 Edge，可用 `NPDMS_BROWSER_CHANNEL` 指定已安装的浏览器通道。

脚本在随机本地端口启动仅用于本表单的 Vite 入口，结束时关闭自己的服务器和浏览器。浏览器拒绝非本源、业务 API 和非 GET 请求，无数据库、真实项目、认证或业务写入访问。

验证范围：共享 codec 往返、11 核心/3 必填/11 附件兼容、扩展选项和明细列、运行上下文不污染配置、普通 PATCH 排除伪造附件及未知字段、空数组/空文本/null/前导零保留；真实 FormCreate 的填写、校验失败/成功、明细增删、只读切换保值、普通输入键盘切换及 320/768/1024/1440 宽度。

`browser/` 是测试夹具，不是生产宿主。Vue、Element Plus、FormCreate、共享 codec 均真实运行；未改动的项目专用 `Editor` 与 `PmsFileArtifact` 使用明确标识的文本框/附件占位，未验证富文本编辑器、上传、服务端保存、权限、发布或任务完成。不得把截图当作完整业务验收。

已观察到 FormCreate 3.2.38 的 group 原生加减控件是无 tabindex/可访问名称的 div；鼠标增删可用，键盘增删尚未满足可访问性要求。测试会明确报告该限制，不修改通用组件来掩盖它。宿主接入验收须由 p903 核对这一实际限制及其处理边界，本内容单元不宣称全页面 WCAG 通过。

## 官方组件依据

规则使用 FormCreate 的既有 [多选配置](https://www.form-create.com/v3/element-ui/components/checkbox.html)、[group 子表单](https://www.form-create.com/v3/guide/group) 和 [v-model/表单参数](https://form-create.com/v3/guide/global-props)，没有新增渲染器或生产依赖。实际验证版本为 FormCreate 3.2.38、Vue 3.5.34、Element Plus 2.13.7。
