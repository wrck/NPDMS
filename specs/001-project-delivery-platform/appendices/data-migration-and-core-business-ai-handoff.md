# 数据迁移与核心业务AI实施交接方案

> 文档用途：任何没有当前会话上下文的AI或开发人员，都能仅依赖本仓库的规格和结构化证据，继续完成目标数据库、迁移程序、核心业务服务及验收。
>
> 当前状态：实施交接基线；只允许开展未被第13节阻断的迁移框架、前向DDL、转换器、领域服务和测试，**不代表允许执行生产迁移**。
>
> 基线日期：2026-08-06。

## 1. 接手AI执行契约

接手者必须按以下顺序工作，不得跳过证据核验直接编码：

1. 读取本文件，确认本次只承接一个实施工作包。
2. 读取第2节列出的强制输入，并核对DDL哈希、矩阵校验和未决事项。
3. 检查当前目录是否为实现仓库。本仓库若只有`specs/docs/tasks/需求`，它是规格仓库；工作包清单必须填写`implementationRepo`、分支/工作树、Git提交和构建入口。路径未知时停止代码实现，不能自行搜索后猜测，也不能把Java、Vue或正式数据库迁移代码写入规格目录。
4. 在实施前输出：工作包ID、引用的规则、将修改的模块、数据库变化、测试和回退方式。
5. 只使用已确认规则。证据不足时写迁移问题或标记`【待确认】`，不得按字段名、编号后缀或相似名称猜测。
6. 每次提交只完成一个可独立验证的纵向切片；先扩展、再迁移、后切换，删除或改名必须单独进入后续收缩版本。
7. 完成后提交机器可复核证据：测试结果、对账结果、DDL/映射哈希、问题分类和未完成项。

规则标签：

- `【已确认】`：可以直接实现，不允许自行改变语义。
- `【证据规则】`：由当前旧库统计或数据元证明；源数据更新后必须重新画像。
- `【建议实现】`：推荐的技术落地方式，可以在保持契约的前提下调整。
- `【待确认】`：可以建设框架和校验，但不能生成生产业务结论或越过切换门禁。
- `【禁止】`：任何实现不得执行。

### 1.1 仓库定位

| 用途 | 当前核验位置 | 分支/提交 | 使用规则 |
|---|---|---|---|
| 规格与证据 | 本文件所在仓库工作树 | 当前为detached `bf87819829ca450eb7ddc7ce47590c5ad0c7e0b9`且有未提交规格变更 | 只维护规格、证据生成器和评审制品，不写业务实现 |
| 实现候选工作树 | `D:\开发资料\PMS资料\项目交付平台-worktrees\implement-cp-foundation` | `implement/cp-foundation` / `ff47f2c32223d053efafb8b2f8d8bfcfffacd0ed` | 写代码前重新运行`git worktree list --porcelain`核验；必须先纳入经批准的本规格发布清单，不能假定当前候选提交已包含本文 |

`AI-MIG-000`可先在规格工作树生成只读漂移报告；任何脚本落库、前向DDL或业务代码必须在受控目标分支执行。候选工作树不存在、提交变化或尚未包含批准规格时，记录为阻断，不自行创建另一个实现位置。

## 2. 唯一事实来源与优先级

### 2.1 强制读取顺序

| 顺序 | 文件 | 用途 |
|---:|---|---|
| 1 | 本文件 | 实现顺序、边界、工作包和交付格式 |
| 2 | [`../00-master-spec.md`](../00-master-spec.md) | 平台范围、技术基线和全局决策 |
| 3 | [`docs/decisions/0001`](../../../docs/decisions/0001-project-order-line-scope-model.md) | 项目—订单行实施主链及禁止推断 |
| 4 | [`docs/decisions/0002`](../../../docs/decisions/0002-platform-identity-and-project-scoped-access.md) | 公司、部门、账号及项目授权边界 |
| 5 | [`docs/decisions/0003`](../../../docs/decisions/0003-contract-scoped-secondary-sn-cache.md) | 合同维度附加SN关系和当前缓存 |
| 6 | [`business-domain-table-design.md`](business-domain-table-design.md) | 领域权威表、发生时引用、读模型和查询路径 |
| 7 | [`project-order-physical-schema.mysql.sql`](project-order-physical-schema.mysql.sql) | 目标MySQL物理结构；字段、索引和约束以此为准 |
| 8 | [`project-order-migration-mapping.md`](project-order-migration-mapping.md) | 逐表转换、问题分类、对账、切换和回退 |
| 9 | [`complete-field-migration-matrix.md`](complete-field-migration-matrix.md) | 全字段处置口径及自动阻断规则 |
| 10 | [`../evidence/migration/README.md`](../evidence/migration/README.md)及同目录JSON/JSONL | 可编程读取的字段映射和目标字段目录 |
| 11 | [`../evidence/data-elements/README.md`](../evidence/data-elements/README.md)及同目录JSON/JSONL | Excel数据元的机器可读证据，包含隐藏列 |
| 12 | [`platform-identity-access-migration.md`](platform-identity-access-migration.md) | 两套旧权限、外部账号和字段权限迁移 |
| 13 | [`module-boundary-and-naming.md`](module-boundary-and-naming.md) | 模块所有权、依赖和契约规范 |
| 14 | [`state-machines.md`](state-machines.md) | 项目、任务及专业流程状态 |

发生冲突时按以下规则处理：

1. 当前DDL与目标字段目录不一致：停止实现，先修复DDL和矩阵并重新校验。
2. 数据元与当前旧库结构冲突：物理结构以最终只读抽取为准，业务语义提交评审。
3. 旧代码行为与已确认业务规则冲突：保留旧行为证据，不自动复制错误行为。
4. ADR状态、规格结论和实施任务不一致：停止受影响工作包，要求架构负责人明确状态。

### 2.2 当前机器基线及漂移状态

