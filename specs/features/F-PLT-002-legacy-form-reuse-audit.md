# F-PLT-002 旧动态表单与需求分析实现复用审计

> 审计状态：`COMPLETE`
> 审计对象仓库：`NPDMS`
> 产品代码提交：`3adea6121000b5bb55b176d352b5afa94143b7dd`
> 当前审计工作树提交：`6a57b616469e8cf18efdc244a78329bf6f51af6d`（相对产品代码提交仅增加根级实施约束并取消旧计划，未修改下列被审计产品代码）
> 关联Feature：`F-PLT-002`
> 需求：`SOL-01`；支撑`PRE-04/PM-03/PM-11`

## 1. 审计结论与决策语义

设计前审计已经完成，不再作为Implementation阶段的待办。实施必须逐项遵守本文件的映射：

- `DIRECT_REUSE`：从原公共路径直接导入并保持原文件不变，不复制第三方引擎或公共工具；
- `COPY_THEN_ENHANCE`：只复制表内明确写出的交互或数据意图到新的PLT类、组件或页面，再按F-PLT-002契约增强；原文件、路由、API、表和行为保持不变；
- `DO_NOT_REUSE`：不把该实现、状态语义、数据或写入口带入F-PLT-002。

F-PLT-002只实现共享模板和手工实例基础闭环。旧需求分析的11项业务内容和项目入口已经完成复用判定，但实际接入仍属于F-SOL-003后续前向修订；本Feature不得借审计之名提前实现PRE-04或WorkBinding。

## 2. BPM FormCreate编辑器与公共组件

| ID | source / 已审计项 | decision | 新目标或职责 | rationale | legacy unchanged verification |
|---|---|---|---|---|---|
| BPM-01 | `yudao-ui/yudao-ui-admin-vue3/src/views/bpm/form/editor/index.vue` 的`<fc-designer>`；依赖`@form-create/designer@3.4.0` | `DIRECT_REUSE` | 新PLT模板编辑页直接使用同一`fc-designer`，不复制设计器引擎 | 现有设计器已覆盖布局、控件、配置、联动、事件、校验、录入和多端预览 | 新页面只import公共包；BPM编辑页、路由和API不进入实现diff，并跑BPM表单打开/编辑回归 |
| BPM-02 | `components/FormCreate/src/useFormCreateDesigner.ts`：替换上传/富文本菜单、注册字典/用户/部门/区域/API/iframe等增强控件并修复复制后的重复field | `DIRECT_REUSE` | 新PLT编辑页调用原hook；`PmsFileArtifact`由新PLT页面组合层额外注册，不修改原hook | 公共hook已经是跨页面稳定增强点；直接复用可保持既有控件能力且避免分叉 | 原hook文本不改；组件运行时测试证明PLT页调用原hook且BPM页控件清单不减少 |
| BPM-03 | `utils/formCreate.ts` 的`encodeConf/decodeConf/encodeFields/decodeFields/setConfAndFields/setConfAndFields2` | `DIRECT_REUSE` | PLT草稿保存使用encode；明确修订重开、预览和实例渲染使用decode/set工具 | 已统一处理FormCreate JSON序列化与designer/renderer恢复 | 原工具文件不改；运行时回归覆盖保存后重开、预览和实例刷新值一致 |
| BPM-04 | `plugins/formCreate/index.ts` 的Element Plus自动导入、`formCreate`/`FcDesigner`安装及Upload/Editor/Dict/User/Dept/API/Iframe/Area全局注册 | `DIRECT_REUSE` | 新PLT页面依赖现有`setupFormCreate`装配；新受控文件控件仅在PLT动态表单组合层接入 | 全局运行时装配已成熟，重复注册会造成组件名和运行时行为漂移 | 插件文件与全局控件不改；BPM与PLT真实页面同时打开且无组件解析错误 |
| BPM-05 | BPM编辑页的`designerConfig`：自动激活、label宽度、完整右侧配置、事件、校验、录入和设备预览 | `COPY_THEN_ENHANCE` | 复制到新`DynamicFormTemplateEditor`页面的本地配置，保留全部开关开启并增加PLT高信任提示 | 配置对象属于BPM页面状态，不应让PLT编辑器依赖BPM页面；交互基线可复用 | BPM配置不改；PLT组件测试断言未隐藏既有菜单/配置面板，真实浏览器核对完整设计面 |
| BPM-06 | BPM编辑页“保存→元数据弹窗→encode conf/fields→create/update→关闭标签页”交互 | `COPY_THEN_ENHANCE` | 新PLT编辑页复制“明确保存、校验、序列化、成功后返回列表”体验；改为只PATCH明确DRAFT revision并携带`If-Match`，元数据另走模板PATCH | 保存体验成熟，但BPM的create/update聚合和路由目标不符合模板身份/修订分离契约 | BPM保存逻辑不改；PLT浏览器回归覆盖保存、响应未知重读、版本冲突不覆盖 |
| BPM-07 | BPM编辑页加载详情后`setConfAndFields`恢复设计器的交互 | `COPY_THEN_ENHANCE` | 新PLT编辑页按URL中的明确`revisionId`读取并恢复DRAFT/PUBLISHED；PUBLISHED只读预览 | 恢复体验可复制，但不得以模板当前指针替代请求修订 | BPM加载逻辑不改；PLT测试保存后重开同revision并比较完整config/rules |
| BPM-08 | BPM `type=copy`：读取原表单、去掉id、名称追加`_copy`后另建表单 | `COPY_THEN_ENHANCE` | 新PLT“创建下一草稿”先由服务端锁定并复制当前PUBLISHED revision，再打开返回的明确DRAFT；只复用“复制后继续编辑”的体验 | 客户端删id复制会绕过唯一草稿、不可变发布版、CAS和审计，不能原样复用 | BPM复制路由不改；PLT应用测试证明一次命令产生唯一下一草稿，同键重放不重复 |
| BPM-09 | BPM表、Form API、BPM状态和流程绑定语义 | `DO_NOT_REUSE` | 无F-PLT-002目标；PLT使用新表和新REST | BPM表单属于流程定义，不是共享业务动态表单真值 | 实施diff不得修改BPM后端/数据库/API/路由；BPM表单列表、编辑和复制仍按原权限可用 |

