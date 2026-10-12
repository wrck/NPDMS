# 最终客户联系人整表承接方案

状态：PILOT_LOCAL_VERIFIED。整表已批准，9条最终客户联系人已通过迁移功能及CUS Owner承接；下文保留原提案和裁决过程，不代表仍待批准。

2026-10-12用户对call_a63ea72e55eb4659b22f348c0920a8cf第0项回复“整表按建议”，批准上述29列、仅最终客户关系的有条件可空引用、非主联系人/只读及原NULL审计处理。不重复申请这5列或同表规则。

## 实际承接与验证

V436经指定Compose/Flyway入口应用，表最终29列。原全表17条联系人（本批项目中3条）的24个原字段逐值不变；五个新字段在旧记录均为NULL。旧Flyway回执未改写，其他受保护业务保持。

通过专用迁移入口、公开PROJ相关方/权限接口及CUS Owner迁入9条，全部NEW；原15字段逐项核对，联系姓名、空编码、电话、空邮箱、来源及有效期完整承接，creator精确转换正式用户ID，原NULL updater保持，原NULL update_time按批准使用原createTime。来源身份及原NULL值另由现有迁移绑定和不可变回执证明。全表现为26条，无伪造客户主档、主联系人、账号、项目授权或完成事件。

9次原请求重放全部REPLAY_COMPLETED，没有重复记录。浏览器逐项目验证9个联系人及对应的真实最终客户名称、电话、历史生效时间和只读操作。51304原主联系人李四保持，普通编辑实际打开后取消；53487闭环项目普通新增/载入禁用，历史正常显示。浏览器验收后全值复核通过：原17联系人、客户联系人主档、联系人操作历史、145条项目成员、项目状态与其余保护业务不变，原70行×15列也不变。

62项后端检查通过，包括7项指定MySQL24306单连接临时表验证：条件引用必填、主联系人唯一性、NULL审计保护、租户隔离、区间校验、失败回滚和不替代购货方联系人完成条件。普通维护/历史只读、来源篡改、精确身份、幂等及后续变更保护均有失败检查。前端4项运行测试及完整41模块打包通过。59282更新到批准版本，19082及其他应用进程不变。

证据位于任务私有目录：final-contact-ddl-preview/result-20261012.json、final-contact-approved-state-20261012.json、final-contact-browser-proof-20261012.json及逐项目DOM记录、final-contact-mysql/package-20261012.log。此结论只覆盖已批准10个项目中的9条最终客户联系人；全历史、新客户主档关联及其他待裁决领域尚未完成。此前生产Flyway基线差异与许可索引问题仍未关闭，不宣称生产就绪。

## 已核实的来源与现有实现

原pm_project_member全部15列，本批70行中的9行memberRole=60为最终客户人员，projectType=10、memberCode为空字符串、fromFlag=2、电话均非空、email为空字符串、原更新时间和更新人均NULL。9个原项目为51922、53546、52564、53487、53614、51304、53649、52443、50370；69300没有该类记录。原有效开始和创建时间均有值，原有效结束均NULL。

现有CUS项目联系人表cus_project_customer_contact_relation有24列（包含一个生成列），客户联系人主档cus_customer_contact有19列，操作历史cus_contact_history有10列。ProjectCustomerContactService正常新增会按正式项目customer_id创建客户联系人主档，并可能将首位有效联系人设为主联系人；正常修改使用客户主档、项目版本、权限和校验。联系人启停只有0/1，不能代替原有效期。原项目已经具有10条准确来源关联的FINAL_CUSTOMER相关方，但其party_code均NULL；不代表已绑定同一个购货方客户。以名称匹配客户ID或直接走普通新增会制造未证实的客户联系关系。当前3条项目联系人及其主档/历史保持。

## 建议与备选

建议复用既有CUS项目联系人实体，让记录明确关联项目FINAL_CUSTOMER相关方，不新增联系人业务表，不伪造客户主档或平台账号。新增字段只保存既有实体没有的联系关系及原业务属性；此方案需要批准下列5列和有条件的约束调整后才能实施。另一选择是先由用户逐项指定9人的正式客户ID及联系人主档，仍须另解决原登记来源和有效期缺项。仅按名称补客户或将这些信息写备注、来源快照不能承接完整业务事实。

## 唯一候选结构变更：项目联系人24→29列

| 序号 | 候选新增列 | 类型/可空性 | 业务含义及来源 |
|---|---|---|---|
| 1 | project_party_id | BIGINT NULL | 联系人所属项目相关方；本批关联同一项目经原pm_project_header绑定核实的FINAL_CUSTOMER，不按名称关联客户 |
| 2 | contact_code | VARCHAR(45) NULL | 原人员业务编码memberCode，空字符串原样保留；不充当工号或账号 |
| 3 | registration_source | VARCHAR(2) NULL | 原fromFlag，1项目信息/2手工成员；不是内外部用户类型或授权来源 |
| 4 | effective_from | DATETIME(3) NULL | 原联系人区间开始effectiveFrom，不用当前迁入时间代替 |
| 5 | effective_to | DATETIME(3) NULL | 原联系人区间结束effectiveTo，NULL保持；与status启停分开 |

新增物理表0，其他物理列0，正式实体扩展0。客户联系人主档19列和操作历史10列不改结构。本轮9条仅保存项目最终客户的正式联系关系，主档引用不能核实时不创建伪客户/伪主档。

### 既有24列逐项复用

