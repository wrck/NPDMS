# 默认业务完整链路复核（2026-10-08）

## 判断标准与结论

基于用户在本机验收会话的两次补充（01a11c88-0567-76ce-b5c8-5cb26d9e4c5b、01a11c91-e4a1-7477-b992-6b6920be5590）：命名模板是字段配置的命名配置；默认实现统一提供 CRUD、扩展值、业务完成规则计算、统一交付件与关联完成。展示可读取列表/表单/关联子表 Tab 配置，也可采用专用组件。业务只重载差异，不再编写重复公共流程。

**完整框架验收仍为未完成。** 工勘真实浏览器和默认保存失败保护的通过，只证明已测路径；不能代替以下缺口的闭合。代码检查以 be854960 为基线，工作树无未提交生产改动。

## 已存在并有验证的公共能力

- `ProjectBusinessController` / `DefaultProjectBusinessService`：继承 CRUD、权限、项目范围、事务、幂等与并发；创建支持主体、表单绑定、扩展值同一事务。
- `storeFormWrite`：普通实体和版本化草稿复用同一绑定与扩展值保存。7baa48c 已修正“已绑定但未有扩展值记录”的首写定义查找。
- `DefaultVersionedProjectBusinessService`：可复用草稿、冻结、有效版本、复制与比较；现有需求分析使用旧多行修订实体，不能把它误认为默认版本类的完整演示。
- `ProjectBusinessDeliveries` / `DefaultBusinessDeliveryApi`：公共上传、四键定位、归集和完成事实。已实测确认后错误类型不完成、指定类型上传自动 DONE。
- `ProjectBusinessPage`：统一保存、操作回执、离开保护、可替换表单组件和列表 slot。220f871 已修正列表操作被异步表单加载吞掉的问题。

## 尚未达到目标的接口边界

### 1. 命名字段配置没有在默认入口闭合

`BusinessFieldConfigurationApi.Field` 仅包含 code、label、displayOrder、listVisible、searchable、sortable；`Configuration` 仅有 version、fields。`ProjectBusinessFieldConfiguration.vue` 对应的也是单份列表/查询配置，并不提供命名方案、表单布局或关联子表 Tab。

命名表单能力已经在 `EntityPresentationApi` / `EntityPresentationService` 和已发布动态表单中存在；需求分析的 `RequirementAnalysisPresentations` 单独读取它。默认 Controller 尚无统一的命名配置选择/读取入口，默认 `defaultForm(projectId)` 仍返回空布局。工勘目前通过覆盖 `defaultForm` 取原默认模板。

收敛方向：复用已有命名表单、字段配置、绑定、扩展端口，在默认业务入口统一提供读取和选择；不再建一套模板存储，也不把整段读取/绑定流程留给每个业务。

### 2. 专用表单还承担公共输入拼装

`SurveyFullCaptureForm.vue` 目前除业务采集展示外，还自行读取默认布局、比较绑定、计算扩展值差异、拼装 `$binding` / `$extensions`。其中命名配置选择、差异计算和保存载荷属于可复用能力，仍需上收；工期领域命令和原业务组件保留为可重载差异。

普通更新 `PUT /{id}` 仍通过 `service.input(values)` / typed update；有扩展时前端改走 `save-form`。虽然两条都是继承接口，公共输入处理仍分叉，不能让新增业务页面自己识别并重写保存路由。收敛时必须保留原幂等操作名、并发、afterUpdate/afterChange 以及子表保存语义，不能直接换接口导致回执无法恢复。

### 3. 关联子表 Tab 未在当前默认配置中提供

默认字段类型可以承载 OBJECT_LIST，但普通控件显示 JSON 数组；默认页面没有关联子表 Tab 配置及标准读写契约。工勘子表由 `SiteSurveyBusinessService` 通过原 `details.load/save` 承接。应区分可复用的关联展示/查询/保存与确有业务规则的子表操作，不把 JSON 文本框算作子表 Tab 已完成。

### 4. 完成判断尚依赖业务提供事实钩子

默认 `runtimeFacts` 仅提供 BUSINESS_RECORD_SAVED，`runtimeHandlingCompleted` 默认为 true；工勘、需求分析覆盖专有事实和完成判断。`DirectBusinessRuntimeService` 统一读取这些事实和交付件事实，但默认服务尚未体现“传入业务实体及条件，统一规则计算完成”的完整入口。

应复用现有规则执行能力，明确规则只读事实与写操作边界；业务实体、扩展值及交付件查询保持授权和版本一致。不能用“已保存”替代“已确认”等规则，也不能通过直接写任务状态完成验收。

### 5. 专用界面及验收范围

工勘完整模板主要保存链已实测；结构化地点仍在独立业务区域，不能宣称所有界面位置完全复原。需求分析仍使用通用 `ProjectBusinessPage` 包装，原工作区版本切换、正文/附件展示及操作栏尚未整体验收。

## 下一步顺序

1. 收口本轮真实验收发现的公共错误诊断，保留所有成功与失败证据。
2. 先完成默认命名配置与表单/扩展输入链，使用一个无特殊业务规则的新实体验证无需重复公共代码；再让工勘、需求分析复用该链，保留专用展示组件。
3. 按现有能力与业务契约补关联子表展示及统一条件完成入口；避免先复制一个特例作为所有业务标准。
4. 分别对默认薄实体、工勘、需求分析做真实 API/浏览器验收，再判断完整框架 GO/NO-GO。

### 首个收敛改动（待本机验证）

默认 `PUT /{id}` 现在接收固定字段及可选 `$binding` / `$extensions` / `$business`，复用同一更新事务、项目范围、锁、幂等 `save` 回执和 afterUpdate。前端统一保存不再根据是否有扩展值切换到领域 `save-form`；旧接口保留兼容。新测试覆盖普通 CRUD 同时保存主体/子表/扩展、回执重放、失败整体回滚、领域工期命令与只读限制。此项只收敛保存入口，不代表命名配置、子表 Tab 和规则条件入口已经完成。

该保存入口候选前端 25 项相关测试、vue-tsc 与 Vite 构建（40.33 秒）通过；新增后端及真实 PUT 保存尚待本机执行。原 save-form 回执兼容保持，不冒充已完成迁移验收。

### 2026-10-08 17:56 未完成代码保全

用户要求提交并推送全部待提交代码，以便交接。另有一组命名配置 WIP 一并保存：EntityPresentationApi.listForType、默认 /form-options、BusinessFormOption、工勘原字段映射覆盖、相应测试及前端 ProjectBusinessForm 候选。该组件尚未接入 ProjectBusinessPage / SurveyFullCaptureForm；选择状态、未保存输入保留、首次绑定/扩展定义切换及只读加载还未验证，不能据代码存在宣称全链路完成。此次 WIP 未运行后端编译、前端类型检查或浏览器验收；33e22ed4 的 86 项通过不能覆盖此后改动。后续先按交接职责/钩子清单复核，再继续实施。
