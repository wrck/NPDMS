# domain-migration 提交接入与覆盖追踪

> 后续全面界面/功能核查见 [ui-function-review.md](ui-function-review.md)。此前报告未覆盖全部界面取舍，不应单独作为恢复依据。


比较基线：源 `604dc3a4361b0fdda026a43518ad2c0e78282dea`；目标 `c674a8a8793cd061056b5dbe85ba7afd6dd1ac9a`。本轮只读追踪，不修改业务代码、不部署。

## 本轮取舍口径与补查结果

按用户最新明确要求：同步集成优先当前工作树；项目交付、割接、巡检优先 `codex/domain-migration`；已有明确裁决和调整建议在其适用范围内优先。分支优先级不表示整文件覆盖，也不撤销授权、历史保护或失败校验。以下口径优先于报告早先仅按文件差异作出的恢复建议。

| 范围 | 确认的不一致 | 建议取舍与证据边界 |
|---|---|---|
| 项目交付 / 基本信息 | `d1dcdfc3c` 整合新增合同关系分页读取后，overview成功也须等全部关系查询完成才赋值；关系失败则概览未赋值并显示商务资料失败。源分支无此依赖 | 保留当前合同关系能力，恢复概览独立展示与失败隔离；继续保留权限检查和请求过期保护。已使用两侧真实SFC脚本受控执行延迟/失败场景，见 behavior-probes.json；这不是浏览器验收，也不是此前SQL优化失效 |
| 项目交付 / 需求分析 | 源 `6654121c5` 删除网络拓扑@NotBlank，d1合并未保留 | 按源优先处理这项字段约束差异，保留当前基类和字段元数据增强。尚未证明具体保存入口执行该DO校验；不得扩大结论为所有需求保存失败 |
| 割接 / 上传收尾 | 源 `989446617` 的两项并行读取在d1变串行 | 有实际取舍：当前新增空引用失败保护，且测试明确要求空引用时不调用getVersions。不能直接恢复Promise.all并删除该失败测试；应保留保护，单独评估有效引用的并行优化。未测上传端到端时延 |
| 巡检 | 对源tip与HEAD的 inspection/Inspection 路径比对无差异；割接后端 pms-module-cutover 同样无差异 | 在该路径范围内未发现被覆盖内容；共享设备日志/文件能力仍有整合，不将路径相等宣称为完整业务验收。巡检权威产品数据等已有裁决继续适用 |
| 同步集成 / 产品匹配 | 当前catalog仅允许product_code；源额外允许source_system+source_key | 以当前实现为准，不恢复旧catalog。历史04055e8b3的crm-product模板使用来源复合键，不能原样套入当前catalog；这是旧任务兼容影响，未查询真实数据库中仍有多少此类任务 |
| 同步集成 / 质保字段 | 当前catalog使用warranty_years；源shipment-contract模板仍含warranty_flag | 以当前实现为准；旧模板不能直接原样接入。不将flag与years假定为等价字段或自动改写历史任务。catalog对照见 catalog-compatibility.json，未执行同步或迁移 |
| 资产附带差异 / 维保面板 | 源为点击加载；当前watch immediate使组件初始化即请求，按钮仍称加载维保记录 | 记录为附带交互差异，未获本次模块优先级直接裁决。受控执行真实脚本时源初始化请求0次、当前1次；未证明真实浏览器所有页签的挂载时机，暂不等同整页请求回归 |

补查覆盖了割接前端全部6个差异路径：除上述并行变化外，ClosureEvidencePanel、ClosureForm、PlanEditor、PlanPanel均为新增文件引用失效保护；另一路径为相应失败测试。建议保留这些保护。

本轮新增的是审计证据与取舍建议，未修业务代码、未启动/重启服务、未提交或推送。170个来源提交账本是文件接入追踪；已检查的重点语义和受控脚本结果列于此，不声称170个提交的每个业务场景均已验收。

## 补正：项目基础信息界面遗漏

上一轮将ProjectBaseInfo.vue概括为扩展整合，未逐项报告布局、字段顺序、命名和取值变化，这是审计遗漏，不能据此认为源界面已保留。

源 `fc8993892` 引入当前源分支的平铺字段结构；`d1dcdfc3c` 的remerge diff明确将单个section改回group循环并增加分组标题。当前文件无未提交修改，差异属于已合入代码，不是本轮SQL修复造成。

| 项目 | 源分支 | 当前工作树HEAD |
|---|---|---|
| 主体布局 | 单个基础信息区域，19项字段平铺；合同号起首 | 项目档案、组织与人员、客户与参与方三个分组；项目编码/名称起首 |
| 合同号 | 项目登记合同号与关联合同号合并去重展示 | 主体改为登记合同号，仅取project.contractNo；关联合同仍在下方商务区域，不再同一汇总字段 |
| 分类标签 | signingMethod标为项目类别；projectCategory标为项目类型 | 分别改为签约方式、项目类别；需逐项结合已批准命名裁决判断，不能仅因源优先就机械改字典映射 |
| 代理商 | 下单/施工代理商，仅从AGENT参与方读取 | 下单代理商，兼容AGENT/30且可回退执行单来源 |
| 日期 | 项目开始时间仅YYYY-MM-DD | 使用通用time格式；新增刷新、关闭、状态更新时间等 |
| 展示密度 | 19项基础字段 | 增加业务层级、结构深度、销售/业务类型、工勘结束日期、实施地点、发货/执行状态、市场细分、渠道等 |
| 商务与附属区块 | 合同、销售订单、执行单 | 增加合同关系时间/状态、回款信息、回款计划、设备许可及来源字段 |
| 已保留的源显示限制 | 补充信息仅admin可见；商务资料受权限控制 | 两项限制仍保留；不能概括为所有界面改动都被覆盖 |

按用户的项目交付源优先口径，源分支的布局与字段顺序未保留应单独列为界面覆盖项。建议以源界面为调整基线，逐项处理当前新增承接字段及明确命名裁决，保留权限、历史关系和来源事实；不整文件回退。本补查为模板、脚本与Git来源核查，尚未对两侧页面作浏览器截图对照。

## 范围与判定方法