| 项目 | 当前值 |
|---|---:|
| 数据元Excel SHA-256 | `4250DD8D53C5C312B8C5141A0F626EF80079224D6E2D477978DB697DD85A0116` |
| 当前目标DDL SHA-256 | `5EB9742F84CEF070D79A4DCEC3BB0199ABEBB30B4D9C84F94937F81510EE4249` |
| 现有证据记录的DDL SHA-256 | `2B206992BA5580E776060F9D4ED177A7BD8C34DB614FD65EC9560DAF38F8BF33` |
| 当前核心迁移表/字段 | 60表 / 1240字段；不是平台全量模型 |
| 当前约束/表选项 | 447 / 60；其中同域外键48、CHECK 89 |
| 核心旧表/字段 | 18表 / 326字段，旧证据记录未映射0 |
| 全部物理证据 | 3931行 / 3908个唯一旧字段（已按当前DDL重建目标引用） |
| 语义证据 | 197行 / 108个唯一数据元 |
| 迁移校验 | 现有`migration-validation.json.passed=true`已过期，不代表当前工作树通过 |

`【P0阻断】`当前DDL、目标字段目录、完整矩阵、摘要和校验结果已使用同一当前哈希。ADR-0028已接受Q07、Q08、V1.7及Q09～Q14九组完整清单，逐项寄存器当前994项`ACCEPT_CURRENT`、889项`AMEND_CURRENT`、0项`DEFER`；完整确认入口为`p3-e09-confirmation-packet.md`。模型候选尚待fresh independent review对候选制品整体一致性给出`GO`；它不要求四角色签署或非空`approvedDdlSha256`，该哈希仅属未来历史迁移门禁。任何后续AI必须继续完成`AI-MIG-000`独立复核；在此之前不得把需求方接受、历史“52表DDL已验证”或MySQL执行PASS升级为当前60表核心子集放行证据，也不得把该子集冒充平台全量模型或开始生产迁移。Q08性能结论仍须由Feature查询计划和P3-E06压测形成。

当前唯一可启动的迁移工作包是`AI-MIG-000`。首先在仓库根目录复核漂移：

```powershell
(Get-FileHash -Algorithm SHA256 -LiteralPath 'specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql').Hash
Get-Content 'specs/001-project-delivery-platform/evidence/migration/target-field-catalog-summary.json' -Raw | ConvertFrom-Json | Select-Object ddlSha256
```

两者不一致即保持阻断；不要手工把JSON中的哈希改成当前值冒充重建。

### 2.3 发布清单

`AI-MIG-000`必须生成并由CI校验`migration-release-manifest.json`，至少固定以下内容：

- `releaseId`、生成时间、生成器版本和锁定运行时版本；
- Excel、经审批DDL、源结构目录、目标字段目录、全部矩阵、规则配置、敏感字段策略和验证报告的相对路径及SHA-256；
- 旧库画像的只读水位、表数量和结构哈希；
- 未决决策ID及状态；
- `decisionRegister`和`approvalEvidence`的仓库相对路径、文件哈希和机器可校验状态；
- 当前Git提交和工作树是否干净；
- 生成命令、验证命令和结果。

清单只引用仓库相对路径，不保存数据库密码和本机绝对路径。清单中的任一输入变化都必须生成新`releaseId`，旧批次继续引用旧清单。

### 2.4 DDL漂移裁决

本次`AI-MIG-000`事实核对记录在[`evidence/migration/ddl-drift-review.md`](../evidence/migration/ddl-drift-review.md)；该报告当前为`DEFER`，不代表已批准当前DDL。

当前DDL不是天然权威，旧矩阵也不是天然权威。`AI-MIG-000`必须先生成`ddl-drift-review.json`，逐项列出表、列、索引、外键、CHECK和注释的旧证据值、当前DDL值、来源提交及差异。每项只能由数据架构和业务负责人选择：

- `ACCEPT_CURRENT`：确认当前DDL变化正确；
- `RESTORE_APPROVED_BASELINE`：当前变化错误，使用已批准基线；
- `AMEND_CURRENT`：先按批准意见修改DDL，再复核；
- `DEFER`：保持阻断，不生成新发布基线。

全部差异有有效审批后，最终文件哈希才登记为`approvedDdlSha256`，随后重建目录、矩阵和校验报告。禁止只修改摘要哈希，也禁止先重建为自洽结果再倒推DDL正确。

## 3. 安全与数据库边界

### 3.1 不可违反的操作约束

- `【禁止】`对旧`localhost:3306/dppms`执行DDL、DML、临时表、锁表或迁移标记写入。
- `【禁止】`在SQL中跨旧库和新库查询、关联、插入或更新。
- `【禁止】`把旧库账号、密码、连接串或抽取文件中的敏感值提交到Git。
- `【禁止】`修改已经执行的Flyway/Liquibase迁移；必须新增前向版本。
- `【禁止】`在公司未知时仅按合同号创建正式合同。
- `【禁止】`把待映射、待数量确认或冲突记录计入有效实施、完成率、交付量和验收。
- `【禁止】`仅凭`-L`、`-his`等后缀建立合同、订单或项目血缘。
- `【禁止】`因SN重复删除发货、RMA、返还或再次发放事件。

### 3.2 数据移动方式

旧库与新库必须物理分离：

```text
旧dppms只读连接
  -> 不可变抽取文件
  -> 文件清单、行数、SHA-256
  -> 新库pms_migration_source_record
  -> 目标领域批量导入接口
  -> 对账、问题处理、切换
```

`【建议实现】`每个源表输出UTF-8 NDJSON或CSV压缩文件；清单至少包含来源系统、表名、抽取SQL版本、列顺序、行数、最小/最大主键、文件大小、SHA-256、开始/结束时间和只读库结构哈希。导入只读取文件，不在导入阶段连接旧库。

业务数据一次性迁移；CRM、ERP、SAP、EHR等后续辅助关联数据只允许通过稳定接口或只读抽取增量同步。新平台业务结果不得反写旧库。

## 4. 目标模块与数据所有权

【已确认】首期采用JDK 25、Spring Boot 4.1.0、MyBatis Plus、Yudao模块化单体和独立MySQL 8.x数据库。跨模块只能调用`-api`契约或版本化事件，不能直接访问其他模块Mapper或业务表。

