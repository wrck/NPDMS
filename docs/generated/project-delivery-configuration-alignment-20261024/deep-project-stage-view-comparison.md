# 项目详情、阶段任务、业务视图深入对比

2026-10-24。按用户要求继续对比，**只读审查，不修改业务代码、权限、业务实例、数据库配置或运行服务**。复用 NPDMS 实现现状审查技能，保留目标已有修复与历史。

## 结论

当前差异已不能归结为基础配置遗漏：存在业务实例初始化缺口、源工作目录未提交修复未带入，以及实际后端运行包的处理逻辑差异。五个正式模板内容一致，并不意味着项目已经生成对应执行计划、阶段和任务。之前“模板、配置已补齐”的结论不能扩大为“项目交付全链路已与源环境一致”。

### 1. 同一业务项目的实例缺口已由两端真实浏览器确认

业务编码 `16200726072401N-0`、项目名称、来源类型和创建时间相符，数据库主键不同：

| 项目 | 源环境 | 测试环境 |
|---|---|---|
| 项目 ID | 2109733194632396801 | 84529 |
| 后端 / 前端 | 59191 / 19191 | 59286 / 19086 |
| 当前计划 | 普通类－非直签，计划 v1 | 无生效计划 |
| 当前模板 | 普通类－非直签 v2 | 已存在可用正式模板，但当前项目未实例化 |
| 阶段 / 任务 / 执行节点 | 7 / 13 / 20 | 0 / 0 / 0 |
| 实际界面 | 显示 S0～S6 阶段轨和 13 个任务；人员指派任务可装载成员业务视图 | 当前执行计划读取失败、任务 0/0、没有阶段轨 |

测试项目 `active_plan_version_id=NULL`，不是指向不存在计划的悬空引用。计划读取逻辑先授权，再因没有 EFFECTIVE 计划抛出 1014023010。前轮已用管理员和 z01010 复现相同错误。源页面以管理员会话访问，目标页面以 z01010 会话访问；本次浏览器证据证明实例和呈现差异，不单独证明两账号的权限完全等价。

扩大按业务编码匹配：源库有 204 个 ACTIVE、MIGRATION 且已有生效计划的项目，其中 **181 个在测试库可唯一匹配，181 个均为 ACTIVE 且无计划**；另 23 个来源编码在测试库没有精确匹配，没有猜测或创建对应项目。完整映射见 `same-code-plan-coverage.json`。

目标共有 6956 个 ACTIVE 迁移项目没有生效计划；这只是待核范围，不能推断全部应立即初始化。已结束迁移项目没有计划的历史原样保留，不能为追平来源而批量重开或覆盖历史。

### 2. 源工作目录中的修复未完全纳入此前提交级比较

源分支为 `codex/domain-migration@4d149f34faec4b66e93373794693d6bbd738b716`，但源工作目录 E:/AICoding/Projects/NPDMS 当前还有 69 项未提交变更。测试工作树还有 84 项现有变更，均保留。

扩展至项目、工程、平台、资产、商务、客户、BPM 及前端依赖图后：

- 来源提交树与测试工作文件：7261 相同、361 改变、611 目标新增、2 来源缺失。
- 来源**实际工作目录**与测试工作文件：7228 相同、394 改变、611 目标新增、8 来源缺失；其中 67 个 Vue 文件不同。
- 以上是范围内文件级清单，不代表 394 个差异均为缺陷或已逐行完成语义评审。以下为沿本次链路已具体确认的差异。

| 位置 | 来源实际处理 | 测试当前处理 / 影响 | 证据级别 |
|---|---|---|---|
| ProjectApprovalDrawer.vue、项目详情 index.vue | 抽屉 closed 事件触发外部业务刷新 | 缺 closed 事件和监听；审批抽屉关闭后项目摘要可能继续显示旧状态 | 源码调用链确认；未提交审批以触发变化 |
| ProjectNormalClosurePanel.vue、ProjectFlowPanel.vue、项目详情 index.vue | 暴露 refresh，父级与嵌入视图都能重读闭环状态 | 缺暴露和对应回读调用 | 源码调用链确认 |
| ProjectFlowPanel.vue 的 handleEmbeddedBusinessChanged | 用户信息录入入口等待 DONE/CLOSED/CANCELLED，不因普通版本增长提前停止 | 一旦 task.version 变化就可能停止等待，任务实际完成事件尚未落定时显示旧状态 | 源码条件差异；未执行业务保存实验 |
| TaskStateActions.vue | 保留字符串错误正文，完成条件不满足时提供持续说明 | 字符串异常丢为通用提示，完成条件提示较短且未设置持续 error | 源码确认 |
| BusinessViewHost.vue | 前置判定和阻塞原因有中文说明；已关联割接对象的新建入口给出针对性解释 | 直接显示原始 outcome / reason；可能把入口不适用误读成账号权限异常 | 源码确认，不等于服务端授权失效 |
| StageGateResultsPanel.vue 与父级参数 | 结束项目标识 historical，区分当前规则诊断与历史结果，解释 PROJECT_NOT_ACTIVE | 缺该标识与说明，历史项目可能显示令人误解的当前诊断 | 源码确认；不代表已绕过服务端状态校验 |

源代码直接定位：`E:/AICoding/Projects/NPDMS/yudao-ui/yudao-ui-admin-vue3/src/components/BusinessView/BusinessViewHost.vue:56`、`.../project-master-detail/components/ProjectFlowPanel.vue:688`、`.../ProjectApprovalDrawer.vue:2`、`.../ProjectNormalClosurePanel.vue:169`。目标对应位置没有上述完整修复。目标新增的产品清单、实施范围、设备商务上下文、路由 ID 修复和懒加载必须保留，不能用源页面整文件覆盖。