针对最近三次直接接入该分支的合并，使用 `git log firstParent..sourceParent` 枚举170个新进入历史的提交（159+3+8），使用 merge-base→sourceParent 的净变更逐文件比对合并结果；另以 `git show --remerge-diff` 检查冲突解决及合并时额外修改。更早合并和全树现状仍见原报告，本表不声称170个提交覆盖分支全部历史。
“原样采用”指合并时文件blob与源父提交相同；“两侧整合/改写”指与两个父提交均不同；“沿用目标”指与第一父提交相同且不同于源父提交。后一种也可能因为源改动已提前承接，不能直接当作丢失。逐提交表只表示文件接入，不等同逐项业务验收。
完整路径、提交、合并归属和HEAD对源tip的相等性见 [merge-provenance.json](merge-provenance.json)。可依次运行 `python .run/domain-diff-audit/provenance.py` 与 `python .run/domain-diff-audit/timeline.py` 在当前工作区重建JSON及时间信息，脚本按上述Git命令运行；临时脚本不作为正式实现。

| 合并提交 | 新进入提交 | 原样采用文件 | 两侧整合/改写文件 | 沿用目标文件 | remerge差异路径 |
|---|---:|---:|---:|---:|---:|
| d1dcdfc3c | 159 | 4061 | 173 | 17 | 191 |
| c2d20d57b | 3 | 39 | 6 | 0 | 2 |
| c674a8a87 | 8 | 82 | 8 | 3 | 1 |

remerge差异路径数量包含冲突标记清理和自动合并结果之外的修改，不是缺陷数；不同合并的文件计数不可直接当作唯一文件数。

## 时间线复核（北京时间）

作者时间和提交时间均存入JSON；接入先后以父提交和祖先关系为准，日期用于解释发生顺序。下面关键提交的作者时间与提交时间相同。提交时间不等于部署时间，不采用“较新的文件一定正确”的取舍规则。

| 时间 | 分支侧 / 提交 | 事件与判断 |
|---|---|---|
| 10-07 18:15:18 | 目标 `231a53bc0` | 需求分析接入继承模型，保留网络拓扑必填 |
| 10-14 15:21:35 | 源 `6654121c5` | 明确删除网络拓扑@NotBlank；与目标提交不构成祖先关系，是分支并行演化 |
| 10-15 18:00:50 | 源 `989446617` | 上传后并行读取文件与版本 |
| 10-15 23:18:00 | 目标 `7907c8117` | 独立迁入与同步配置调整，早于本轮合并 |
| 10-16 20:15:12 | 源 `123ff9ca4` | 增加CRM产品字段及product_code匹配键，仍保留来源复合键；与7907不构成祖先关系 |
| 10-16 23:37:20 | 源 `e63f6c44c` | 调整上传引用参数，并行逻辑仍在 |
| 10-23 01:58:06 | 源 `b7d4a42b3` | 第一次合并采用的源快照截止点 |
| 10-23 09:30:00–09:30:30 | 源 `9f9628c2a`、`3742782e1`、`e36c9a42d` | 三项新提交已经产生，但不属于第一次合并选定的源快照 |
| 10-23 10:24:30 | 合并 `d1dcdfc3c` | 接入b7d快照；网络拓扑沿用10-07旧约束，上传并行在合并时变串行；同步配置沿用目标既有方案 |
| 10-23 10:31:17 | 合并 `c2d20d57b` | 补入09:30的三项增量；因此它们在第一次合并缺席是接入批次差异，不能称为覆盖 |
| 10-23 18:27:47 | 目标 `5f0122a45` | 设备清单授权关联优化已先进入目标分支 |
| 10-23 18:41:31 | 源 `aaaa27c76` | 同名设备清单优化进入源分支；必须对比补丁，不能仅看不同SHA判断目标缺失 |
| 10-23 18:52:13 | 目标 `b143cfe89` | 独立同步承接与批处理继续修改目标分支 |
| 10-23 18:56:49 | 源 `604dc3a43` | 第三次合并的源截止点 |
| 10-23 19:32:07 | 合并 `c674a8a87` | 接入剩余8个提交；前两项覆盖仍存在；设备优化部分文件沿用目标是提前已有相应修改 |

设备优化两份提交的33个路径集合相同，33个文件的新增/删除行序列全部相同；差异在原有文件上下文。逐文件比对见 [parallel-optimization-timeline.json](parallel-optimization-timeline.json)；整提交patch-id不同，因此不把两个提交宣称为完全相同补丁。合并时沿用目标的3个代码文件应结合18:27的先行提交解释。

## 已证实的源内容未保留

### 1. 上传收尾的并行读取被串行替代

- 源提交 `989446617` 引入 Promise.all；`e63f6c44c` 后续调整引用键和版本参数。源tip仍并行。
- `d1dcdfc3c` 的remerge diff直接显示删除Promise.all，改成getArtifact → 空值失败 → getVersions；当前 `CutoverChecklistField.vue:270` 仍串行。
- 有效引用路径失去并行性；空引用校验是目标分支增加的保护，恢复并行时应保留该保护。未进行上传写入或实际时延复测。
- 修正上一报告归属：`3742782e1` 对此文件只新增 aria-busy，此改动在 `c2d20d57b` 原样合入，不能称该提交的并行优化被覆盖。

### 2. 网络拓扑取消必填的改动被覆盖

- 源提交 `6654121c5` 对 `RequirementAnalysisDO.java` 的完整修改只有删除 networkTopology 上的 `@NotBlank`。
- 目标此前 `231a53bc0` 增强项目业务基类、业务字段元数据与类型处理，但保留原必填注解。`d1dcdfc3c` 冲突解决选择目标字段块，未合入该删除。
- 当前第25–26行仍是 `@NotBlank` + networkTopology；这是可确定的校验元数据差异。源提交在该文件上的意图未保留。
- 不能据此声称所有页面保存都失败：本轮未逐入口验证是否对该DO执行Bean Validation。修复应只处理这条注解取舍，保留基类、字段元数据和类型处理增强，并核对既定需求后验证实际入口。

## 实际替换/整合，不应整文件回退