| 模块 | 拥有的数据与能力 | 迁移写入方式 |
|---|---|---|
| `yudao-module-system` | `system_company`、共享部门、用户、角色、菜单、公司—部门上下文、字段权限基础 | 平台批量迁移应用服务；授权逻辑表的物理DDL仍受第13节阻断 |
| `pms-module-project` | 客户、项目树、项目组合、参与方、成员、合同、订单、订单行、项目订单行实施范围 | `pms-module-project-api`内部批量命令 |
| `pms-module-asset` | 发货合同归属、装箱单、SN、设备事件、SN关系、项目设备归属 | `pms-module-asset-api`内部批量命令 |
| `pms-module-integration` | 抽取清单、同步批次、逐源行原值、外部键、迁移问题、游标、重试和对账 | 本模块事务；不得直接更新其他模块业务表 |
| `pms-module-analytics` | `pms_project_delivery_summary`等可重建读模型 | 只读消费业务事实或事件后重建 |

`【建议实现】`高数据量导入使用同进程批量应用接口，不逐行HTTP调用。集成模块负责读取批次和编排，目标模块在自己的事务中使用MyBatis/JDBC批处理；批大小可配置并通过锁等待、redo和吞吐实测确定。跨模块返回逐行成功、问题和重试信息，不能只返回整批成功或失败。

## 5. 核心业务主链和不变量

### 5.1 项目、合同、订单和实施范围

```text
项目树节点
  -> pms_project_order_line_scope
  -> pms_sales_order_line
  -> pms_sales_order
  -> pms_order_contract_rel
  -> pms_contract
```

必须同时满足：

1. 项目与合同为N:N；业务上一个项目可有多个合同。
2. 合同与订单为N:N；订单头不得固化唯一合同外键。
3. 销售订单与订单行为1:N。
4. 实施最小权威范围是项目节点到ERP订单行的分配关系，不是CRM执行单。
5. 同一订单行可以按数量分配给多个正式子项目；正式子项目有独立负责人、计划、状态和验收。
6. `ACTIVE`实施范围必须有项目、订单行和`allocated_qty`。
7. 无法唯一解析订单行时只写迁移问题；`PENDING_MAPPING`是问题分类，不是`pms_project_order_line_scope`状态，并且不得创建正式范围行。
8. 已唯一解析订单行但缺分配量时可建立`PENDING_QUANTITY`范围；该范围不得进入实施量、完成率和交付统计。
9. 项目组合、项目父子树和扩容/续采/改造关系是三种不同关系，不得混用。
10. 旧项目迁移默认成为独立根节点；没有明确父子证据时，不得用旧项目组、名称、地区或编号推断正式项目树。

### 5.2 CRM执行单和特殊合并

- CRM执行单及配置只作辅助关联证据，缺少CRM配置不能阻断ERP订单行实施。
- 一个CRM项目改单后可以产生新执行单、新合同或沿用原合同，并生成新ERP订单；新旧订单通过明确证据写`pms_order_change_rel`。
- 多个CRM项目的执行单可以合并生成一个合同和ERP订单；主执行单只是下单主记录，不决定全部订单行的项目归属。
- 特殊合并实施时必须依据执行单合并成员和订单配置行归属，把ERP订单行或数量分配到各自项目。
- 是否安服只能由已取得的安服产品配置提供正向证据；没有配置时为`UNKNOWN`，不能推断为非安服。

### 5.3 合同主档与回款

- 正式合同唯一键为`tenant_id + company_code + contract_no`。
- `sms_ofst_contract_head_sap`是合同回款依据；逐源行进入`pms_contract_receivable`，不等同于完整合同主档。
- `fb_contract`只是发货记录的合同归属，进入`pms_shipment_contract_ref`，不能生成合同主档。
- 公司无法唯一解析时保留原值并生成问题，不创建伪造公司合同。

### 5.4 发货、SN和设备生命周期

```text
发货合同归属 -> 装箱单 -> 设备生命周期事件 -> SN主档
                                     -> 项目设备归属
```

- ERP订单行的`delivered_qty`是ERP数量事实；实际设备通过SN事件跟踪，两种口径不强制相等。
- 同一SN可以多次发放、RMA退回、借用返还和再次发放；每条`fb_shipment_barcode`保留为事件。
- `rma_no`逐字保存；正式动作码未确认前使用`UNCLASSIFIED`，不能靠`isRMA`推断。
- `fb_shipment_barcode_relation`保存合同维度主SN—附加SN关系，是权威历史。
- `pms_device_sn.secondary_sn/secondary_item`只缓存最新发货合同下的当前关系，可重建、不可直接手改。
- SN项目当前归属必须由完整转移链确定；多项目冲突保持待确认。

### 5.5 公司、部门、发生时引用和权限

- 公司是业务主体，目标字段统一使用`company_*`；部门使用`department_*`。
- 部门编码全平台共享，不能从部门反推公司；同一业务上下文中的公司和部门必须保存在同一关系行。
- 高频ID、编码和名称按业务发生时保存；主档同步不自动回写，当前值需要显式查询，受控回归刷新必须审计和对账。
- 系统用户与EHR员工目录分离；内部账号保存工号，外部账号不伪造工号。
- 外部人员只能查看明确转派的项目节点；权限是有效转派、菜单、操作、字段、数据范围和对象条件的交集，前端隐藏不能代替服务端校验。

## 6. 必须通过的业务场景

| 场景 | 最小实现结果 | 失败时处理 |
|---|---|---|
| 普通项目首次下单 | 项目、合同、订单、订单行、实施范围完整贯通 | 任一外键多义进入问题，不猜测 |
| 执行单改单 | 保留新旧执行单、合同选择和新订单；建立明确改单血缘 | 证据不足只保留来源关系 |
| 多项目执行单合并下单 | 合并批次可含多个成员；订单行按来源配置归属不同项目 | 配置不全不伪造范围 |
| 大型项目按地区/局点拆分 | 创建正式子项目；订单行数量分别分配，负责人和验收独立 | 数量不完整为`PENDING_QUANTITY` |
| 订单取消、退货、替代和新增 | 订单关系有类型和证据；原订单行、退货行、新增行均保留 | 不按编号相似度自动关联 |
| 多合同订单 | 同一订单存在多条有效合同关系 | 不覆盖成单合同字段 |
| SN退回再发 | 同一SN保留完整事件序列并确定唯一当前项目归属 | 时间链冲突进入问题 |
| 合同维度附加SN变化 | 历史关系按合同保留，主档缓存匹配最新发货合同 | 缓存差异通过重建修复 |
| 外部人员实施 | 仅能访问转派的项目、菜单、动作和字段 | 默认拒绝并记录安全审计 |

