# DU-20260909-DELIVERY-DEMO-BUSINESS-UI Demo领域页面与表单内容

> DU状态：`CLAIMED`
> DU类型：`TASK`
> Feature协调：`F-SOL-003=TASK_COORDINATED`
> Task范围：`Demo V2需求分析表单内容增量：PRE-04兼容JSON及定向测试；不包含生产宿主、注册、发布或任务接入`
> Owner：`实现动态表单和业务操作界面（01a0822c-ea82-7801-b318-39bfd843f866）`
> 分支：`DETACHED`
> Worktree：`M:/AICoding/CodexData/worktrees/bfbf/NPDMS`
> 认领基线：`3d6d6686b703638041a1007bdc7372206c9501c3`
> 认领提交：`SELF`
> 修改边界：`yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/requirement-analysis/demo-template.json;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/requirement-analysis/demoTemplate.spec.ts;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/requirement-analysis/README.md;yudao-ui/yudao-ui-admin-vue3/src/views/pms/delivery-business/requirement-analysis/browser/**;tasks/delivery-units/DU-20260909-DELIVERY-DEMO-BUSINESS-UI.md`
> 串行资源：`无数据库访问、无SQL或迁移号；浏览器目录仅本模板测试入口及脚本，不增加生产路由；保留bfbf在途PM01文件`
> 旧功能范围：`NONE`
> 验证：`JSON与既有共享codec、PRE-04兼容规则的定向测试；真实FormCreate浏览器渲染/取值；工具不可用如实记录，不冒充业务保存或宿主接入验收`
> 集成记录：`NONE`

## 协调入口与依据

- 协调者：开始项目交付主线任务 (2)，任务`01a08130-1cc3-7713-bb2b-20ea95140b1a`；协调Worktree为`M:/AICoding/CodexData/worktrees/2916/NPDMS`，只在master登记认领，不接收实施工作树在途文件、不推送。
- 来源Task：bfbf任务中的“Demo V2需求分析表单内容增量”，本单元进展集中记本DU；关联现有`tasks/features/F-SOL-003.md`，不修改其既有Feature状态或历史Done。
- Requirement：`PRE-04`；正式契约为`specs/features/F-SOL-003-requirement-analysis-versioning.md`及其物理合同；F-PLT-002仅作为既有共享表单能力依赖，不认领其实现职责。
- 授权依据：用户已确认ZCode继续负责宿主/模板/通用注册接入，bfbf负责Demo对应领域页面与表单内容；来源任务于2026-09-09集中补交本单元精确清单并要求一次登记。来源工作树当时为detached `8912d6f8`；此认领不授权切换其工作树/分支、提交或接收PM01在途内容，也不要求来源分支包含认领提交。
- Demo：`E:/AICoding/Projects/NPDMS/需求/项目交付/项目交付页面数据DemoV2.html`，只作界面与示例数据参考，不覆盖正式业务、权限、状态和历史契约。

## 本次交付与排除项

唯一生产内容产物为新增JSON对象`{formConfJson, formRulesJson}`，保持与现有`DynamicFormRevisionVO`、`decodeDynamicForm`兼容；保留11个核心Editor、3个核心必填和11个受控附件槽。Demo传输多选、流量、IP及运维多选仅按既有契约允许的扩展添加，不嵌入业务状态命令、API地址或密码。

测试和README只服务本JSON；browser目录可提供独立真实渲染/取值验收入口及脚本，不成为生产页面宿主或替代领域实现。不得修改SDS、领域Service/API/表、SQL、模板注册、既有组件、已发布修订或项目冻结引用；不调用生产写API、不擅自发布模板，不把页面保存改成任务或节点完成。

后续其他Demo页面未在本次认领内；明确业务义务与精确文件后可在本DU扩展，由协调者提交新边界后生效，不提前占用整个delivery-business目录。

## 依赖与消费交接

- F-PLT-002 → 既有表单引擎、共享codec及受控文件组件 → `specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md`、对应Feature Task和源码。仅消费，不复制引擎或Provider；来源分支缺代码时先明确可用增量及同步授权，不从p903在途文件复制。
- F-SOL-003 → PRE-04兼容规则及既有`PATCH /preparations/{id}/form` → 现有Feature Spec/Task。原应用链为PLT模板草稿PATCH、由原Owner配置发布/选择、SOL表单PATCH；本单元只交付兼容内容与测试，不执行该生产写链。
- `DU-20260908-TEMPLATE-BUSINESS-VIEW` → 原宿主、受控注册、模板发布/选择/项目冻结和任务接入 → Owner/位置/进展直接读取master该DU及p903来源Task。本次不新增宿主props、事件或注册键，不改变或转交其现有职责与文件，因此不以尚未取得ZCode接入回执阻断本独立内容单元；实际配置、挂载和联调仍由原Owner承接，涉及契约或边界改变时须取得其真实确认。

## 交接

- 最后提交：`NONE`
- 已完成：仅职责、依赖及文件边界认领；实现和测试尚未执行。
- 剩余：新增JSON、定向兼容/渲染测试与结果说明；真实宿主接入不在本单元。
- 完成层级：本内容单元完成不等于整个Demo、F-SOL-003新增量或完整用户办理闭环完成；未挂载不得宣称用户已能从任务入口办理。