## 3. 旧`pms_eng_form_template` / `pms_eng_form_instance`

| ID | source / 已审计项 | decision | 新目标或职责 | rationale | legacy unchanged verification |
|---|---|---|---|---|---|
| PMS-01 | `FormTemplateDO.productType`及`published-list?productType=` | `DO_NOT_REUSE` | F-PLT-002只提供通用`categoryCode`和无业务条件的手工选择；WorkBinding适配后续另锁契约 | `productType`是旧工程域联动条件，直接搬入PLT会把单一消费者规则固化为共享真值 | 旧字段和过滤接口保留；新表无`product_type`，本Feature不生成WorkBinding规则 |
| PMS-02 | `FormTemplateDO.conf/fields`与VO中的FormCreate JSON | `COPY_THEN_ENHANCE` | 拆入`DynamicFormTemplateRevision.form_conf_json/form_rules_json`，由同一encode/decode工具保存和恢复 | 采用FormCreate配置/规则载荷的意图正确；需进入独立不可变revision而非可变模板行 | 旧列、VO和API不改；新命令只写`plt_dynamic_form_template_revision` |
| PMS-03 | 模板/实例`@Version version`乐观锁 | `COPY_THEN_ENHANCE` | 新PLT分别使用template metadata version、DRAFT revision version和instance version，并通过`If-Match`执行CAS | 并发覆盖防护可复用，但一个旧version不能表达根、修订和实例三种并发事实 | 旧version字段和更新方式不改；新MySQL并发回归分别验证三类CAS |
| PMS-04 | `FormInstanceDO.templateSnapshot`及创建时冻结模板的意图 | `COPY_THEN_ENHANCE` | `DynamicFormInstance`冻结`template_id/template_revision_id/revision_no/engine versions`并始终读取不可变revision | “创建时冻结”正确；旧字符串拼接JSON、允许请求传snapshot且不校验发布指针不可复用 | 旧snapshot生成逻辑不改；新实例测试证明模板发布新revision后仍渲染原revision |
| PMS-05 | `formData`保存、刷新恢复与版本校验 | `COPY_THEN_ENHANCE` | `DynamicFormInstance.value_json`保存普通JSON对象；PATCH只更新出现的非文件字段并保留`null/false/0/空字符串/空数组`语义 | 动态值持久化意图可复用；旧整段字符串更新并顺带推进FILLED状态不符合通用载体 | 旧保存接口不改；新运行时和MySQL测试覆盖部分PATCH、假值、刷新和CAS冲突 |
| PMS-06 | 旧模板页的分页筛选、状态标签、列表动作、元数据编辑和确认反馈 | `COPY_THEN_ENHANCE` | 复制到新的PLT模板列表/元数据弹窗；状态改为revision与availability双轴，按钮只渲染服务端`allowedActions` | Element Plus列表交互成熟，但旧单状态轴和前端自行判断不可沿用 | 旧页面不改；新页面运行时测试与浏览器证据核对双轴状态和动作投影 |
| PMS-07 | 旧模板页用textarea直接编辑`conf/fields`并用`pre`充当详情/预览 | `DO_NOT_REUSE` | 新模板编辑/预览必须使用完整`fc-designer`和FormCreate renderer | 原始JSON编辑不满足“可配置动态表单”用户目标，也无法形成所见即所得闭环 | 旧原始JSON页面保留；新PLT页面不得出现以textarea或pre替代设计器/预览的实现 |
| PMS-08 | 旧实例页的模板下拉、实例分页、编辑/明细弹窗和刷新列表流程 | `COPY_THEN_ENHANCE` | 复制手工选模板与列表/详情的交互骨架到新PLT实例页面；下拉只返回ENABLED的当前PUBLISHED revision并冻结选择 | 选择和管理体验可复用；项目、填报人和旧状态列不属于共享手工实例 | 旧实例页不改；新浏览器闭环验证选择后停用/指针变化失败且不静默换版 |
| PMS-09 | 旧实例页用textarea编辑`formData`、用`pre`显示快照和值 | `DO_NOT_REUSE` | 新实例用FormCreate renderer动态填写/查看，并单独渲染受控`PmsFileArtifact` | 原始JSON输入无法执行schema校验、控件交互或受控文件事实 | 旧页面保留；新PLT真实浏览器必须通过控件填写、保存和刷新，不接受JSON文本替代 |
| PMS-10 | 旧模板`DRAFT→PUBLISHED↔DISABLED`单字段状态机及草稿删除 | `DO_NOT_REUSE` | 新PLT把revision `DRAFT→PUBLISHED`与template `ENABLED/DISABLED`分离，PUBLISHED不可变且不提供模板删除 | 旧状态把内容生命周期和可选性混在一行，无法保证历史revision与当前指针 | 旧状态机和删除仍在旧模块；新状态机只按F-PLT-002物理契约实现 |
| PMS-11 | 旧实例`PENDING/FILLED/SUBMITTED/AUDITED/REJECTED`、submit/approve/delete及审核人意见 | `DO_NOT_REUSE` | F-PLT-002实例无业务状态机、提交、审批、完成或删除；消费Context自行拥有这些语义 | 把工程域审批搬进PLT会使共享表单错误拥有业务完成事实 | 旧Controller/Service/API/按钮保持；新REST不得出现submit/approve/delete |
| PMS-12 | 旧Controller/Service/Mapper/DO、`pms_eng_form_*`数据与菜单权限 | `DO_NOT_REUSE` | 新实现全部位于`pms-module-platform`新命名空间、新REST和`plt_dynamic_form_*`表 | 旧实现含工程域权限、旧全局编号和不完整项目校验，不能成为新PLT真值 | 实施diff不得修改旧类、页面、API、表、数据、菜单或角色关系；无迁移、无双写 |