## 7. 迁移执行阶段

| 阶段 | 输入 | 主要输出 | 完成门禁 |
|---|---|---|---|
| M0 基线冻结 | 规格、DDL、矩阵、旧库结构 | 批次版本、哈希清单、未决项清单 | DDL和矩阵校验通过；未决项有责任人 |
| M1 Expand | 评审DDL、现有目标库 | 新表、新可空列、新索引、DO/Mapper | 空库和升级库均可执行；旧代码仍可运行 |
| M2 只读抽取 | 旧库只读连接 | 不可变文件及manifest | 行数、列序、文件哈希和结构哈希完整 |
| M3 原值落地 | 抽取文件 | `pms_sync_batch`、`pms_migration_source_record` | 来源读取数等于逐源行记录数 |
| M4 基础主档 | 公司、部门、账号、客户、产品、旧项目 | 主档、外部键、问题 | 重复和多义均有问题；无静默丢失 |
| M5 合同与订单 | 回款、项目合同桥、ERP头行、CRM辅助 | 合同、订单、订单行及关系 | 业务键唯一；每个源头行有映射或问题 |
| M6 实施范围 | `pm_project_product_line`及目标订单行 | 项目订单行范围 | `ACTIVE`范围完整；数量守恒；待处理不统计 |
| M7 发货与设备 | 发货合同、装箱单、条码、关系、项目SN | SN、事件、关系、项目归属 | 事件不丢失；当前归属和缓存可重建 |
| M8 权限 | `t_*`、`fnd_*`、EHR映射 | 用户权限、外部转派、字段规则 | 内外部账号分流；越权测试通过 |
| M9 读模型 | 已完成业务事实 | 项目交付汇总 | 可全量重建；与明细对账；失败不发布半批 |
| M10 演练与切换 | 最终一致性抽取 | 签署的对账包、切换/回退记录 | 所有阻断问题关闭或经批准接受 |
| M11 Contract | 使用监控、兼容期结果 | 停写旧兼容字段、独立删除迁移 | 零使用证据；删除单独版本；回退已测试 |

阶段只能前向推进；M2至M10重跑必须创建新`batch_no`，不能覆盖旧批次证据。

### 7.1 迁移运行、阶段和对象批次

一次迁移不是单个`pms_sync_batch`。实现必须明确三层标识：

```text
migration_run       一次完整演练或正式切换
  -> stage_run      M0至M11中的一个阶段及重试
      -> sync_batch 一个source_system + object_type + 分片范围
```

`【建议实现】`以前向DDL增加`pms_migration_run`和`pms_migration_stage_run`，或提供等价、持久化且可查询的运行清单；不得只在日志中保存运行关系。`migration_run`至少记录运行类型、目标库标识、基线版本、状态、开始/结束时间和批准信息；`stage_run`至少记录阶段码、前置阶段、尝试次数、状态、输入/输出manifest和错误摘要。正式状态和值域须先进入权威字典和状态转换测试。

### 7.2 一次性迁移与切换后同步分离

一次性业务迁移和只读关联同步必须使用不同的作业类型、配置、批次和对账报表：

| 协议 | 写入方向 | 对象 | 游标与删除语义 |
|---|---|---|---|
| `ONE_TIME_MIGRATION` | 不可变抽取文件 -> 新库 | 全量历史业务和原值证据 | 最终一致性快照；失败不修改旧库 |
| `READ_ONLY_REFERENCE_SYNC` | CRM/ERP/SAP/EHR只读源 -> 新库辅助数据 | 仅评审通过的关联和主档对象 | 每对象定义业务键、更新时间/水位、取消/删除语义、频率、延迟SLO、重试和全量对账 |

无可靠更新时间或递增键的表不得伪造增量游标，应采用可控周期的全量哈希比对。同步仍使用两个连接池，禁止跨库SQL，新平台业务状态不得反写来源系统。

## 8. 迁移程序实现契约

### 8.1 批次和逐源行状态

`【建议实现】`批次状态采用：

```text
CREATED -> EXTRACTED -> LOADING -> RECONCILING -> READY_TO_CUTOVER -> COMPLETED
                   \-> FAILED                         \-> REJECTED
```

逐源行至少区分`EXTRACTED/MAPPED/PARTIAL/ISSUE/EXCLUDED`。问题状态至少区分`OPEN/RESOLVED/ACCEPTED`；`RESOLVED`必须记录解决人、时间和动作，`ACCEPTED`必须记录审批依据且仍计入已接受风险。

### 8.2 幂等算法

```text
for each source row:
  canonicalPayload = 按源列顺序规范化的完整JSON
  checksum = SHA-256(UTF-8(canonicalPayload))
  insert migration_source_record if absent
  if same batch + source key + same checksum exists:
      reuse existing result
  if same batch + source key + different checksum exists:
      create SOURCE_ROW_CHANGED and stop this row
  transform through the matching matrix rule
  resolve every single-target relation as 0 / 1 / N matches
  if a single-target relation has 1 match:
      continue transformation
  else:
      write migration issue with raw key and candidate IDs
      stop this row
  if this row carries explicit project allocation evidence:
      build ResolvedAllocations and validate the allocation total atomically
  call owning domain batch API and write external key map
  update mapped_target_count and mapping_status in the same integration transaction
```

没有稳定主键的表，使用“完整行规范化SHA-256 + 同哈希行在不可变抽取文件中的序号”作为批次内`source_pk`。不得依赖无`ORDER BY`的数据库返回顺序。

### 8.3 唯一关联解析与分配解析

项目、订单行、合同、公司等单目标关联解析器必须返回结构化结果，禁止以`null`同时表示未命中和冲突：

```text
Resolved(targetId, evidence)
NotFound(issueType, rawBusinessKey)
Ambiguous(issueType, candidateTargetIds, rawBusinessKey)
Rejected(issueType, violatedRule)
```

上述`Ambiguous`中的N命中表示同一业务键存在多个候选目标，是冲突；不得把它用于表达一个订单行合法分配给多个项目。

项目订单行数量拆分使用独立结果：