### 3. 割接审批就绪重验存在实际运行包差异

来源当前运行后端为 `E:/AICoding/Projects/NPDMS/.run/dac-route-server.jar`，SHA256 `789fbeb37395dcf01d3d38e07e6a7d0630916951a69932db067a6cf078219634`；测试运行包 SHA256 为 `f6844f4a337de46120f8ed1b0d47c02f01a3d15c86fb8b14005656fa365ec901`。

直接检查两包的 ProjectBusinessContextApiImpl.class：来源包含 `lockCutoverReadinessRequirements`，目标不包含。来源的专用链路以当前真实审批资格、冻结快照、设备范围、租户和项目树版本重验，不把审批资格转为通用项目编辑权限。

目标工作树缺少三个来源生产文件：

1. CutoverApprovalReadinessAccessApi.java。
2. CutoverApprovalReadinessAccessApiImpl.java。
3. CutoverApprovalReadinessResult.java。

相关 ImplementationReadinessApi/Service、CutoverOwnerAdapters、CutoverApprovalApplicationService、ProjectBusinessContextApi 等也不同。这个缺口不能通过补菜单或扩大服务经理权限替代。未实际办理割接审批，本轮证明运行包能力差异，没有声称所有审批场景已完成失败复现。

八个来源缺失文件中，另外三个是测试文件；V515/V516 为前轮明确排除的组织维护/独立施工计划菜单迁移，不因出现在差异清单而自动判定应迁入。

### 4. 配置、冻结快照和源自身问题必须分别看

- 五个正式模板及 51 处视图快照此前已完成语义和引用检查，其结果仍适用；本轮没有修改配置。目标 84573 的有效计划拥有完整视图快照。
- 目标 84572 的 v1～v3 为 SUPERSEDED 历史，存在旧快照缺口；v4 的人员指派旧结束节点也缺旧快照。前轮修复已保留历史且修复后续未结束节点，不能把这些历史记录当作新缺项覆盖。
- 来源当前受控组件目录 17 个、目标 19 个；**17 个共同组件描述逐字段相等**，目标另有归档文档、交付件检查两个新增组件。并不是目标整体缺业务视图组件。
- 来源数据库有 12 个旧式 DIRECT_BUSINESS 注册不在当前可配置组件目录中，源计划仍有 79 处引用，其中 66 处属于 EFFECTIVE 计划。这个来源遗留风险不能直接迁入目标当作修复；目标没有这些不兼容注册/引用。按当前目录未提供登记，不等于本轮已逐个打开全部 66 处并复现运行失败。
- 对一个来源已发布旧注册尝试发布预检接口，返回“业务视图状态不允许此操作”；预检仅用于草稿，故该结果不作为组件不可用的运行证明，也未改变记录。

### 5. 数据库结构差异以兼容性分类

项目、平台、方案、工程、割接表的列结构比较发现 8 张表有差异：

- 目标 installation.install_location 扩至 1024，来源为 255；保留目标增强。
- 目标割接任务/方案新增 legacy_* 来源解释字段；保留迁移证据。
- 目标两个产品关系表的 project_id 可空，来源不可空；没有直接收紧目标约束或回填未知项目关系。
- 三张来源旧表 proj_legacy_detail_record、proj_legacy_detail_snapshot、plt_business_field_configuration 目标没有；当前两工作目录 Java/XML 未找到消费者，未补建无消费的旧表。

索引比较得到 22 个来源定义未出现在目标：21 个来自来源独有旧表或无当前代码消费者的保修事实表，另一个是 com_sales_order_line 的旧唯一键 `(tenant_id,order_id,line_no)`。没有直接加旧唯一约束，以免破坏目标现有迁移数据；本轮未证明该唯一键差异导致当前页面故障。此前补建的订单查询索引保留。

## 建议处理顺序与边界

1. **先处理 181 个同码项目的实例初始化差异**：根据目标当前业务事实、已发布模板和授权边界准备正常初始化/迁移方案；单独处理 23 个未匹配编码。不是复制源主键或直接写生命周期。当前仅形成清单，没有执行。
2. **单列需要代码授权的修复**：割接专用审批就绪重验链、审批后刷新链、任务状态等待回读、历史门禁与业务操作说明。与目标增强逐项合并，不能整文件覆盖。用户当前禁止改业务代码，本轮未执行。
3. **保持历史和源问题边界**：不重写 SUPERSEDED 快照、已结束轮次、审批、审计或来源证据；不迁入旧式不兼容注册，不把源自身问题当作目标缺项。

## 证据与验证范围

本轮新增证据：来源实际工作树差异、两端真实页面及只读 API、同码项目映射、运行包类文件、列与索引元数据、计划冻结引用。未执行写入业务数据的审批、完成、重开、初始化或保存操作；未运行新编译/单测，也未用旧测试结果冒充本轮代码差异已验证。

报告附件：`deep-comparison-summary.json`、`same-code-plan-coverage.json`。完整脚本输出和原始证据位于 `.run/project-delivery-recheck-20261024/deep-comparison/`，包括 working-tree-differences.json、runtime-artifact-comparison.json、data-audit.json、schema-differences.json、same-business-project-api.json、view-registry-reference-audit.json、index-differences.json，以及 source/target-project-detail.png 和来源任务业务视图 DOM。