## 4. 旧需求分析页面、字段与项目入口

| ID | source / 已审计项 | decision | 新目标或职责 | rationale | legacy unchanged verification |
|---|---|---|---|---|---|
| REQ-01 | `views/pms/engineering/requirement/index.vue`的11项内容标签及顺序：需求背景、拓扑描述、传输需求、流量特征、业务描述、IP规划、冗余设计、防护要求、运维要求、日志留存、接口内容 | `COPY_THEN_ENHANCE` | PLT负责让冻结`formRulesJson`无损承载这些标签、顺序和字段类型；F-SOL-003重规划时把该映射复制为PRE-04业务模板配置，F-PLT-002本身不预置PRE-04业务内容 | 这些标签是旧需求分析中已验证的业务表达，应保留；但标签所有权仍在SOL，不能硬编码进PLT共享模型 | 旧页面和字段列不改；F-PLT-002验收只验证通用schema承载能力，后续F-SOL-003契约测试逐项断言11项映射 |
| REQ-02 | `需求背景`、`接口内容`使用公共`Editor v-model`的富文本交互 | `COPY_THEN_ENHANCE` | 新PLT设计器直接复用已全局注册的Editor规则，预览/实例沿FormCreate renderer双向绑定；以后PRE-04配置上述字段为富文本 | 富文本编辑体验可复用；不复制旧页面的固定字段绑定 | 旧Editor用法不改；PLT运行时测试覆盖设计、序列化、重开、填写和刷新富文本 |
| REQ-03 | 旧需求列表、项目选择、两列Element Plus表单与保存后刷新 | `COPY_THEN_ENHANCE` | PLT模板/实例页面复用列表、选择、对话反馈和响应式重排意图，改为动态schema、独立权限及`allowedActions` | 页面节奏成熟，但固定VO、写API和前端状态判断不可沿用 | 旧页面不改；新页面在320/768/1024/1440验证列表和动态表单不溢出 |
| REQ-04A | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-detail/index.vue`的需求分析模板选择卡片、模板快照意图和动态章节表单 | `COPY_THEN_ENHANCE` | 新PLT手工实例页复制“选择→预览/填写”的交互，改为服务端锁定明确ENABLED/PUBLISHED revision并用FormCreate渲染；不复制本地快照兜底、固定字段分支或`sectionData+flatData`双写 | 这是旧实现已经具备且可用的模板选择体验；其手写渲染与业务双写不适合作为共享基础 | 旧project-detail原样保留；新浏览器闭环验证精确revision冻结，旧入口回归不变 |
| REQ-04 | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-detail/index.vue`工程实施导轨中的“需求分析”入口和项目上下文 | `COPY_THEN_ENHANCE` | F-PLT-002只提供独立模板/手工实例入口；F-SOL-003重规划后在新的需求分析工作区复制项目入口体验，并由WorkBinding自动确定模板，项目用户不得手工选模 | 项目内入口和上下文值得保留，但属于SOL消费方；当前基础Feature提前接入会再次形成碎片能力和反向依赖 | 本Feature不修改旧project-detail；后续接入必须使用PLT公开窄API，不依赖PLT Service/Mapper/表 |
| REQ-05 | 旧需求create/update/delete/submit/mark-effective/archive、`DRAFT→SUBMITTED→EFFECTIVE→ARCHIVED`及旧权限码 | `DO_NOT_REUSE` | 无PLT目标；旧链保持原样，F-SOL-003未来只按重新锁定的版本化状态实现 | 旧CRUD/状态是业务流程，不是通用动态表单保存语义 | 旧Controller/API/页面按钮/菜单权限保持；F-PLT-002不修改、退役或重授权 |
| REQ-06 | `pms_eng_requirement`数据、固定DO/VO和旧REST | `DO_NOT_REUSE` | 不迁移、不双写、不作为`plt_dynamic_form_*`真值 | 旧数据仍服务旧功能；共享基础能力必须从新命令形成独立事实 | 新迁移只建`plt_dynamic_form_*`及新权限资源；旧表行数和旧API响应不因PLT命令改变 |