```text
ResolvedAllocations([
  {projectId, orderLineId, allocatedQty, sourceEvidence}
])
AllocationPendingQuantity(projectId, orderLineId, sourceEvidence)
AllocationRejected(issueType, violatedRule, sourceEvidence)
```

只有每个`orderLineId`和`projectId`均唯一解析后才能生成分配集合；集合可含多个项目，必须在同一并发门禁中校验数量合计。缺项目或订单行唯一映射时只生成迁移问题，不产生分配。

至少实现并测试以下问题码：

`DUPLICATE_PROJECT_CODE`、`CONTRACT_COMPANY_UNKNOWN`、`CONTRACT_COMPANY_CONFLICT`、`RECEIVABLE_MASTER_MISSING`、`PROJECT_CONTRACT_ORPHAN`、`ORDER_HEADER_CONFLICT`、`DUPLICATE_SCOPE`、`CRM_EXECUTION_CONFLICT`、`SHIPMENT_CONTRACT_REF_NOT_FOUND`、`SN_SOURCE_NOT_FOUND`、`SN_MULTI_PROJECT_CONFLICT`、`SOURCE_ROW_CHANGED`。

### 8.4 事务与失败

- 抽取文件不可修改；转换失败只改变新库批次、来源记录和问题状态。
- 单个批次按对象和分片提交，不能用一个超大事务覆盖百万级事件。
- 每个分片具备确定性范围、重试次数、处理计数和耗时。
- 目标域写入成功但外部键写入失败时，依靠目标业务键和来源键幂等恢复，不能重复创建业务记录。
- 读模型和设备当前缓存在明细全部成功后发布；中途失败保留旧版本。

### 8.5 可执行映射规则

Markdown和现有JSONL矩阵是审计说明，不是完整转换程序。实现仓库必须提供版本化机器契约；每条转换至少包含：

```json
{
  "mappingId": "PROJECT.CODE.V1",
  "ruleVersion": 1,
  "sourceTable": "pm_project",
  "inputFields": ["projectCode"],
  "targetBindings": ["pms_project.project_code"],
  "transformRuleId": "TRIM_TO_NULL",
  "nullPolicy": "ISSUE",
  "lookupKey": null,
  "onZero": "ISSUE",
  "onMany": "ISSUE",
  "dictionaryCode": null,
  "precision": null,
  "timezone": "Asia/Shanghai"
}
```

代码只能调用已登记的`transformRuleId`，不得解析自由文本`transform`来决定生产行为。规则变更必须提升版本，旧批次继续引用原版本。

### 8.6 行校验和规范

`canonicalizationVersion=1`使用固定顺序的类型化JSON行，源列按抽取manifest列顺序排列。对象键顺序固定为`v,columns`，列对象键顺序固定为`name,type,isNull,value`；UTF-8无BOM，行内不追加换行。字符串保留源Unicode字符，不做trim或Unicode归一化；DECIMAL保留源小数位并使用非科学计数法；日期时间按manifest声明的源时区输出ISO-8601；二进制使用Base64；`null`与空字符串严格区分。JSON转义器、JDBC类型映射和测试向量必须随生成器锁定版本。

固定测试向量：

```text
{"v":1,"columns":[{"name":"id","type":"BIGINT","isNull":false,"value":"1"},{"name":"name","type":"VARCHAR","isNull":true,"value":null}]}
SHA-256 = 36D8F37E6D606D18EFAD0C0927414EEDD5DCFC5E108A58EA40561E2FF3E4E13C
```

行checksum只计算未压缩规范行；文件SHA-256计算最终落盘字节，压缩算法和版本进入manifest。

### 8.7 只读抽取文件协议

- 每张表固化显式列清单，禁止`SELECT *`；列顺序进入manifest。
- 优先在只读事务的一致性快照内抽取；不得使用`LOCK TABLES`或全局读锁。若存储引擎或抽取时长无法保证跨表一致，必须采用业务冻结、来源方快照或记录水位后补偿，不得假装全局一致。
- 有稳定键的表按主键或唯一组合键做键集分页并稳定`ORDER BY`；百万级表禁止`OFFSET`深分页。
- 无稳定键的表先按全部源列确定规范排序和行哈希，再赋批次内序号；重复行不得被去重。
- 输出采用UTF-8、LF、固定日期/时区、JSON `null`和十进制字符串规范；建议NDJSON+gzip，二进制值使用Base64。
- 文件名至少包含运行号、源系统、表名、分片起止键和序号；manifest记录SQL版本、结构哈希、行数、字节数、SHA-256和水位。
- 凭证只来自安全配置。受控生产迁移文件为保证无损迁移可保留完整源值，但必须加密存储、最小授权、记录访问审计并按保留期销毁；Git制品、日志、测试夹具、问题列表和报表必须按字段分级规则脱敏。字段分级清单需分别声明迁移存储方式和证据展示方式，不得靠通用字符串替换破坏迁移输入。

`fb_shipment_barcode`等百万级表必须支持断点续传：只重跑缺失或校验失败的确定性分片，不能覆盖已签名文件。

敏感字段策略必须固化为`evidence/migration/sensitive-field-policy.jsonl`并通过`evidence/migration/sensitive-field-policy.schema.json`校验。每行至少包含`policyVersion/sourceSystem/sourceTable/sourceField/classification/migrationStorage/evidenceDisplay/keyReference/retentionDays/ownerRole/approvalId`；`migrationStorage`至少区分`FULL_ENCRYPTED/FULL_ACCESS_CONTROLLED`，`evidenceDisplay`至少区分`NON_SENSITIVE/MASK/HIDE`；`keyReference`只保存密钥引用，不保存密钥。全部物理源字段必须恰好命中一条当前策略，未命中或重复命中均阻断`AI-MIG-004`。允许值及具体分类由安全负责人批准，发布清单必须固定策略文件和Schema哈希。

### 8.8 问题解决和补偿协议

问题解决动作只允许`KEEP`、`MERGE`、`REMAP`、`EXCLUDE`、`DEFER`。每次解决必须保存问题ID、动作、结构化修复payload、目标ID、解决人、审批人、依据、时间和新补偿批次号；不得修改旧批次原值或映射证据。`resolution_action`自由文本只能作为说明，不得作为执行指令。

