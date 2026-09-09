# ACC-02 满意度页面直接复用适配

本增量直接复用并加性修改 `project/satisfaction/index.vue`、`TaskPanel.vue`、`ResultPanel.vue`，没有复制平行页面、重建问卷引擎或新增生产包装组件。Requirement为ACC-02，依据现有F-ACC-002契约；认领及实际验证记在 `tasks/delivery-units/DU-20260909-DELIVERY-DEMO-BUSINESS-UI.md`。

## 给页面消费者的接口

三个既有组件均支持可选 `projectId?: number`、`readonly?: boolean`：

- 不传projectId（或undefined）仍是原独立管理方式，按用户现有授权范围查询，保留手工项目筛选；不传readonly仍可办理原有操作。
- 传入正数安全整数projectId时，直接按该项目查询且锁定项目选择，不受组件内手工筛选值影响。0、负数、null、NaN及不安全整数不发查询，不退化为全范围查询。
- 上层只有取得已授权的有效项目ID后才可装载；未解析出的上下文不能以undefined作为“指定项目”传入，因为undefined明确表示独立管理模式。这与既有业务视图宿主先校验上下文再装载的约定一致。
- readonly=true时禁止指派、受控链接创建、现场协助提交、整改重收与结果失效；处理函数同样校验，不仅隐藏按钮。文件下载和结果导出是已有受权查询能力，仍须由服务端独立鉴权，未因readonly获得额外权限。
- 指定项目或readonly时不显示问卷模板管理页签；默认独立管理入口保持原三个页签。

消费示例（p903负责实际受控注册和宿主映射，本目录不进行注册）：

```vue
<SatisfactionWorkbench
  :project-id="authorizedProjectId"
  :readonly="readonly"
  @dirty-change="onDirtyChange"
  @changed="refreshBusinessSummary"
/>
```

原组件路径为 `@/views/pms/project/satisfaction/index.vue`。`changed`只提示Owner命令已返回成功、需刷新摘要，不是ProjectTask完成事实。readonly是额外展示约束，不替代现有服务端权限、租户、项目范围、责任人、版本及文件授权。

组件提供 `isDirty()`、`discardChanges()` 并发出 `dirty-change`。宿主切换目标前继续按其既有协议提示未结束操作，明确放弃后才切换；现场协助上传进行中时discardChanges返回false。强制变更projectId会清空旧列表、选择和对话框，防止继续操作旧项目；只读变更保留现场协助表单输入并禁用写入，一次性问卷链接关闭。

## 复用与修改边界

继续调用原有满意度列表、指派、grant、现场协助预留/文件上传/提交、整改、结果失效、文件下载和导出API，未修改Controller、Service、评分、签字、Schema或历史事实。现成客户问卷填写页与问卷模板页未修改；现场协助中的原答卷JSON输入也没有在本次上下文适配中重做。

仅补充上下文/只读props、旧异步响应隔离、切换时停止旧导出轮询、加载失败提示及窄屏弹窗宽度。已经发出的有效请求不会被假称撤销；上下文变化后不再发起后续上传/提交/下载票据请求，也不把旧结果回填到新项目。

独立管理页和项目嵌入复用同一套实现。p903仍持有模板发布/选择/冻结、注册和宿主；本次没有新增注册键、路由、迁移或种子，也没有替它完成真实任务入口接入。

## 验证入口

从前端根目录运行：

```powershell
node node_modules/vitest/vitest.mjs run --config src/views/pms/delivery-business/satisfaction/browser/config.mjs
node src/views/pms/delivery-business/satisfaction/browser/verify.mjs
```

当前工作树无完整依赖安装时，沿用PRE-04测试配置的 `NPDMS_UI_DEPENDENCIES`（已有前端根目录）和 `NPDMS_BROWSER_PACKAGES`（含Playwright的node_modules路径），只读复用安装，不修改另一个工作树的源码或依赖。默认无头Edge，随机本地端口；脚本finally关闭自己的浏览器和服务器。证据输出在本工作树被忽略的 `.run/delivery-demo/satisfaction-browser/`。

测试加载真实Vue组件与现有方法；单元测试使用项目已有轻量渲染器，浏览器使用真实Element Plus。满意度API、文件API、消息提示、租户取值及二维码为明确测试夹具，拒绝未列入场景的业务动作；浏览器只允许本源静态GET资源，不访问生产、数据库或认证服务。ContentWrap仅作测试容器。

覆盖：独立与项目入口、无效ID拒绝、查询失败、旧响应隔离、只读写命令拒绝及确认后重验、晚到grant/下载/导出结果、上传预留期间上下文变更、未结束操作提示、明确放弃、mock指派及320/768/1024/1440显示。测试通过不等于真实服务端授权、上传/评分/归档、生产导出或宿主联调通过；不晋级F-ACC-002 Done。

接口采用Vue的[单向props](https://vuejs.org/guide/components/props.html#one-way-data-flow)，根据[响应式侦听](https://vuejs.org/guide/essentials/watchers.html)重新查询并隔离旧响应。实际复用Vue3.5.34/ElementPlus2.13.7，未升级依赖。