## 5. 实施绑定与验收

F-PLT-002 Technical Plan和实施只能在Feature Ready GO后生成，并须把上述ID作为任务输入。最小绑定如下：

1. 新PLT前端仅新建动态表单模板列表、编辑/预览、手工实例列表/选择/填写页面；BPM、旧PMS表单和旧需求分析页面不改。
2. 新PLT后端仅新建`DynamicFormTemplate`、`DynamicFormTemplateRevision`、`DynamicFormInstance`的Controller/Service/DO/Mapper及物理契约已列出的REST；不得复制旧包名或依赖engineering实现。
3. `DIRECT_REUSE`项通过原路径import；`COPY_THEN_ENHANCE`项必须能在代码复审中从本表ID追到新目标；`DO_NOT_REUSE`项不得出现在新状态机、接口或数据迁移中。
4. Implementation Done候选必须提供：三组旧路径相对实施基线的零修改证据；BPM表单、旧PMS模板/实例、旧需求分析及旧项目入口的原权限浏览器/API回归；新PLT整体浏览器闭环；对应机器契约测试。

结论：三类旧来源已全部完成`DIRECT_REUSE / COPY_THEN_ENHANCE / DO_NOT_REUSE`判定，无`PENDING/TODO/待确认`项。该审计锁定实现输入，但不代表Feature Ready、Technical Plan或Implementation通过。