### 8.9 回退边界

- 切换前每次全量演练写入全新、可丢弃的目标schema或独立数据库；失败时停止入口、保存证据并废弃该目标，不按业务表做反向删除。
- 若必须在共享目标库重跑，必须先交付逐表变更日志、依赖逆序、补偿算法和恢复演练；当前业务表没有统一`batch_id`，因此不能承诺通用按批次删除。
- 切换后只允许通过已批准入口回退应用流量；不得回写旧库，也不得假设数据库可以自动降级。任何收缩DDL都在稳定期后单独发布。

## 9. AI实施工作包

每个AI一次只承接一个工作包。开始前在实现仓库建立工作包清单，固定`id`、输入文件及哈希、规则版本、允许修改路径、前置状态、执行和验证命令、输出目录、失败处理、门禁及依赖；命令尚不存在时先在该工作包内创建并纳入版本控制，不能把“请运行相关脚本”作为交付。表中的“完成”均包含代码、自动化测试、文档和证据。

| ID | 工作包 | 依赖 | 完成定义 |
|---|---|---|---|
| AI-MIG-000 | 恢复可重复基线 | 无 | 先生成DDL逐项漂移报告并取得裁决；再将生成器、JSON Schema、锁定运行时和验证命令纳入受控目录，从批准DDL和原始结构化证据重建目录、矩阵、摘要和校验结果；生成`migration-release-manifest.json`且全部哈希一致、生成零差异 |
| AI-MIG-001 | 基线守卫与证据校验器 | 000 | CI校验DDL哈希、目标列、公共注释、禁止命名、矩阵目标和Excel manifest；任一漂移构建失败 |
| AI-MIG-002 | 前向DDL与领域DO | 001、迁移工具确认、物理模型评审 | 当前核心表及补齐的身份授权表按所有权拆成前向迁移；空库/升级库/重复启动测试通过；不修改已执行迁移 |
| AI-MIG-003 | 批次、原值、外部键和问题框架 | 002 | `pms_sync_batch`、`pms_migration_source_record`、`pms_external_key_map`、`pms_migration_issue`及运行/阶段清单的DO、服务、Mapper、唯一键、状态动作、幂等和问题查询可用 |
| AI-MIG-004 | 只读抽取器与文件manifest | 001 | 每个源表使用只读查询输出不可变文件；迁移原值受控保留，文件加密和访问隔离，日志/展示证据脱敏；行数和哈希可复核 |
| AI-MIG-005 | 公司、部门、账号和权限迁移 | 003、004、授权物理DDL通过评审 | 两套旧权限归并；外部账号保留；公司—部门上下文和项目转派门禁通过 |
| AI-MIG-006 | 客户、产品、项目树和项目关系 | 003、004 | 项目旧键、客户、公司—部门发生时值、根节点和非树关系可追溯；重复编码入问题 |
| AI-MIG-007 | 合同、回款和项目合同关系 | 006 | 公司+合同号唯一；回款、正式合同和发货合同归属不混用；未知公司入问题 |
| AI-MIG-008 | ERP订单、订单行、合同及执行单关系 | 007 | 组合业务键归并、冲突问题、N:N合同和执行单关系完整；负退货数量保留 |
| AI-MIG-009 | CRM执行单、配置、合并和改单血缘 | 006、008 | 普通/安服头归并；安服只正向确认；合并成员不限数量；不按后缀建血缘 |
| AI-MIG-010 | 项目订单行实施范围 | 006、008 | 0/1/N匹配、数量分配、并发防超配、未匹配只写问题、待数量范围及统计排除通过 |
| AI-MIG-011 | 发货、装箱单、SN和设备事件 | 007、008 | 全量事件保留；SN唯一；订单行仅唯一命中补链；RMA原值保留 |
| AI-MIG-012 | 设备关系、当前缓存和项目转移 | 010、011 | 合同维度关系权威；缓存可重建；跨项目SN当前归属唯一或入问题 |
| AI-MIG-013 | 汇总读模型与高频查询 | 010、012 | 项目列表、实施订单和SN下钻按既定索引；读模型可全量重建和原子发布 |
| AI-MIG-014 | 对账引擎与问题处置界面 | 005至013 | 行级、主档、关系、数量、权限和缓存对账；问题可分派、解决、接受和重跑 |
| AI-MIG-015 | 全量演练、性能、切换和回退 | 014、全部阻断决策关闭或批准接受 | 固定夹具、P95、冻结、最终抽取、签署及隔离目标废弃/入口回退演练证据完整 |
| AI-MIG-016 | 切换后只读关联同步 | 015、逐对象同步协议批准 | 同步对象、业务键、游标、取消/删除语义、SLO、重试、漂移告警和全量对账全部落地 |
| AI-MIG-017 | 兼容收缩与旧平台退役 | 015、016及稳定期 | 零使用证据；停止旧同步和兼容写；删除独立版本；旧平台保留期按批准计划结束 |

## 10. 核心业务服务实现顺序

迁移不能先于核心业务约束存在。业务服务按以下顺序建设：

1. `BIZ-001`：客户、公司、部门、用户引用和统一数据权限守卫。
2. `BIZ-002`：项目主档、父子树、非树关系、组合、成员、参与方和负责人动作。
3. `BIZ-003`：合同、订单、订单行、项目合同和订单合同关系查询。
4. `BIZ-004`：项目订单行实施范围创建、拆分、调量、失效和并发数量门禁。
5. `BIZ-005`：CRM辅助关系、特殊合并和订单变更血缘。
6. `BIZ-006`：发货合同归属、装箱单、SN事件、设备关系和项目转移。
7. `BIZ-007`：项目列表、实施订单、设备下钻、统计导出和读模型重建。
8. `BIZ-008`：外部人员转派、菜单/操作/字段权限和安全审计。

每个命令服务必须验证权限、前置状态、业务不变量、幂等键和乐观锁；状态变更只能通过命名动作，不允许Controller或Mapper直接改状态。

## 11. 测试与验收证据

### 11.1 自动化测试层次