| 来源提交或路径 | 合并取舍 | 当前结果 |
|---|---|---|
| `123ff9ca4` 产品来源字段；generic-targets.json | d1沿用目标配置 | CRM新增字段仍在；产品匹配keys从源的 source_system+source_key 与 product_code 两套，变为仅product_code。这是实际配置差异，不是新增CRM字段丢失；须结合独立同步隔离方案判断，不能直接恢复整份配置 |
| generic-targets.json其余目标 | d1沿用目标此前7907c8117等提交的配置 | warranty_flag改warranty_years；ERP订单行从不可写改为可写并增加承接列；新增运输/客户来源字段。这些是同步路径及数据契约差异，本轮未执行同步或迁移 |
| `905167e97` 交付范围合同信息 | d1沿用已增强的目标文件 | selectOrderContracts改为selectOrderFields，合同号仍映射到响应，同时增加数量/单位；page改用PageQuery对象 |
| `967381ccf` / `e50dc4a24` 设备日志解析 | d1沿用目标部分文件并整合服务 | 解析记录入口保留，history改visibleHistory且空可见项目集合返回空；UI增加卸载时失效序号。属于增加范围保护，不是删除日志功能 |
| `dadc8feb4` 项目联系人 | d1沿用目标服务和测试 | 原联系人处理仍在，追加迁入最终客户联系人只读、禁止普通编辑/删除/恢复及展示信息 |
| `355107ef5` 流式同步 | d1整合实现、沿用增强测试 | 流式管道/分块事务/预取保留，样本提交时点调整；恢复测试增加锁、租户和失败证据保护 |
| `cd53e72cb` 前端类型修复 | d1沿用部分目标组件 | ContentHistoryPanel用值窄化函数替代模板强制断言；FileSlot改FileId，保留字符串ID；不是撤销修复 |
| `aaaa27c76` 设备清单授权SQL | c674大部原样采用，三文件沿用目标 | DeviceMapper仅锁方法声明位置不同；ContractAccessService保留根对象读取/回款/关系增强；ContractMapper抽取rootReadScope并空范围失败关闭。核心设备授权关联SQL保留 |
| `9f9628c2a` 授权复用/批量树 | c2整合 | DeviceController去掉旧organizationService注入，保留目标发货投影字段；请求内复用与批量树代码保留 |
| `e36c9a42d` 上传组件测试类型 | c2冲突采用源修复 | 保留attrs.modelValue类型转发，替换目标固定空字符串写法 |
| `486986c1f` 文件授权读取复用 | c674整合 | 复用保留，同时保留目标稳定引用键发现及空引用保护 |
| `b498519eb` 合同概览/按需面板 | d1整合 | 原按关联主键读取及36面板懒加载保留，叠加历史执行单链/回款；新增读取成本已在上一查询任务定向修复 |
| `b073f52e0` / `792f90163` / `604dc3a43` | c674接入 | 草稿离开保护、采集校验/恢复、系统身份回调相关增量进入合并；文件级详情见逐提交账本，不以该事实替代运行验收 |
| F-AST-001任务记录 | c674文档冲突整合 | 保留源批量树验证记录和目标组织回填记录；remerge统计中的6行删除是冲突标记，不能算证据正文丢失 |

## 沿用目标的全部20个文件事件

以下列出最易被误判为“没合入”的全部文件事件；17个发生于d1，3个发生于c674。每个均检查过源父提交→合并结果差异。除上述明确注解覆盖和配置差异外，其余为增强、替代实现、类型调整、测试扩充或空白差异；此分类不是完整业务验收。