| 既有列 | 类型/可空性 | 本轮处理 |
|---|---|---|
| id | BIGINT NOT NULL | 正式生成ID，原id用既有迁移绑定，不覆盖原3条 |
| project_id | BIGINT NOT NULL | 精确原项目绑定 |
| customer_id | BIGINT NOT NULL | 建议仅具有经核实project_party_id的最终客户联系关系可NULL；未核实客户身份不挂购货方 |
| customer_contact_id | BIGINT NOT NULL | 建议仅上述联系关系可NULL，不伪造客户主档；普通来源引用仍必填 |
| name | VARCHAR(64) NOT NULL | memberName原值 |
| department | VARCHAR(64) NULL | 原无此属性保持NULL |
| title | VARCHAR(64) NULL | 原无此属性保持NULL，不把角色60当职务 |
| mobile | VARCHAR(32) NULL | 原phoneNum是通用电话属性，本轮写phone，本列保持NULL，不复制两份 |
| phone | VARCHAR(32) NULL | 原phoneNum原值，不改写用户主档 |
| email | VARCHAR(128) NULL | 原email，包括空字符串；普通维护仍沿现有格式规则 |
| primary_flag | BIT NOT NULL | false；不撤销当前主联系人、不猜历史主联系人 |
| primary_set_time | DATETIME NULL | NULL；原无主联系人设定事实 |
| status | TINYINT NOT NULL | 0，联系人资料启用；实际区间另以原有效期判断，不生成账号权限或任务完成事实 |
| remark | VARCHAR(500) NULL | NULL，不承接原字段 |
| version | INT NOT NULL | 正式新记录0，现存记录及版本不变 |
| creator | VARCHAR(64) NULL | 原createBy精确SYSTEM账号映射为正式ID；本批所有操作者已核实存在，不替换为执行管理员 |
| create_time | DATETIME NOT NULL | 原createTime |
| updater | VARCHAR(64) NULL | 原updateBy=NULL保持NULL，不受框架自动填充影响 |
| update_time | DATETIME NOT NULL | 建议原NULL用各自原createTime初始化，原NULL在不可变来源证据列明，待本表批准 |
| deleted | BIT NOT NULL | false，原未记录删除，不删除当前关系 |
| deleted_by | BIGINT NULL | NULL |
| deleted_at | DATETIME NULL | NULL |
| tenant_id | BIGINT NOT NULL | 正式迁移租户1，由服务端确定 |
| active_primary_project_id | BIGINT生成列 | 既有表达式不变；新记录primary=false不占主联系人槽位 |

### 主档19列、操作历史10列全部保持

cus_customer_contact：id、customer_id、name、department、title、mobile、phone、email、primary_flag、status、remark、version、creator、create_time、updater、update_time、deleted、tenant_id、active_primary_customer_id。没有精确客户身份，不新增或修改主档。原9人的项目联系属性由项目关系承接，不用主档备注或主档快照代替。

cus_contact_history：id、customer_contact_id、project_relation_id、project_id、action_code、before_values、after_values、actor_user_id、occurred_at、tenant_id。原表是联系关系及区间，不是操作事件流；不能生成历史CREATE/UPDATE动作或伪操作者。新迁入操作由既有迁移运行/Owner回执审计，不对customer_contact_id必填的操作历史伪造主档引用。全部现存历史保持。

## 全部15原字段映射：无丢弃项

| 原字段 | 正式承接 |
|---|---|
| id | 既有INT/PLT来源身份绑定，项目联系人正式ID独立生成 |
| projectId | project_id，经项目绑定精确转换 |
| projectType | 正式项目project_type=10；其他主体不自行扩大 |
| memberRole | project_party_id指向同项目FINAL_CUSTOMER，由已核实原03字典60→最终客户含义转换；不授平台角色 |
| memberCode | 候选contact_code，原空字符串保持 |
| memberName | name |
| phoneNum | phone |
| email | email，原空字符串保持 |
| fromFlag | 候选registration_source |
| createTime | create_time原日期 |
| createBy | creator，精确正式用户ID |
| updateTime | update_time，原NULL用原创建时间初始化，原NULL证据保留；本条待裁决 |
| updateBy | updater，原NULL保持 |
| effectiveFrom | 候选effective_from |
| effectiveTo | 候选effective_to，原NULL保持 |

## 有条件约束、Owner及验收边界

仅project_party_id精确核实为同租户同项目FINAL_CUSTOMER且迁移来源身份有效时，允许customer_id/customer_contact_id为空；普通购货方联系关系仍需成对有效客户及主档。数据库增加同义条件约束，不能让普通请求创建无归属记录。既有状态0/1、主联系人唯一键及来源主档唯一键不撤回，不新增主联系人或当前项目访问权限。

本轮由正式CUS迁移Owner经迁移功能承接；PROJ通过公开业务API提供项目权限、准确相关方和版本，不跨模块调用Mapper/表。来源全部15字段、原绑定、目标版本及全部现存记录需保护。只有缺少来源主档的最终客户历史联系关系允许只读；普通客户联系人维护仍走原Owner，不通过迁移ID激活正常编辑/删除/恢复。正常联系人完成规则继续要求正式项目客户主档关系；最终客户联系资料不能自动代替购货方联系人完成事实或唤醒任务完成。

验收应覆盖9条原值、原审计、有效期、相关方/客户边界、空邮箱、普通来源唯一性、租户/项目管理权限、禁止伪主档与主联系人、失败回滚、幂等重复与后续修改阻断；真实页面需展示最终客户归属、历史区间和只读状态。未经整表批准不实施上述结构或写入。

证据：任务私有project-contact-table-readonly-20261012.json（原9行15列、现有三CUS表全部字段/索引、现存3联系人、10最终客户相关方、正式项目客户）；project-member-original-readonly-20261011.json；V216/V347、ProjectCustomerContactService、ProjectContactContextApiImpl、ContactRules、联系人Mapper及现有UI。只读核查businessWrites=0、schemaWrites=0。