| 层次 | 必测内容 |
|---|---|
| 单元测试 | 每个字段转换、枚举映射、校验和、0/1/N解析、数量规则和问题码 |
| MySQL集成测试 | 真实MySQL 8.x上的唯一键、复合租户外键、CHECK、批量幂等和并发防超配 |
| 契约测试 | 集成模块到项目/资产批量API的逐行结果、版本兼容和重复调用 |
| 迁移场景测试 | 第6节全部场景，含冲突、缺失、退货负数和重复SN事件 |
| 对账测试 | 人工构造丢行、多映射、数量不守恒、缓存错误时必须阻断 |
| 性能测试 | 当前数据规模下的批量吞吐、锁等待、项目列表、实施订单和SN下钻P95 |
| 安全测试 | 旧库写入尝试失败、跨租户外键失败、外部人员越权失败、敏感字段脱敏 |
| 浏览器验收 | 迁移问题处理、项目拆分、设备历史、外部转派及导出权限真实页面流程 |

建议验证命令由实现仓库最终固化；Maven模块存在后至少执行：

```powershell
mvn -pl pms-module-integration,pms-module-project,pms-module-asset -am test
mvn clean verify
```

不得用H2替代所有迁移约束测试；DDL和高风险SQL必须在目标MySQL 8.x小版本验证。

### 11.2 每批次证据目录

`【建议实现】`每次演练输出：

```text
evidence/implementation/migration/{batch_no}/
├── source-manifest.json
├── schema-drift.json
├── counts-by-table.json
├── mappings-by-target.json
├── issues-by-type.csv
├── quantity-reconciliation.json
├── relation-reconciliation.json
├── permission-reconciliation.json
├── cache-reconciliation.json
├── explain-analyze/
├── performance-summary.json
└── acceptance.md
```

### 11.3 固定业务夹具

以下最小夹具必须机器化并写明预期结果，不能只做人工抽样：

1. 数量10的订单行分配给两个正式子项目6和4，允许提交；再次分配1必须拒绝。
2. 已解析订单行但数量未知，建立`PENDING_QUANTITY`范围且所有实施统计为0；补量后通过命名动作转为`ACTIVE`。
3. 订单行0命中或N命中，生成问题且不生成范围行；`PENDING_MAPPING`不得写入范围状态。
4. CRM执行单或配置缺失，但ERP订单行完整，实施仍可创建；安服标志为`UNKNOWN`。
5. 多项目、多执行单合并下单，成员数不设固定上限；同一订单的不同行或数量正确归属不同项目。
6. 原订单取消、已执行部分退货、未执行行取消、新增改动行生成，显式血缘完整且没有编号后缀推断。
7. 同一SN经历发货、RMA退回、借用返还和再次发放，全部事件保留且当前归属唯一。
8. 同一主SN在不同合同有不同附加SN，历史关系不覆盖；主档缓存与最新发货合同关系一致。
9. 合同号存在但公司无法唯一解析，不创建正式合同并生成阻断问题。
10. 外部人员只获一个正式子项目，未授权兄弟节点、父节点敏感字段和菜单均被服务端拒绝；撤销后立即失效。
11. 读模型重建中途失败不发布半批；成功批次原子切换且可从明细完全重建。
12. 相同抽取文件重跑结果幂等；单行内容变化产生`SOURCE_ROW_CHANGED`，旧证据不修改。

数量门禁的基础公式为：同一订单行所有`ACTIVE`范围的`allocated_qty`之和不得超过已批准的可分配基数；可分配基数、单位、小数位和负退货口径由`BLK-006`确定后写入版本化规则配置。

## 12. 完成和切换门禁

满足以下全部条件才可标记“可切换”：

- 每张源表`source_read_count = migration_source_record_count`。
- 每条来源记录恰好属于已完成、待处理或批准排除之一，且`source_read_count = completed_count + pending_count + approved_excluded_count`；三类互斥。
- 按当前DDL重建后的核心、物理和语义矩阵全部通过，无无效目标或无处置来源字段。
- 项目、合同、订单和SN业务键无未解决阻断重复。
- `ACTIVE`实施范围数量完整，跨项目分配满足批准的计量规则。
- 未解析合同公司、项目合同、订单行范围和SN当前归属均有问题或批准凭证。
- ERP数量、SN事件数量和项目设备归属分别对账，不混用口径。
- 设备当前附加SN缓存与权威关系零差异。
- 外部用户不能访问未转派项目；字段、菜单和操作权限服务端生效。
- 目标查询达到已定义P95，并保存`EXPLAIN ANALYZE`和数据规模。
- 至少完成两次全流程演练；最后一次使用正式候选版本和同量级数据，并完整验证切换与回退。
- 业务、数据、研发、测试和运维负责人签署问题接受清单。

## 13. 当前阻断项

以下事项必须进入决策登记表。AI可以实施不依赖该决策的框架和非生产测试，但不得自行选值或越过所列工作包门禁。

决策登记表固化为`evidence/implementation/migration/decision-register.json`，并由同目录`decision-register.schema.json`校验。每项至少包含`decisionId/status/ownerRole/selectedValue/selectedValueSha256/affectedRules/blockedPackages/approvalId/evidencePath/approvedBy/approvedAt/expiresAt`。状态只允许`OPEN/APPROVED/RISK_ACCEPTED/SUPERSEDED`。

审批证据由`approval-evidence.json`及`approval-evidence.schema.json`约束，每条至少包含`approvalId/decisionId/releaseId/decisionContentSha256/status/approverSubjectId/approverRole/signatureProvider/signatureReference/approvedAt/expiresAt`。`signatureReference`必须指向企业审批/签署系统中的可验证记录，不能把JSON自报的姓名视为签名。切换程序仅在Schema通过、决策内容哈希一致、责任角色符合、外部签署记录验证有效、未过期且未被后续记录取代时接受`APPROVED`或`RISK_ACCEPTED`。聊天结论、代码注释、口头说明和无法验证签署身份的文件均不能放行。

验签适配器配置固化为`approval-verifier-config.json`并由Schema校验，至少登记`providerCode/endpointReference/credentialReference/subjectBinding/roleBinding/timeout/retry/failurePolicy/verifyCommand`；仅保存地址和凭据引用。`failurePolicy`固定为`DENY`：提供方不可用、身份或角色映射失败、命令非零退出、超时或响应无法验签时全部保持阻断。`verifyCommand`必须能在CI和切换环境以同一输入重复执行并输出逐`approvalId`结果；在`BLK-018`关闭前，JSON中的`APPROVED/RISK_ACCEPTED`状态不具有放行效力。