| 合并 | 文件 |
|---|---|
| d1dcdfc3c | `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/device/DeviceParsedLogController.java` |
| d1dcdfc3c | `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/dataobject/product/AssetProductOfficialDO.java` |
| d1dcdfc3c | `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/configurationlog/DeviceLogParsedFactMapper.java` |
| d1dcdfc3c | `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/scope/DeliveryScopeController.java` |
| d1dcdfc3c | `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/controller/admin/scope/vo/DeliveryScopeRespVO.java` |
| d1dcdfc3c | `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/scope/CommerceDeliveryScopeQueryService.java` |
| d1dcdfc3c | `pms-module-customer/src/main/java/cn/iocoder/yudao/module/pms/customer/service/contact/ProjectCustomerContactService.java` |
| d1dcdfc3c | `pms-module-customer/src/test/java/cn/iocoder/yudao/module/pms/customer/service/contact/ProjectCustomerContactServiceTest.java` |
| d1dcdfc3c | `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/requirement/RequirementAnalysisDO.java` |
| d1dcdfc3c | `pms-module-integration/src/main/resources/sync/generic-targets.json` |
| d1dcdfc3c | `pms-module-integration/src/test/java/cn/iocoder/yudao/module/pms/integration/sync/SyncRunMaintenanceTest.java` |
| d1dcdfc3c | `yudao-ui/yudao-ui-admin-vue3/src/api/pms/commerce/index.ts` |
| d1dcdfc3c | `yudao-ui/yudao-ui-admin-vue3/src/components/BusinessEntity/ContentHistoryPanel.vue` |
| d1dcdfc3c | `yudao-ui/yudao-ui-admin-vue3/src/components/DeviceCollection/semantic/types.ts` |
| d1dcdfc3c | `yudao-ui/yudao-ui-admin-vue3/src/components/PmsFileArtifact/useFileSlotState.ts` |
| d1dcdfc3c | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device/components/DeviceParsedLogRecords.vue` |
| d1dcdfc3c | `yudao-ui/yudao-ui-admin-vue3/src/views/pms/integration/TaskEditor.runtime.spec.ts` |
| c674a8a87 | `pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/dal/mysql/device/DeviceMapper.java` |
| c674a8a87 | `pms-module-commerce/src/main/java/cn/iocoder/yudao/module/pms/commerce/service/contract/ContractAccessService.java` |
| c674a8a87 | `pms-module-commerce/src/main/resources/mapper/contract/ContractMapper.xml` |

## 170个来源提交的文件接入账本

数字按该提交触及的路径在对应合并中的最终净结果计数；同一路径在多个来源提交中出现时会重复计数。“无净变化”指到源父提交时相对merge-base已无净变化。合并提交本身若无普通diff-tree输出则显示0，不能解释为没有内容。

| 来源提交 | 源提交时间（+08:00） | 标题 | 接入合并 | 原样采用 | 整合/改写 | 沿用目标 | 无净变化 |
|---|---|---|---|---:|---:|---:|---:|
| ffc5b86eb | 2026-10-07 16:05:41 | docs(template): 保存系统记录版交付模板草稿证据 | d1dcdfc3c | 26 | 1 | 0 | 0 |
| 27e04716d | 2026-10-07 16:08:46 | feat(asset): 归档拆分设备日志并判定最新文件 | d1dcdfc3c | 43 | 3 | 0 | 4 |
| fc97ec7eb | 2026-10-07 16:15:48 | chore(merge): 合入模板草稿与设备日志功能 | d1dcdfc3c | 0 | 0 | 0 | 0 |
| adcfe75a6 | 2026-10-09 17:43:45 | fix(platform): 兼容 HTTP 环境下的 UUID 调用 | d1dcdfc3c | 3 | 3 | 0 | 0 |
| 045967e67 | 2026-10-09 17:43:50 | fix(engineering): 工勘确认时同步归档 | d1dcdfc3c | 2 | 1 | 0 | 0 |
| ea207e2c3 | 2026-10-09 17:43:56 | fix(engineering): 到货交付件保存后完成签收 | d1dcdfc3c | 5 | 1 | 0 | 0 |
| e85481515 | 2026-10-09 17:44:02 | fix(acceptance): 独立验收上传保存即完成 | d1dcdfc3c | 10 | 2 | 0 | 0 |
| 22bf74f30 | 2026-10-09 17:44:06 | fix(solution): 对齐实施方案反馈要求 | d1dcdfc3c | 6 | 0 | 0 | 0 |
| 3cc32e6ea | 2026-10-10 18:36:32 | feat(acceptance): 成套提交完工证明与满意度调查 | d1dcdfc3c | 204 | 2 | 1 | 1 |
| c969e8057 | 2026-10-11 22:53:54 | fix(project): 阻止未满足准入的审批提交 | d1dcdfc3c | 9 | 1 | 0 | 0 |
| dd3929aef | 2026-10-11 22:58:37 | build(worktree): 固定构建工具链与冻结产物部署 | d1dcdfc3c | 8 | 0 | 0 | 0 |
| df64cc1d2 | 2026-10-11 22:58:38 | chore(dev): 禁用本地数据库二进制日志 | d1dcdfc3c | 1 | 0 | 0 | 0 |
| c47764a05 | 2026-10-11 22:58:39 | feat(dac): 支持设备日志解析与语义版本投影 | d1dcdfc3c | 32 | 0 | 0 | 8 |
| fc0d9b840 | 2026-10-11 22:58:45 | feat(integration): 接入设备日志解析接口 | d1dcdfc3c | 4 | 0 | 0 | 0 |
| 967381ccf | 2026-10-11 22:59:00 | feat(platform): 提供设备日志解析及结果展示 | d1dcdfc3c | 29 | 0 | 1 | 0 |
| e50dc4a24 | 2026-10-11 22:59:03 | feat(asset): 归档日志并投影设备解析事实 | d1dcdfc3c | 42 | 11 | 3 | 0 |
| 9f19bce92 | 2026-10-11 22:59:03 | fix(location): 保留地址快照并同步选择器状态 | d1dcdfc3c | 4 | 0 | 0 | 0 |
| 611ddd633 | 2026-10-11 22:59:05 | perf(asset): 优化设备可见范围及序列号查询 | d1dcdfc3c | 20 | 7 | 0 | 0 |
| f9595a978 | 2026-10-11 22:59:06 | fix(asset): 对齐设备安装位置与合同组织投影 | d1dcdfc3c | 6 | 8 | 0 | 0 |
| 355107ef5 | 2026-10-11 22:59:07 | perf(integration): 优化分页同步与流式写入 | d1dcdfc3c | 13 | 14 | 2 | 0 |
| 905167e97 | 2026-10-11 22:59:07 | feat(commerce): 补充交付范围订单合同信息 | d1dcdfc3c | 4 | 4 | 3 | 0 |
| 49688b4bd | 2026-10-11 22:59:13 | feat(installation): 支持批量地址与安装结果归集 | d1dcdfc3c | 33 | 3 | 0 | 0 |
| 91ce20db9 | 2026-10-11 22:59:14 | feat(configuration): 导入设备日志关联配置记录 | d1dcdfc3c | 18 | 3 | 0 | 0 |
| 3f7b20154 | 2026-10-11 22:59:15 | feat(joint-test): 接入设备日志采集工作台 | d1dcdfc3c | 3 | 0 | 0 | 0 |
| ba71dde9b | 2026-10-11 22:59:16 | feat(engineering): 提供安装与施工计划原生结果 | d1dcdfc3c | 1 | 0 | 0 | 0 |
| 1be5848ef | 2026-10-11 22:59:17 | fix(stage-plan): 归集审批结果并记录提交原因 | d1dcdfc3c | 16 | 2 | 0 | 0 |
| efd9bdf6a | 2026-10-11 22:59:17 | fix(arrival): 保存签收文件后关联交付记录 | d1dcdfc3c | 8 | 3 | 0 | 0 |
| dadc8feb4 | 2026-10-11 22:59:18 | fix(project-contact): 按客户有效联系人自动完成任务 | d1dcdfc3c | 21 | 4 | 2 | 0 |
| 1b5d591ac | 2026-10-11 22:59:19 | feat(project-task): 按联系人与安装事实校验完成条件 | d1dcdfc3c | 2 | 0 | 0 | 0 |
| a08a01a0c | 2026-10-11 22:59:20 | fix(project-plan): 完整激活计划返工事务 | d1dcdfc3c | 3 | 0 | 0 | 0 |
| 6c268a2c6 | 2026-10-11 22:59:28 | fix(pagination): 统一分页默认值与请求参数 | d1dcdfc3c | 6 | 0 | 0 | 0 |
| c0744f18e | 2026-10-11 22:59:29 | fix(business-entity): 简化业务操作失败提示 | d1dcdfc3c | 0 | 1 | 0 | 0 |
| 36857200e | 2026-10-11 22:59:29 | refactor(ext-proc): 拆分采购表单与详情弹窗 | d1dcdfc3c | 0 | 3 | 0 | 0 |
| 81a0d8d5e | 2026-10-11 22:59:30 | refactor(material-req): 拆分领用表单与详情弹窗 | d1dcdfc3c | 0 | 3 | 0 | 0 |
| 8f7530aed | 2026-10-11 22:59:31 | refactor(outsource): 拆分外包表单与详情弹窗 | d1dcdfc3c | 0 | 4 | 0 | 0 |
| 9ff38fa66 | 2026-10-11 22:59:31 | fix(material-exchange): 对齐产品合同及申请字段 | d1dcdfc3c | 6 | 4 | 0 | 0 |
| ea1fb9a3f | 2026-10-11 22:59:32 | fix(site-survey): 补齐地址快照与分支申请办理 | d1dcdfc3c | 29 | 1 | 0 | 0 |
| a7b5decbe | 2026-10-11 22:59:33 | fix(bpm): 按方案级别选择审核分支 | d1dcdfc3c | 4 | 0 | 0 | 0 |
| 6c2c7a332 | 2026-10-11 22:59:33 | fix(solution): 对齐需求来源与审核记录展示 | d1dcdfc3c | 7 | 4 | 0 | 0 |
| f3cc03ae8 | 2026-10-11 22:59:34 | fix(cutover): 绑定业务表单并保护审批办理 | d1dcdfc3c | 4 | 0 | 0 | 0 |
| 934676865 | 2026-10-11 22:59:38 | fix(acceptance): 对齐独立验收入口与交付归集 | d1dcdfc3c | 14 | 0 | 0 | 0 |
| 83e289b5d | 2026-10-11 22:59:39 | fix(completion): 适配移动签名与证明打印布局 | d1dcdfc3c | 56 | 0 | 0 | 0 |
| 463d1605f | 2026-10-11 22:59:39 | feat(project): 展示节点超期并扩展项目筛选 | d1dcdfc3c | 21 | 3 | 0 | 0 |
| aff8baece | 2026-10-11 22:59:40 | feat(project): 内嵌审批记录详情抽屉 | d1dcdfc3c | 9 | 0 | 0 | 0 |
| 80c5b3b7a | 2026-10-11 22:59:41 | feat(project): 集成安装日志工作台及业务导航 | d1dcdfc3c | 5 | 3 | 0 | 0 |
| ff88ddb9d | 2026-10-11 22:59:41 | docs(architecture): 同步模块接口与业务裁决 | d1dcdfc3c | 2 | 2 | 0 | 0 |
| 2e9b7300b | 2026-10-11 22:59:42 | docs(project): 补充项目办理与验收证据 | d1dcdfc3c | 1 | 0 | 0 | 0 |
| 4803d3260 | 2026-10-13 18:01:09 | feat(project): 完整加载项目树并统一详情展示 | d1dcdfc3c | 13 | 7 | 0 | 0 |
| 05e43a134 | 2026-10-13 19:40:13 | fix(acceptance): 生成前复用已登记完工确认PDF | d1dcdfc3c | 17 | 0 | 0 | 0 |
| 4e9b7c06b | 2026-10-14 15:20:53 | chore(worktree): 固定工作树构建与宿主机运行入口 | d1dcdfc3c | 5 | 0 | 0 | 0 |
| 37143fb62 | 2026-10-14 15:21:02 | feat(system): 提供租户角色成员资格事实 | d1dcdfc3c | 8 | 0 | 0 | 0 |
| a1ddfc19a | 2026-10-14 15:21:04 | feat(platform): 完善采集文件契约与业务授权 | d1dcdfc3c | 34 | 4 | 0 | 0 |
| 799a34c49 | 2026-10-14 15:21:05 | feat(customer): 提供项目联系人引用事实 | d1dcdfc3c | 3 | 0 | 0 | 0 |
| 8dfe100ee | 2026-10-14 15:21:24 | feat(commerce): 同步合同订单范围与设备清单 | d1dcdfc3c | 13 | 4 | 0 | 0 |
| fc8993892 | 2026-10-14 15:21:26 | feat(project): 接通项目任务事实与闭环规则 | d1dcdfc3c | 90 | 10 | 0 | 0 |
| 6654121c5 | 2026-10-14 15:21:35 | feat(engineering): 完善实施事实与需求表单 | d1dcdfc3c | 161 | 11 | 1 | 0 |
| acd0bd986 | 2026-10-14 15:21:36 | feat(asset): 消费割接结果并保护设备日志事实 | d1dcdfc3c | 16 | 5 | 0 | 0 |
| e87c162d9 | 2026-10-14 15:22:08 | feat(bpm): 支持割接角色共享待办与受控办理 | d1dcdfc3c | 14 | 0 | 0 | 0 |
| b680f305a | 2026-10-14 15:23:21 | feat(cutover): 实现结构化流程并清理旧入口 | d1dcdfc3c | 430 | 1 | 0 | 130 |
| adaf442a3 | 2026-10-14 15:23:28 | feat(acceptance): 完善验收授权与成果归集 | d1dcdfc3c | 39 | 1 | 0 | 0 |
| bc79d11bc | 2026-10-14 15:23:30 | feat(frontend): 接通跨模块页面路由与交互 | d1dcdfc3c | 6 | 1 | 0 | 0 |
| 95bfcd491 | 2026-10-14 15:24:14 | docs(architecture): 同步业务裁决与专项验收记录 | d1dcdfc3c | 132 | 2 | 0 | 0 |
| bf935b554 | 2026-10-14 15:26:10 | fix(cutover): 收敛独立办理入口与测试装配 | d1dcdfc3c | 15 | 1 | 0 | 1 |
| 7f6f384e4 | 2026-10-14 17:08:34 | test(cutover): 对齐工作台组件测试契约 | d1dcdfc3c | 3 | 1 | 0 | 1 |
| e556581e7 | 2026-10-15 00:08:03 | refactor(cutover)!: 收敛割接实现并修复历史版本访问 | d1dcdfc3c | 619 | 8 | 0 | 130 |
| 790e90e88 | 2026-10-15 00:23:07 | fix(project): 完善需求分析提示和项目任务办理体验 | d1dcdfc3c | 8 | 4 | 0 | 0 |
| b5a73442a | 2026-10-15 00:23:07 | fix(cutover): 将旧项目详情接入现行割接组件 | d1dcdfc3c | 1 | 0 | 0 | 0 |
| 989446617 | 2026-10-15 18:00:50 | fix(cutover): 改善材料上传刷新和页面提示 | d1dcdfc3c | 8 | 3 | 0 | 0 |
| 07a5cef07 | 2026-10-15 18:03:38 | fix(collection)!: 解除命令模板与连接协议耦合 | d1dcdfc3c | 15 | 0 | 0 | 0 |
| 4c8f5b298 | 2026-10-15 18:10:09 | fix(cutover): 修复修订匹配与详情式新建交互 | d1dcdfc3c | 110 | 8 | 0 | 0 |
| 0f8f55879 | 2026-10-15 18:14:54 | feat(inspection)!: 实现独立巡检闭环并退役旧入口 | d1dcdfc3c | 302 | 5 | 0 | 2 |
| 2632aa5b5 | 2026-10-16 00:02:51 | fix(inspection): 清理旧维保目录并补齐验收记录 | d1dcdfc3c | 3 | 0 | 0 | 0 |
| 831379972 | 2026-10-16 02:53:30 | feat(project): 完善模板按需任务与督导闭环 | d1dcdfc3c | 113 | 15 | 0 | 0 |
| 92129b4a2 | 2026-10-16 13:07:34 | fix(engineering): 校验实施方案必填与来源文件 | d1dcdfc3c | 12 | 4 | 0 | 0 |
| fe0f90f55 | 2026-10-16 13:07:45 | fix(project): 消除正常闭环任务的循环校验 | d1dcdfc3c | 11 | 0 | 0 | 0 |
| e065d1701 | 2026-10-16 13:08:21 | fix(delivery): 按任务归集日志并补齐项目66验收证据 | d1dcdfc3c | 81 | 2 | 0 | 0 |
| 123ff9ca4 | 2026-10-16 20:15:12 | feat(project): 完善项目选择资料与客户产品来源字段 | d1dcdfc3c | 36 | 4 | 2 | 0 |
| 8eec472fb | 2026-10-16 20:15:31 | feat(cutover): 完善等级确认与方案采集办理闭环 | d1dcdfc3c | 90 | 3 | 0 | 0 |
| 275126166 | 2026-10-16 20:15:39 | feat(inspection): 完善多设备日志上传与执行解析 | d1dcdfc3c | 20 | 0 | 0 | 0 |
| cbd145505 | 2026-10-16 20:15:46 | fix(project): 完善交付件调整与项目详情办理展示 | d1dcdfc3c | 27 | 4 | 0 | 0 |
| 521b0756a | 2026-10-16 20:15:53 | docs: 归档项目办理验收证据与接口决策 | d1dcdfc3c | 53 | 2 | 0 | 0 |
| fad78a616 | 2026-10-16 20:45:46 | fix(frontend): 清除项目办理页面ESLint错误与警告 | d1dcdfc3c | 11 | 6 | 0 | 0 |
| cd53e72cb | 2026-10-16 21:18:12 | fix(frontend): 修复全量检查的40项既有类型错误 | d1dcdfc3c | 10 | 4 | 1 | 0 |
| 102ae30b1 | 2026-10-16 23:17:18 | fix(frontend): 修复工勘问卷测试失败与静态检查问题 | d1dcdfc3c | 5 | 1 | 0 | 0 |
| 591cc9f6b | 2026-10-16 23:37:04 | fix(platform): 完善材料上传重试与引用刷新交互 | d1dcdfc3c | 4 | 3 | 1 | 0 |
| e63f6c44c | 2026-10-16 23:37:20 | fix(cutover): 完善调研材料接入与弹层布局 | d1dcdfc3c | 4 | 3 | 0 | 0 |
| 3ea1f8dd8 | 2026-10-16 23:47:56 | test(frontend): 对齐工期附件与割接新建契约 | d1dcdfc3c | 1 | 1 | 0 | 0 |
| a4abd217f | 2026-10-17 03:00:03 | fix(platform): 扩大交付件提交证据容量 | d1dcdfc3c | 3 | 0 | 0 | 0 |
| b8c5bab34 | 2026-10-17 03:02:40 | fix(engineering): 保留联调日志上传的原任务归属 | d1dcdfc3c | 7 | 0 | 0 | 0 |
| 42f8d6dcf | 2026-10-17 03:04:21 | fix(device-log): 保存不可变解析快照供离线读取 | d1dcdfc3c | 13 | 3 | 0 | 0 |
| 9f6a9102c | 2026-10-17 03:04:40 | feat(project): 按已批准交付件规则完成联调任务 | d1dcdfc3c | 7 | 0 | 0 | 0 |
| 7960f035f | 2026-10-17 10:15:17 | docs(engineering): 记录联调自动完成部署前验证 | d1dcdfc3c | 2 | 0 | 0 | 0 |
| 36bfcd738 | 2026-10-17 13:20:35 | fix(pms): 修复经理权限与项目范围校验 | d1dcdfc3c | 45 | 4 | 0 | 0 |
| b498519eb | 2026-10-17 13:20:55 | perf(project): 优化详情基本信息加载 | d1dcdfc3c | 2 | 2 | 0 | 0 |
| 408683dfd | 2026-10-17 18:19:10 | feat(project): 补齐外部成员部门与职务资料 | d1dcdfc3c | 4 | 7 | 0 | 0 |
| f9e10a53c | 2026-10-17 18:19:10 | fix(customer): 按项目范围读取关联客户地点 | d1dcdfc3c | 11 | 2 | 0 | 0 |
| 95980c519 | 2026-10-17 18:19:18 | fix(survey): 删除草稿时关闭当前任务业务引用 | d1dcdfc3c | 10 | 0 | 0 | 0 |
| b877eacc6 | 2026-10-17 18:19:18 | fix(survey): 隐藏已停用的独立申请入口 | d1dcdfc3c | 4 | 0 | 0 | 0 |
| 70ffa5d0a | 2026-10-17 18:19:18 | fix(requirement): 完善草稿展示与附件上传入口 | d1dcdfc3c | 5 | 1 | 0 | 0 |
| 070573cf3 | 2026-10-17 18:19:19 | fix(bpm): 避免缺少手机号回滚审批事务 | d1dcdfc3c | 4 | 0 | 0 | 0 |
| 99059ebd9 | 2026-10-17 18:19:40 | build(database): 提供V498正式测试空库发布包 | d1dcdfc3c | 11 | 0 | 0 | 0 |
| 3a31305e7 | 2026-10-17 18:19:40 | docs(engineering): 收口联调自动完成与模板验收 | d1dcdfc3c | 4 | 0 | 0 | 0 |
| cbe59bdc0 | 2026-10-17 18:19:49 | docs(pms): 归档项目权限契约与角色复测证据 | d1dcdfc3c | 59 | 1 | 0 | 0 |
| 98a8aa83c | 2026-10-19 19:21:49 | fix(governance): 校准存量实现清单与诊断范围 | d1dcdfc3c | 5 | 0 | 0 | 0 |
| e099db0da | 2026-10-19 19:21:56 | refactor(project): 退役旧项目入口并保留当前风险链路 | d1dcdfc3c | 83 | 1 | 0 | 0 |
| e2e709e46 | 2026-10-19 19:22:03 | fix(acceptance): 加固原生验收范围与材料引用校验 | d1dcdfc3c | 27 | 9 | 0 | 0 |
| c54867aa2 | 2026-10-19 19:22:04 | fix(bindings): 校验场景执行身份与结果可见范围 | d1dcdfc3c | 8 | 0 | 0 | 0 |
| f2f715d17 | 2026-10-19 19:22:04 | fix(template): 保持发布快照与规则编译边界 | d1dcdfc3c | 7 | 0 | 0 | 0 |
| 45038f397 | 2026-10-19 19:22:05 | fix(system): 按指定身份校验授权与账号有效性 | d1dcdfc3c | 3 | 1 | 0 | 0 |
| 175a791cd | 2026-10-19 19:22:05 | fix(engineering): 从原审批任务回写工期同意意见 | d1dcdfc3c | 12 | 0 | 0 | 0 |
| 3acb1a7eb | 2026-10-19 19:22:13 | fix(cutover): 冻结客户服务等级并完善人员选择 | d1dcdfc3c | 64 | 0 | 0 | 0 |
| 5e42b55b8 | 2026-10-19 19:22:25 | build(configuration): 迁移已验证配置并保留发布边界 | d1dcdfc3c | 13 | 0 | 0 | 0 |
| 2a8edb3be | 2026-10-19 19:22:26 | docs(delivery): 归档存量审查与项目闭环验收边界 | d1dcdfc3c | 9 | 1 | 0 | 0 |
| 72f304bf0 | 2026-10-19 19:22:39 | fix(project-ui): 修复交付刷新与办理入口反馈 | d1dcdfc3c | 14 | 4 | 0 | 0 |
| 1b3acc4f9 | 2026-10-19 19:25:07 | fix(frontend): 修复客户概览类型错误与既有警告 | d1dcdfc3c | 8 | 0 | 0 | 0 |
| 19f434eab | 2026-10-19 19:36:12 | fix(engineering): 合并配置调试重复菜单入口 | d1dcdfc3c | 3 | 0 | 0 | 0 |
| 6d3ea3f47 | 2026-10-19 19:47:03 | refactor(pms)!: 退役旧工期倒排与项目治理入口 | d1dcdfc3c | 37 | 0 | 0 | 0 |
| ef1f1f47f | 2026-10-20 00:32:06 | fix(acceptance): 完善满意度实体闭环与受控下载 | d1dcdfc3c | 24 | 0 | 0 | 0 |
| 7ccb5d95d | 2026-10-20 09:02:08 | fix(deliverable): 修复汇总分类与归档状态误判 | d1dcdfc3c | 9 | 2 | 0 | 0 |
| e822960c0 | 2026-10-20 09:35:29 | fix(project-ui): 优化阶段任务显示与独立展开 | d1dcdfc3c | 1 | 1 | 0 | 0 |
| bf809ff4e | 2026-10-20 09:35:33 | fix(project-ui): 限制基础信息扩展区块可见性 | d1dcdfc3c | 1 | 1 | 0 | 0 |
| 2cd5f2f80 | 2026-10-20 09:35:41 | fix(deliverable): 完善提交布局与历史文件访问 | d1dcdfc3c | 4 | 5 | 0 | 0 |
| 36c7a7f12 | 2026-10-20 09:35:47 | fix(arrival): 内嵌任务签收查询并保留只读权限 | d1dcdfc3c | 5 | 0 | 0 | 0 |
| d47db37a1 | 2026-10-20 09:35:53 | refactor(satisfaction)!: 移除历史独立调查界面 | d1dcdfc3c | 10 | 0 | 0 | 1 |
| 26ca3b3d7 | 2026-10-20 09:36:02 | refactor(project-ui): 整合项目树并精简重复信息 | d1dcdfc3c | 9 | 2 | 0 | 0 |
| a72ec7835 | 2026-10-20 09:36:08 | fix(schedule): 调整生效工期与验收计划概览 | d1dcdfc3c | 0 | 1 | 0 | 0 |
| ed40ca5c7 | 2026-10-20 09:36:13 | docs: 登记督导模板需求分析前置待决问题 | d1dcdfc3c | 0 | 1 | 0 | 0 |
| bb7c7e841 | 2026-10-20 19:52:05 | refactor(dac): 统一设备运维平台命名空间 | d1dcdfc3c | 682 | 0 | 0 | 11 |
| 6a0a4d594 | 2026-10-20 19:56:44 | feat(project): 支持受控新轮次与历史交付隔离 | d1dcdfc3c | 71 | 8 | 0 | 2 |
| ce704527a | 2026-10-20 20:06:37 | feat(project): 提供受控项目成员选择接口 | d1dcdfc3c | 4 | 2 | 0 | 0 |
| f2fa08026 | 2026-10-20 20:08:13 | feat(cutover): 完善草稿办理与审批追溯闭环 | d1dcdfc3c | 114 | 7 | 0 | 0 |
| a17611e9a | 2026-10-22 02:20:56 | feat(dac): 交付本机客户端并修复下发回传 | d1dcdfc3c | 66 | 2 | 0 | 0 |
| ee82e4e0f | 2026-10-22 02:25:31 | fix(solution): 允许实施方案按需引用需求分析 | d1dcdfc3c | 3 | 2 | 0 | 0 |
| 59fb7a642 | 2026-10-22 02:28:01 | feat(file): 完善受控文件预览与文档处理 | d1dcdfc3c | 20 | 11 | 0 | 0 |
| 5d91815ab | 2026-10-22 02:29:05 | feat(cutover): 完善调研证据与任务设备校验 | d1dcdfc3c | 19 | 2 | 0 | 0 |
| 757a1610e | 2026-10-22 02:29:11 | feat(cutover): 完善方案预填与文档导出 | d1dcdfc3c | 31 | 3 | 0 | 0 |
| 978283d54 | 2026-10-22 02:29:19 | fix(cutover): 完善审批改派与完整任务只读访问 | d1dcdfc3c | 24 | 1 | 0 | 0 |
| 2f18acd95 | 2026-10-22 02:29:27 | perf(cutover): 优化工作台读取并修复闭环上传 | d1dcdfc3c | 18 | 1 | 0 | 0 |
| 7c7b1e56d | 2026-10-22 02:29:38 | fix(project): 完善任务准入与业务办理恢复 | d1dcdfc3c | 15 | 3 | 0 | 0 |
| 59f2f4247 | 2026-10-22 02:29:47 | fix(schedule): 回填完成日期并保留审批人标识 | d1dcdfc3c | 12 | 3 | 0 | 0 |
| adbc60cac | 2026-10-22 02:29:53 | fix(survey): 补齐重开后工勘完成依据 | d1dcdfc3c | 3 | 0 | 0 | 0 |
| f7a289c81 | 2026-10-22 02:29:59 | fix(solution): 完善图片脚本与流程访问权限 | d1dcdfc3c | 17 | 4 | 0 | 0 |
| fb9ca460d | 2026-10-22 02:30:04 | fix(acceptance): 修复报告草稿展示与内嵌办理 | d1dcdfc3c | 15 | 0 | 0 | 0 |
| bad462220 | 2026-10-22 02:30:10 | fix(acceptance): 修复完工证明与配置权限 | d1dcdfc3c | 10 | 0 | 0 | 0 |
| b12fc9e0b | 2026-10-22 02:30:17 | fix(project-ui): 修复人员选择与列表初始化权限 | d1dcdfc3c | 4 | 1 | 0 | 0 |
| 8fdb43888 | 2026-10-22 02:30:22 | build: 按构建目标隔离源码一致性检查 | d1dcdfc3c | 4 | 0 | 0 | 0 |
| c68f7513f | 2026-10-22 02:30:29 | docs: 记录技术支持模块授权结果 | d1dcdfc3c | 1 | 0 | 0 | 0 |
| 328cda6a9 | 2026-10-22 02:30:47 | docs: 保存 UMC 接口对接需求清单 | d1dcdfc3c | 3 | 0 | 0 | 0 |
| f6fa373e8 | 2026-10-22 02:31:01 | docs: 保存月底上线准备与迁移计划 | d1dcdfc3c | 1 | 0 | 0 | 0 |
| 1edd4ad9a | 2026-10-22 08:14:29 | feat(dac): 安装成功后自动打开本机状态页 | d1dcdfc3c | 10 | 1 | 0 | 0 |
| 76a2daffa | 2026-10-22 11:47:48 | fix(project): 统一重开与返工结果判定并恢复培训自动完成 | d1dcdfc3c | 12 | 1 | 0 | 2 |
| 8222fd12d | 2026-10-22 21:16:53 | fix(project): 修复自动归集查看与满意度业务区 | d1dcdfc3c | 28 | 5 | 0 | 0 |
| 0a02409a5 | 2026-10-23 01:57:03 | fix(file): 修复版本展示与保存归集一致性 | d1dcdfc3c | 31 | 7 | 0 | 0 |
| 686933308 | 2026-10-23 01:57:15 | feat(acceptance): 完善完工确认与到货日期 | d1dcdfc3c | 42 | 0 | 0 | 0 |
| 20c48c007 | 2026-10-23 01:57:25 | feat(training): 统一培训打印与模板只读选用 | d1dcdfc3c | 15 | 1 | 0 | 0 |
| 6900a44b6 | 2026-10-23 01:57:34 | feat(asset): 优化设备查询与项目关联展示 | d1dcdfc3c | 34 | 7 | 0 | 0 |
| 6fa26979c | 2026-10-23 01:57:42 | fix(project): 完善模板初始化与阶段办理展示 | d1dcdfc3c | 63 | 8 | 0 | 0 |
| 30f17e8e2 | 2026-10-23 01:57:50 | fix(dac): 完善安装下载与原任务续跑 | d1dcdfc3c | 13 | 0 | 0 | 0 |
| b7d4a42b3 | 2026-10-23 01:58:06 | docs: 汇总上线计划与业务验收记录 | d1dcdfc3c | 29 | 2 | 0 | 0 |
| 9f9628c2a | 2026-10-23 09:30:00 | perf(asset): 复用设备授权并批量解析项目范围 | c2d20d57b | 24 | 5 | 0 | 0 |
| 3742782e1 | 2026-10-23 09:30:19 | perf(cutover): 精简清单读取与设备覆盖查询 | c2d20d57b | 14 | 1 | 0 | 0 |
| e36c9a42d | 2026-10-23 09:30:30 | test(solution): 修正上传组件测试转发类型 | c2d20d57b | 1 | 0 | 0 | 0 |
| 486986c1f | 2026-10-23 12:01:03 | perf(file): 复用已授权的当前引用查询结果 | c674a8a87 | 3 | 1 | 0 | 0 |
| fece20a97 | 2026-10-23 12:02:51 | feat(dac): 按授权设备范围隔离本机连接 | c674a8a87 | 29 | 1 | 0 | 0 |
| 400f8492d | 2026-10-23 12:02:58 | fix(cutover): 统一冻结字段说明与方案来源展示 | c674a8a87 | 9 | 0 | 0 | 0 |
| b073f52e0 | 2026-10-23 12:03:06 | fix(cutover): 保护草稿离开与待上传文件 | c674a8a87 | 9 | 5 | 0 | 0 |
| 1fa1f69b4 | 2026-10-23 12:03:13 | docs: 补充读取优化与割接验证记录 | c674a8a87 | 2 | 1 | 0 | 0 |
| aaaa27c76 | 2026-10-23 18:41:31 | perf(asset): 合并设备清单授权查询以减少重复扫描 | c674a8a87 | 28 | 2 | 3 | 0 |
| 792f90163 | 2026-10-23 18:56:36 | fix(collection): 修复采集校验与失效来源恢复 | c674a8a87 | 3 | 0 | 0 | 0 |
| 604dc3a43 | 2026-10-23 18:56:49 | fix(cutover): 使用系统身份记录自动采集回调 | c674a8a87 | 2 | 0 | 0 | 0 |

## 验证边界

完成：Git祖先关系、三次合并的170提交文件接入账本、20个沿用目标文件事件的差异检查、重点优化及两处未保留内容的来源追踪。未完成：所有整合文件的逐业务入口验收、全部191个remerge路径的完整语义验收。不能据此承诺仅有这两处遗漏，也没有证据支持“大量性能优化全部丢失”。本轮无业务代码修改、无测试重跑、无服务重启、无数据库写入。
