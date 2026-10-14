# proj_project_member_assignment 整表候选方案

2026-10-14后续裁决：用户明确历史最终客户联系人按项目成员迁入，历史项目无法准确归集最终客户。生产P32因此复用本表及PROJ成员Owner承接原角色60的人员历史，与P31账号成员批次按角色分开；不再依赖最终客户相关方或CUS联系人主档，不产生账号授权。姓名、联系方式、登记来源、有效区间及审计沿既有成员字段承接；原角色60保留历史含义，无精确账号时user_id可空，authorization_confirmed=false。详见[现行历史人员裁决](../decisions/open-questions.md#q-legacy-member-20261008-001--历史项目人员)。下文将角色60排除到CUS的要求作为原方案保留，由最新裁决替代；原试点数据和证据不改写，现有成员入口调整及验证待完成。

状态：WHOLE_TABLE_APPROVED / IMPLEMENTATION_IN_PROGRESS；用户已确认本表29个既有列及5个新增列、相应缺项和约束处理。具体主责冲突仍待逐项裁决；本文件本身不产生业务写入。

## 已核实事实及建议业务归属

原pm_project_member为15列435342条，本批70条。建议其中61条销售/服务经理/项目经理复用既有成员区间表；9条最终客户人员由既有CUS联系人业务另提交整表方案，当前本表不得将其变成平台账号成员。没有原字段或原行获准丢弃。全量其他主体/角色和133组重复区间身份仍逐项保留待裁决，不按本批同码猜配。

用户已确认有效成员经正式Owner资格核验承接项目授权、过期区间保留历史、当前成员或主责冲突另行裁决。正常成员Owner会改主责、版本和指派状态，并要求ACTIVE项目；历史迁入不能直接调用该动作或跳过这些约束。

2026-10-11真实只读Owner预览：3个ACTIVE项目中的10条匹配候选，9条返回唯一相同正式用户，销售l03302因仅有areaLeader系统角色返回0条；未自行补授销售代表角色。另19条匹配候选在已闭环项目，普通Owner入口不适用，不能标为资格通过。6条来源有效区间没有精确账号，26条账号成员区间已过期，9条属于联系人主体。所有成员、授权和生命周期保持。私有脚本曾因接口长ID为字符串而误判，已按无精度损失的完整字符串比较修正；当前9/1统计有真实Owner回执。

## 已批准新增5列

| 新列 | 类型/可空/默认 | 原字段及用途 |
|---|---|---|
| member_code | VARCHAR(45) NULL | 原memberCode；完整历史人员编码。user_id能精确关联账号，但不能代替未关联人员编码，也不能写工号 |
| phone_number | VARCHAR(20) NULL | 原phoneNum；成员区间自身联系电话，不覆盖SYS当前手机 |
| email | VARCHAR(45) NULL | 原email；成员区间自身邮箱，不覆盖SYS当前邮箱 |
| registration_source | VARCHAR(2) NULL | 原fromFlag：1项目信息、2手工成员；保持原值，不表示内部/外部 |
| authorization_confirmed | BIT NOT NULL DEFAULT true | 既有正常成员为true；待资格/主责裁决及过期迁入历史为false。只有Owner核验、必要裁决及授权事务完成后才能为true |

第五列建议用于明确区分正式保存的历史资料和当前授权关系。原区间仍有效但无账号、资格不符或主责冲突时，仅保存原有效期与ACTIVE会被现有成员权限消费者识别为授权。建议全部当前成员/指派/项目范围/任务资格消费者通过Owner只读取true且有效的关系，历史列表保留false记录；不改变原日期、不发明历史失效原因、不用假结束日期绕过授权。正常新增和维护仍走原Owner及完整业务校验。用户也可指定另一既有正式业务属性承接这个区别，未裁决不新增本列或改查询。

## 29个既有列逐项复用或约束建议

| 既有列 | 现类型/可空 | 本轮处理候选 |
|---|---|---|
| id | bigint / NO | 正式ID；原ID归既有迁移绑定 |
| project_id | bigint / NO | 通过原项目绑定关联正式项目 |
| user_id | bigint / NO | 完整账号精确关联；建议仅历史未关联记录允许NULL，不伪造账号 |
| employee_no | varchar(64) / YES | 仅SYS核实的真实工号，不承接来源登录名 |
| member_name | varchar(128) / YES | 原memberName，不被当前姓名覆盖 |
| company_id | bigint / YES | 来源未记录成员入组时的组织事实；不以当前组织伪造历史快照，建议NULL |
| company_code | varchar(64) / YES | 来源未记录成员入组时的组织事实；不以当前组织伪造历史快照，建议NULL |
| company_name | varchar(255) / YES | 来源未记录成员入组时的组织事实；不以当前组织伪造历史快照，建议NULL |
| department_id | bigint / YES | 来源未记录成员入组时的组织事实；不以当前组织伪造历史快照，建议NULL |
| department_code | varchar(64) / YES | 来源未记录成员入组时的组织事实；不以当前组织伪造历史快照，建议NULL |
| department_name | varchar(255) / YES | 来源未记录成员入组时的组织事实；不以当前组织伪造历史快照，建议NULL |
| member_role | varchar(32) / NO | 本批10/20/30对应既有销售代表/服务经理/项目经理；其他全历史角色待逐类核实 |
| assignment_type | varchar(32) / YES | 有经核实及裁决的主责/协作事实再填；不从来源1/2推断 |
| site_id | bigint / YES | 来源无站点指派，不猜造 |
| responsibility | varchar(500) / YES | 来源未记录职责，不猜造 |
| change_reason | varchar(500) / YES | 迁移命令自身说明，不代替任何原字段 |
| effective_from | datetime(3) / YES | 原effectiveFrom |
| effective_to | datetime(3) / YES | 原effectiveTo，过期区间原样保留 |
| status | varchar(32) / NO | 沿既有记录状态ACTIVE及有效区间；授权资格单独由候选标记控制 |
| version | int unsigned / NO | 迁入新行初始化0；现存行不得覆盖版本 |
| creator | varchar(64) / YES | 原createBy精确人员绑定；未关联原编码建议保留，NULL保持 |
| create_time | datetime / NO | 原createTime；建议仅未确认授权历史允许原NULL |
| updater | varchar(64) / YES | 原updateBy精确人员绑定；未关联原编码建议保留，NULL保持 |
| update_time | datetime / NO | 原updateTime；NULL时建议取本行原createTime，两者均NULL保留NULL |
| deleted | bit(1) / NO | 默认false，不吞入来源行 |
| deleted_time | datetime / YES | 未删除保持NULL |
| tenant_id | bigint / NO | 当前真实租户 |
| remark | varchar(500) / YES | 无同义来源值，NULL；不能保存原业务缺项 |
| end_reason | varchar(500) / YES | 无原原因则NULL，不编造退出原因 |

最终候选为34列；新增表0、扩展属性0，新增物理列仅上述5项。user_id、create_time、update_time的原非空约束建议仅对未确认授权历史允许NULL；确认授权仍要求可核实用户，正常业务审计规则保持。历史原NULL操作者保留，需显式Owner持久化及实际落库检查避免框架自动填管理员。所有既有当前记录、日期、权限、主责和不可变回执先保护。

## 全15原字段映射（无排除）

| 原字段 | 本表/CUS承接建议 |
|---|---|
| id | INT/PLT稳定来源绑定；不新增技术镜像 |
| projectId | 本表project_id；联系人按CUS真实项目关联 |
| projectType | 项目主体与正式项目类型精确对应；本批10已裁决，其他主体不猜挂 |
| memberRole | 本批10→SALES_REPRESENTATIVE、20→SERVICE_MANAGER、30→PROJECT_MANAGER；60为最终客户联系人，CUS另审查；其他主体原码另裁决 |
| memberCode | 候选member_code及可核实user_id；联系人原码待CUS方案 |
| memberName | member_name；联系人name |
| phoneNum | 候选phone_number；联系人正式mobile/phone，不能凭格式猜是哪一类 |
| email | 候选email；联系人正式email |
| fromFlag | 候选registration_source；联系人登记来源待CUS方案 |
| createTime/createBy | create_time/creator；联系人正式审计，原NULL与未关联操作者按整表裁决 |
| updateTime/updateBy | update_time/updater；联系人正式审计，原NULL按整表裁决 |
| effectiveFrom/effectiveTo | effective_from/effective_to；联系人区间缺项另由CUS整表裁决 |

## 约束与写入顺序（均为候选）

建议保留现有uk_proj_member_role（租户、项目、用户、角色、开始时间）及现有应用层区间重叠保护。本批原行先逐键预览，冲突阻断并交裁决；全历史133组重复身份尚未逐值核查，因此本方案不批准去重、删行或改变唯一键。承接点方案批准后，再提交确实存在的完整重复行及约束处理，不用唯一键失败当作丢弃理由。

既有61条人员范围只能经迁移/同步及PROJ Owner写入，与PLT来源绑定和回执同事务。本轮人员原15列重读、绑定及现存全列保护，后续变化阻断。有效且资格通过也不能直接覆盖原主责：当前管理员主责、新旧不同经理、同项目多个有效经理须先明确责任类型及裁决。审批、任务完成、当前项目生命周期和SYS系统角色不由历史成员直接生成。

已闭环19条候选如何承接当前项目授权另待业务裁决。建议先正式保存历史，复用SYSTEM资格API作精确核查；是否允许在不重开项目、不修改生命周期/当前指派的条件下补授项目访问，必须明确Owner契约，不能把普通成员入口的ACTIVE校验删除或绕过。

## 本批真实Owner候选明细

| 原记录 | 原项目 | 正式角色候选 | 用户ID | 原区间有效 | 核查结果 |
|---|---|---|---|---|
| 238673 | 50370 | SERVICE_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 243629 | 51304 | SERVICE_MANAGER | 2108539871029019646 | 是 | OWNER_QUALIFIED |
| 249226 | 51922 | SERVICE_MANAGER | 2108539871029019646 | 是 | OWNER_QUALIFIED |
| 254359 | 52443 | SERVICE_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 254809 | 52564 | SERVICE_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 258780 | 53487 | SERVICE_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 258781 | 53487 | SALES_REPRESENTATIVE | 2108539871029018999 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 259074 | 53546 | SERVICE_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 259302 | 53614 | SERVICE_MANAGER | 2108539871029019646 | 是 | OWNER_QUALIFIED |
| 259303 | 53614 | SALES_REPRESENTATIVE | 2108539871029018999 | 是 | OWNER_NOT_QUALIFIED |
| 259394 | 53649 | SERVICE_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 259938 | 53546 | PROJECT_MANAGER | 2108539871029019704 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 259939 | 53546 | PROJECT_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 260260 | 53649 | PROJECT_MANAGER | 2108539871029019801 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 261076 | 52564 | PROJECT_MANAGER | 2108539871029019801 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 274364 | 52443 | PROJECT_MANAGER | 2108539871029019801 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 303247 | 53614 | PROJECT_MANAGER | 2108539871029019137 | 是 | OWNER_QUALIFIED |
| 303252 | 53487 | PROJECT_MANAGER | 2108539871029019078 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 303253 | 53487 | PROJECT_MANAGER | 2108539871029019137 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 303349 | 51304 | PROJECT_MANAGER | 2108539871029019137 | 是 | OWNER_QUALIFIED |
| 303350 | 51304 | PROJECT_MANAGER | 2108539871029019646 | 是 | OWNER_QUALIFIED |
| 340733 | 51922 | PROJECT_MANAGER | 2108539871029019646 | 是 | OWNER_QUALIFIED |
| 343465 | 69300 | SERVICE_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 417261 | 50370 | PROJECT_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 417264 | 52443 | PROJECT_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 417265 | 52564 | PROJECT_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 417267 | 53649 | PROJECT_MANAGER | 2108539871029019646 | 是 | NORMAL_OWNER_REQUIRES_ACTIVE_PROJECT |
| 445141 | 53614 | PROJECT_MANAGER | 2108539871029019609 | 是 | OWNER_QUALIFIED |
| 445213 | 51922 | PROJECT_MANAGER | 2108539871029019609 | 是 | OWNER_QUALIFIED |

依据：原15列及现29列实际SHOW FULL COLUMNS/SHOW INDEX、原始全表统计和本批70行，见project-member-original-readonly-20261011.json；真实Owner GET member-candidates回执见project-member-owner-preview-20261011.json；系统角色差异见project-member-qualification-reasons-20261011.json。现有ProjectMemberRoles、OrdinaryProjectMemberService、ProjectManagerMemberApplicationService、ActiveUserSelectionApi及当前成员Mapper已审查。此候选方案只供整表裁决，未实施。

## 2026-10-11用户整表及闭环项目裁决

用户对call_cff98495ddcb4c93ac2385908e0c0efb第0项答复“确认”，批准上述34列整表方案、仅未确认历史可空的用户与审计约束、原NULL审计处理、现有唯一键及正常资格校验保持。新增列仅member_code、phone_number、email、registration_source、authorization_confirmed五项，9条最终客户联系人仍单独提交CUS整表方案。

同调用第1项答复“允许迁移Owner核验后补建成员授权关系”。专用迁移Owner可对已闭环项目复用相同SYSTEM账号、角色和适用公司资格规则核验并补建成员授权；不重开项目、不改生命周期，不删除普通成员入口的ACTIVE校验。此批准不代替具体主责/成员冲突裁决，不授权赋予缺失系统角色。上文尚待结构或闭环方向裁决的文字为提案阶段记录，以本节裁决为准。

## 本轮已完成验证边界

29项后端检查通过：普通成员18项、专用历史预览5项、MySQL24306单连接临时表6项。V434通过Compose/Flyway成功应用（rank377、checksum254460667），最终34列；现有84条记录的原29字段逐行一致，五个新属性为NULL/默认true，其他保护业务及旧回执一致，尚无成员迁入或新授权。前端12项运行测试通过，覆盖未确认/未关联历史只读、区间联系方式显示和普通已确认成员维护。完整41模块打包通过；真实Owner预览、当前主责冲突裁决和正式成员写入尚未完成。

## 2026-10-12执行前验证与三项目主责批准

真实专用Owner已核验全部70条原成员及15原字段：23条通过相同SYSTEM角色/适用公司资格，其中14条属于闭环项目；26条过期历史、6条没有精确账号、6条未通过资格，9条最终客户人员仍属CUS待裁决。完整70行及三项目主责批准见2026-10-11-dppms-project-member-owner-conflicts.md。仅51304/53487/51922的旧主责和6条管理员区间结束已获具体批准，其他项目主责引用仍等待答复。

最终49项相关后端/数据库检查通过：迁入Owner13项、只读资格预览5项、普通成员18项、SYSTEM精确身份4项、真实MySQL单连接临时表9项。真实数据库失败检查曾捕获实体参数被框架自动补入当前审计日期；已改为非实体迁移参数，原NULL审计及日期保持检查通过，普通自动填充保留。失败事务中新成员与管理员区间一起回滚验证通过；租户、当前授权、唯一键、版本、主责冲突、重放绑定和后续人工变化均受保护。没有改低普通ACTIVE/资格规则。最新完整41模块打包通过。

真实浏览器已确认51304原2条管理员当前成员显示、普通维护入口可见、历史页为空，基线未执行写入。此基线验收不代表新历史及授权已完成。2026-10-12切换59282应用成员Owner，启动保护核对84条既有成员、153条许可和其他业务均未改变；本节记录时仍未迁入成员。后续实际回执与页面验收另追加。

## 2026-10-12完整10项目正式承接及重放

最新用户批准其余7项目旧主责后，按原保护预览逐项目执行同一PROJ迁移Owner。三项目先迁21条，七项目再迁40条，合计61条：23条资格通过并确认授权，38条原过期/未关联/资格未通过记录保留正式只读历史；9条最终客户人员继续等待CUS整表裁决，不作为账号成员。按批准结束6条管理员当前区间，原创建人、创建时间及人员等属性保留。69300仅服务经理z01010授权，原项目经理没有补授角色、没有项目经理授权。

全表145条=原84条＋61条；原84条除获准6条结束相关字段外逐值一致。61条原人员编码、姓名、角色、联系方式、来源、原区间、人员/工号引用及历史审计逐值读回一致，原NULL日期/更新人没有被自动填充。10项目主责引用和版本符合批准，其余项目属性（含生命周期、阶段、历史审计、51922新结束日期）保持。原70行15字段及其他PROJ业务、153许可、软件当前/历史/批次/解析事实、保修记录和当前投影均保持。

10项目再次调用均REPLAY_COMPLETED，0重复成员、0再次结束区间、0额外项目版本更新。初次迁入命令绑定来源及完整预览，持续成员同步未启用；新来源或不同请求不能借初次命令再授权限。来源/回执/目标全字段或后续人工变化仍阻断。

重放真实核查还发现MySQL JSON回执把DECIMAL的0.00规范成0.0，导致数值未变的项目被误判。仅对progress/aggregation_weight按数值规范尾零后比较，其他字段和真实数值变化仍逐字段保护；未修改旧回执或放弃项目比较。新增失败检查证明0.01仍被阻断。最终50项相关检查通过：迁入Owner14、资格预览5、普通成员18、SYSTEM身份4、真实MySQL临时表9；完整41模块打包通过。SQL/回滚检查前提未变复用有效证据，不重复写真实业务测试数据。

合并证明：任务私有member-ten-project-final-proof-20261012.json，逐项目不可变预览及回执为member-three-project-approved-state-20261011.json、member-seven-project-approved-state-20261012.json。初次三项目资料中的待其余7裁决/未迁入状态由本节和最新用户批准解决；原阶段记录保留，不改写历史证据。真实页面验收另追加。生产Flyway基线仍有已记录校验差异，许可大表身份查询索引仍未应用，不能宣称生产上线完成。

### 完整10项目真实页面验收

19082真实浏览器逐项目进入详情、查询当前成员与历史成员，当前/历史数量分别为：51304=3/5、53487=3/5、51922=3/9、52564=2/7、53649=2/4、50370=2/3、69300=1/1、52443=2/4、53546=2/2、53614=3/5。23条授权的人员及主责/协作与Owner回执一致；所有未确认历史显示原姓名、编码、邮箱和区间并只读，原管理员结束区间可查询。69300只有服务经理，未通过项目经理仍在历史页只读。

51304真实打开liujiazhen_supp编辑弹窗，系统资格选择显示正确人员、邮箱及非主责，长用户ID未发生精度损失；取消后刷新仍为3条当前成员，无业务写入。53487已闭环，普通新增、编辑、移出仍禁用，迁移授权没有放开普通ACTIVE约束。导航重进后各项目数据保持。列表切换会异步加载，本次等待实际当前/历史数据及授权标记后采集，不能仅按相同总数判断切换已完成。

页面证据为任务私有member-ten-project-browser-proof-20261012.json及20份member-browser-{原项目号}-{current/history}-20261012.txt；编辑弹窗另见member-browser-edit-51304-20261012.txt。页面操作后重新执行完整10项目逐字段核对，全部项目、原成员、许可、软件、保修、其他项目业务及原来源保持本次批准结果。该结论只覆盖此10项目成员本地闭环；9条最终客户联系人、其他待裁决领域和生产迁移基线仍未完成。