| 决策ID | 当前状态 | 待确认内容/允许输出 | 决策责任角色 | 阻塞工作包 |
|---|---|---|---|---|
| BLK-001 | `OPEN` | `ADR-0001`从`Proposed`转为`Accepted`，并确认只用项目—订单行范围还是另建独立“实施订单”实体；或给出替代模型 | 架构负责人、业务负责人 | 002、006至015 |
| BLK-002 | `OPEN` | 当前DDL与矩阵失配；逐项漂移裁决后，生成器、Schema、锁定运行时和CI命令进入版本控制 | 数据架构、业务负责人、开发负责人 | 001至017 |
| BLK-003 | `OPEN` | 身份与访问仅有逻辑设计；`pms_project_assignment`、`pms_user_service_scope`、`pms_access_profile*`、`system_user_company_department_scope`等是否作为正式物理表及字段矩阵 | 安全架构、数据架构 | 002、005、008、015 |
| BLK-004 | `OPEN` | 项目、任务和专业流程的稳定状态码、动作转换、守卫、角色、并发/幂等、重开规则 | 各领域负责人、架构负责人 | BIZ-002至BIZ-008中涉及状态变化的代码、AI-MIG-013至015；数据结构和测试骨架可先做 |
| BLK-005 | `OPEN` | 正式MySQL 8.x小版本、字符集、排序规则和Flyway/Liquibase选择 | 技术负责人、DBA | 002以后 |
| BLK-006 | `OPEN` | 订单行计量单位、小数位、可分配基数和退货行数量守恒公式 | 业务负责人、数据负责人 | 010、014、015 |
| BLK-007 | `OPEN` | 历史跨项目订单行缺失分配量的补录、排除或风险接受 | 业务负责人 | 010、014、015 |
| BLK-008 | `OPEN` | SN跨项目转移链和唯一当前归属；冲突处置 | 资产领域负责人 | 012、014、015 |
| BLK-009 | `OPEN` | 无法解析所属公司的回款合同、项目合同补录凭证 | 合同业务负责人 | 007、014、015 |
| BLK-010 | `OPEN` | `rma_no`到RMA、借转销、借转退、借用返还、再次发放的正式动作码 | 资产领域负责人 | 011、012、014 |
| BLK-011 | `OPEN` | 外部账号允许服务的公司范围、跨公司规则、多转派权限合并，以及`PROJECT_SUBTREE`是否动态覆盖以后新增后代 | 安全负责人、业务负责人 | 005、008、015 |
| BLK-012 | `OPEN` | 时态关系采用追加历史还是原行失效复用；生效区间、唯一性和更正动作 | 数据架构、领域负责人 | 006至012 |
| BLK-013 | `OPEN` | 正式切换窗口、冻结对象、旧平台只读保留期、签署人及入口回退时限 | 业务、研发、测试、运维 | 015至017 |
| BLK-014 | `OPEN` | 每个只读同步对象的权威方向、业务键、游标、删除/取消语义、频率和延迟SLO | 集成负责人、来源系统负责人 | 016、017 |
| BLK-015 | `OPEN` | 高频查询清单、数据规模、P95阈值、并发、冷热缓存、采样次数、硬件环境和豁免审批 | 架构负责人、测试负责人、运维负责人 | 013至015 |
| BLK-016 | `OPEN` | 实现候选工作树接收批准规格提交和发布清单，固定实现分支、提交及构建入口 | 开发负责人 | 001至017的代码实现 |
| BLK-017 | `OPEN` | 全部物理源字段的敏感分级、迁移存储、证据展示、密钥引用、保留期和审批 | 安全负责人、数据负责人 | 004、014、015 |
| BLK-018 | `OPEN` | 企业审批/签署提供方、验签适配器、人员与角色绑定、CI/切换验证命令及失败关闭策略 | 安全负责人、运维负责人 | 015至017及全部风险接受放行 |

以下是文档一致性缺口，也必须在`AI-MIG-000`关闭：

- `pms_project_tree_summary`只在证据性说明中出现，未进入正式DDL和领域设计；删除该引用或明确为未来可选读模型，不能由AI直接创建。
- ER图中项目到实施订单的连线必须明确落在`pms_project_order_line_scope`，不能误标为项目—订单头关系。
- 现有`state-machines.md`不是可执行状态机；没有稳定码和动作矩阵前，不得按中文状态说明直接编码。
- `tasks/todo.md`中的`T-CP-006`只覆盖通用数据库迁移基线；进入实现前必须把`AI-MIG-000`至`AI-MIG-017`映射为可跟踪任务，不得把旧数据迁移混入单个Flyway骨架任务。
- DDL建表验证只证明结构可创建，不证明真实数据可迁移、性能达标或未决规则已关闭。

## 14. 禁止用“完成”掩盖的事项

- DDL能建表，不等于真实数据能迁移。
- 326字段有映射，不等于3908个旧字段都进入业务列；`SOURCE_ONLY`必须保留原值。
- 批次成功，不等于所有问题已解决；必须区分阻断、告警和批准接受。
- 最终状态相同，不等于变更血缘、事件、权限和审计等价。
- 编译和单元测试通过，不等于MySQL约束、性能、浏览器权限和切换回退通过。
- 新平台上线，不等于可以删除旧平台；必须先证明零活动依赖并完成独立收缩版本。

## 15. 接手AI交付模板

每个工作包完成时按以下格式报告：

```markdown
## 工作包
AI-MIG-xxx / BIZ-xxx

## 依据
- 规则和文件定位
- 使用的DDL、Excel和矩阵哈希

## 变更
- 模块、前向迁移、代码、接口和页面

## 数据行为
- 输入、输出、幂等键、事务、失败和重跑

## 验证
- 自动化测试
- MySQL验证
- 对账与性能证据
- 浏览器验收（如适用）

## 未完成
- 待确认、问题类型和阻断影响

## 回退
- 代码回退、数据库兼容和批次处置
```

如果无法填写上述任一项，不得声称工作包已经完成。
