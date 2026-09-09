# S1 PRE-01 项目工期页面直接复用

本增量直接修改并复用 `ProjectDurationPanel.vue` 和 `ProjectDurationFormDrawer.vue`，不复制平行页面。对应 F-SOL-001/PRE-01；在S1中承接Demo工期要求录入与变更办理，不把工期写进工勘表单，也不恢复旧倒排页写入口或实现S2施工计划。

## 消费接口与边界

原有 `project: ProjectMasterVO` 必填输入保持不变，两个组件增加可选 `readonly?: boolean`：

- 不传readonly时保留原首次生效、变更草稿、提交审批、撤回、文件证据及历史查看入口，继续由原API和权限指令处理授权与业务状态。
- readonly=true时不显示新增/编辑/审批/撤回入口，编辑抽屉和文件控件禁写；处理函数在异步校验/确认后重验，不仅隐藏按钮。只读仍可查当前工期与历史。
- 项目ID缺失、0、负数、NaN或不安全整数时，不发查询，也不提供首次录入。查询失败显示错误，不误报成“尚未录入”。
- 项目切换时旧当前值/草稿读取结果不会覆盖新项目；迟到的保存/文件回执不回填新上下文。历史查看组件按项目重新挂载，但其原文件及查询逻辑未修改。
- 只读切换保留当前抽屉输入；强制项目切换关闭旧抽屉并清理旧引用。宿主应先处理未保存提示，再变更项目，不以强制变更props代替用户明确放弃。

面板发出 `dirty-change`、`changed` 并暴露 `isDirty()`、`discardChanges()`；抽屉保留原 `openInitial/openCreate/openEdit`，增加同一dirty协议，并保留 `saved` 事件。正在保存/提交/撤回时拒绝明确放弃。`changed/saved`只用于刷新Owner事实，不等于项目任务或阶段完成。

实际受控注册、项目上下文解析、模板发布/选择/冻结与宿主装载仍归p903；本增量不新增注册键、生产路由或通用包装组件。服务端项目权限、租户、参与人、CAS/幂等、日期计算、BPM和受控文件契约不变。

## 验证

前端根目录的定向命令：

```powershell
node node_modules/vitest/vitest.mjs run --config src/views/pms/delivery-business/duration/browser/config.mjs
node src/views/pms/delivery-business/duration/browser/verify.mjs
```

沿用此前内容验证的 `NPDMS_UI_DEPENDENCIES`、`NPDMS_BROWSER_PACKAGES` 和可选 `NPDMS_BROWSER_CHANNEL`，只读复用现有安装；不修改别的工作树源码或依赖。输出在本工作树被忽略的 `.run/delivery-demo/duration-browser/`。浏览器和本地测试服务器在finally关闭。

24项检查通过：原8项路由/并发参数/当前草稿/历史/文件/BPM/旧入口保护检查保留，只有因安全读取改为局部current而变化的一处源码字符串断言随实现更新；新增16项真实组件方法测试覆盖默认项目输入、非法ID、两层旧响应、失败状态、只读写入与确认后重验、原首次工期载荷、校验期间切换、草稿保留、文件回执隔离等。

浏览器使用真实原工期面板和编辑抽屉、Element Plus与VueUse响应式查询；验证原日期表单填写及提交载荷、只读操作隐藏、历史入口保留、项目切换、非法项目拒绝及320/768/1024/1440无页面溢出。Owner API、BPM、权限指令、文件组件、历史查看弹窗和消息/用户身份为明确测试夹具；只允许本源静态GET资源，不访问生产/数据库。未验证真实鉴权、BPM审批、文件上传、历史弹窗内部或业务生效，未运行全应用构建/类型检查，不据此晋级Feature或整条S1链路完成。

## S1顺序与已识别缺口

S1联系人先核对后确认不能直接使用旧客户联系人模型：当前旧DO没有projectId，主联系人约束按客户；CUS-04要求项目独立记录、项目主联系人唯一与历史。该缺口已写入全流程适配输入，未用工期增量替代，也未擅自新建联系人业务API/表。

当前工期复用适配已交付；后续仍按S1工勘、物料、需求分析/交底书顺序推进，再进入S2～S6。此前S5成果保留，不把它当作S1完成依据。
