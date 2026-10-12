# G001：proj_project 全表裁决与实施边界

用户已对本表作出裁决；下表原审查证据仅供追溯，不再表示待批准建议。当前代码、SQL基线和真实承接尚未全部完成。

整个过程使用既有迁移与同步功能，复用结果与过程并支持扩大到全部历史数据；业务写入仅经 Owner，脚本只作只读审查或测试驱动，不直接承接业务数据。

用户明确要求当前 SQL/migrations 也直接清理，禁止追加冗余字段再用后续迁移删除；原执行脚本和审计只读留存，当前脚本整理为最终定义，实际开发库基线与校验对齐尚未执行。

有既有正式字段直接复用；候选字段去掉 source_ 前缀后按业务语义承接；无法匹配的新增列删除；已有正式字段的重复列在正式字段承接后删除。

先完成 fnd_user_info、fnd_user_power、fnd_roles 的迁移，再关联项目创建/更新人。项目编码以旧编码为准；历史时间保持；旧状态 100/20/31/32 按已确认含义处理，不绕过状态机。

| 序号 | 用户裁决 |
|---|---|
| 7 | 新增正式 custom_info JSON，完整保存 customInfo，不拆键。 |
| 8 | 新增正式 order_create_time，保存项目创建时 column009 的历史值，不改变其他订单同步逻辑。 |
| 9 | 删除新增 source_back_reason，不保留对应新增列。 |
| 10 | 删除新增 source_back_state，不保留对应新增列。 |
| 11 | 复用正式 company_id/company_code；通过 t_company 来源身份解析，再按 01→001、02→002、03→003、04→004 转换，未知码阻断。 |
| 12 | 复用正式 create_time，保持历史迁移时间。 |
| 13 | 先通过迁移与同步完成人员角色迁移，再关联正式 creator。 |
| 17 | 新增正式 disabled；保持旧含义，1/true 无效、0/false 有效；只沿既有核销/失效动作更新，不新增流程。 |
| 18 | 新增正式 effective_from，保留项目有效期。 |
| 19 | 新增正式 effective_to，保留项目有效期。 |
| 20 | 复用 proj_project_party 的 FINAL_CUSTOMER，表示产品实际最终用户，与 COM final_customer_name 语义一致。 |
| 27 | 复用正式 project_code，旧项目编码为准；冻结编码差异须通过正式迁移 Owner 纠正，普通编辑仍保持冻结规则。 |
| 32 | 旧状态 100 正常闭环、20 不予跟踪、31 已指派服务经理、32 已指派项目经理；复用合法正式状态/历史 Owner，不绕过状态机，不臆造当前任务完成。 |
| 33 | 复用正式 project_type。 |
| 36 | 复用正式 update_time，保持历史迁移时间。 |
| 37 | 先通过迁移与同步完成人员角色迁移，再关联正式 updater。 |

以下为提交时原审查清单，实施结果另按实际验证记录。

## 本批原表复核与补充裁决（2026-10-09）

原表pm_project_header全部37列已再次只读核对。本批10个项目中6个projectState=100、1个=20、3个=32；原创建时间全部存在、原更新时间1条NULL。当前正式项目仍为ACTIVE/S0/UNASSIGNED，不得把既有source_镜像当作状态承接完成。

- projectType原码10在原fnd_basic_data.projectTypes明确表示“用服售后项目”；本来源全表80320条均为10。用户已确认复用project_type，保留原业务码10并在pms_project_type补入同义字典项；不新增项目类型列，不猜成网络集成等细分类。
- column010按原05字典10普通类/20工程类→正式project_category；column011按原06字典10直签/20非直签→signing_method；column012按原15字典0直服/1督导/3自服→implementation_mode。原15字典另有2直服转包，本批无此值，扩展时不得默认映射为其他方式。
- 用户已确认原100通过正式迁移Owner初始化为NORMAL_CLOSED、闭环阶段S6；原20初始化为EXCEPTION_CLOSED，正式原因保留“不予跟踪”。关闭时间均使用原projectCloseTime，不新增状态，不生成现平台任务或审批完成事实。
- 原31/32只表示已指派服务经理/项目经理，不等于执行或任务完成；人员关系要通过已迁入账号及原项目人员事实关联，不能凭状态编造负责人。
- 用户已确认原更新时间NULL的1条用本项目原createTime初始化正式update_time，原NULL在来源证据中列明；其他历史时间按原值，不使用本次运行时间。

本段补充裁决已批准，尚未执行正式项目写入、修改状态或整理实际数据库基线；此前新增5个正式字段及复用/清理裁决继续有效。此前提交时审查表中的建议由以上当前裁决覆盖。

| 序号/编号 | 新增列及定义 | 原字段（pm_project_header） | 既有正式字段/Owner或候选 | 差异与证据 | 建议/待核问题 |
|---|---|---|---|---|---|
| 1 / C001 | `source_market_name VARCHAR(255) NULL` | `column004` | proj_project.market_name | 旧正常写链保存名称、代码另列；全表136行中本列10个非NULL原文与正式列一致、126NULL，数据库依赖0。 | 建议仅本列撤回重复依赖并受控前向清理；原NULL、正式值及不可变证据保持，执行前必须重核。 |
| 2 / C002 | `source_system_name VARCHAR(255) NULL` | `column005` | proj_project.system_name | 旧正常写链保存名称、代码另列；全表136行中本列10个非NULL原文与正式列一致、126NULL，数据库依赖0。 | 建议仅本列撤回重复依赖并受控前向清理；原NULL、正式值及不可变证据保持，执行前必须重核。 |
| 3 / C003 | `source_expend_name VARCHAR(255) NULL` | `column006` | proj_project.expend_name | 旧正常写链保存名称、代码另列；全表136行中本列10个非NULL原文与正式列一致、126NULL，数据库依赖0。 | 建议仅本列撤回重复依赖并受控前向清理；原NULL、正式值及不可变证据保持，执行前必须重核。 |
| 4 / C004 | `source_industry_name VARCHAR(255) NULL` | `column007` | proj_project.industry_name | 旧正常写链保存名称、代码另列；全表136行中本列10个非NULL原文与正式列一致、126NULL，数据库依赖0。 | 建议仅本列撤回重复依赖并受控前向清理；原NULL、正式值及不可变证据保持，执行前必须重核。 |
| 5 / C064 | `erp_customer_code VARCHAR(255) NULL` | `column002` | customer_code/customer_id；现有客户来源身份或项目客户关系 | 原ERP客户码与当前直接客户码10/10不同，不能覆盖当前客户。 | 待精确身份关联复核；不批准该新增列保留或清理。 |
| 6 / C065 | `erp_customer_name VARCHAR(255) NULL` | `column003` | customer_name；订单客户或项目客户关系 | 只有3/10名称相同，名称相同不能代替客户身份一致。 | 与ERP客户码一起核实正式客户关系，暂不改结构。 |
| 7 / C066 | `source_custom_info JSON NULL` | `customInfo` | 待逐键核对正式业务属性/关系 | 10个JSON对象尚未完成内部键值语义审查；整个JSON原样保存不等于有效字段承接。 | 完整列出JSON键及其映射后，再补充本表裁决。 |
| 8 / C067 | `source_order_create_time DATETIME(3) NULL` | `column009` | com_sales_order.order_create_time，经正式订单关系读取 | 原项目列是订单日期上下文；项目该列与正式订单的对应值尚未逐项证实。 | 优先复用订单Owner；核关联和差异后再裁决该新增列，不重做已完成32项日期纠正。 |
| 9 / C082 | `source_back_reason TEXT NULL` | `column014` | 待核既有回退/治理历史 | 本批全NULL；无值不能证明该字段没有业务作用，也不能当作不跟踪原因。 | 保留现有数据；核旧回退动作和正式历史位置，暂不新增或清理。 |
| 10 / C083 | `source_back_state VARCHAR(11) NULL` | `isback` | 待核正式项目状态/回退历史 | 旧回退节点及不跟踪选择与当前生命周期不同；不是退款状态。 | 与原状态、原因组成完整历史事实；具体实体/列定义尚未批准。 |
| 11 / C084 | `source_company_id INT NULL` | `compId` | company_id/company_code，经来源公司身份解析 | 原compId是旧主键，不是目标company_id，也不是可直接套转换规则的公司编码。 | 先旧公司ID→原公司码，再01→001、02→002、03→003、04→004解析现公司；未知码阻断。关系验证后裁决列必要性。 |
| 12 / C085 | `source_created_at DATETIME(3) NULL` | `createTime` | 现create_time；另核既有来源审计/业务历史Owner | 10非空旧创建时间与本系统入库审计时钟不同。 | 保留旧时间，不覆盖当前审计；正式历史位置核实后裁决。 |
| 13 / C086 | `source_created_by VARCHAR(45) NULL` | `createBy` | 现creator；另核来源人员身份及历史Owner | 9非空旧人员编码，不等于当前操作者或账号。 | 保留来源人员身份，不虚构账号或授予权限；核正式历史位置。 |
| 14 / C087 | `source_custom_config JSON NULL` | `customConfig` | 待核正式配置模型 | 本批10条均NULL；不能推定与模板或闭环策略含义相同。 | 先核旧配置用途；不批准新增JSON容器，也不据NULL丢弃字段。 |
| 15 / C088 | `source_customer_project_name VARCHAR(255) NULL` | `customerProjectName` | customer_project_name | 4个有效值精确一致；1空串按既有规则归NULL，10/10转换后一致。 | 优先复用既有字段；清理前还须全表差异、依赖及原空串兼容审查，本轮不授权DROP。 |
| 16 / C089 | `source_delivery_project_type VARCHAR(10) NULL` | `column010` | project_type/project_category/business_type候选 | 旧列经fnd_basic_data解析分类；本批与现project_type直接值0/10相同。 | 先核旧字典及既有转换，不以原码新增列代替分类映射。 |
| 17 / C090 | `source_disabled BIT(1) NULL` | `disabled` | 待核正式来源记录有效性/历史Owner | 旧有效性不是现deleted、ACTIVE或关闭状态。 | 保持当前生命周期和删除标记；核正式历史位置，不自动映射。 |
| 18 / C091 | `source_effective_from DATETIME(3) NULL` | `effectiveFrom` | 待核正式来源业务记录的有效期 | 10非空；来源有效期不是项目启动或人员任期。 | 与effectiveTo、disabled联合核业务含义和既有版本Owner。 |
| 19 / C092 | `source_effective_to DATETIME(3) NULL` | `effectiveTo` | 待核正式来源业务记录的有效期 | 本批全NULL；不是现关闭时间。 | 与来源有效期一并核实，保留NULL，不自动生成结束事实。 |
| 20 / C093 | `source_final_customer_name VARCHAR(255) NULL` | `column013` | proj_project_party角色/名称或COM final_customer_name候选 | 10非空最终客户名称，不等于直接客户；完整身份和角色关联待核。 | 优先复用正式相关方/最终客户模型；不覆盖直接客户，不臆造角色。 |
| 21 / C094 | `source_implementation_mode VARCHAR(2) NULL` | `column012` | implementation_mode；既有属性Owner及纠正历史 | 10非空，与现值直接比较0/10相同；原字典转换未证明。 | 先核转换规则，禁止照搬原值或绕过冻结属性。 |
| 22 / C095 | `source_implementation_readonly INT NULL` | `columno12_readonly` | 既有CRM属性来源控制/实施方式取值候选 | 原-1可编辑、其他值只读；旧查询还把它用于实施方式回退取值，不能当新布尔属性。 | 先核旧控制和回退规则；暂不批准保留为独立业务列或删除。 |
| 23 / C096 | `source_major_project_level VARCHAR(255) NULL` | `majorProjectLevel` | major_project_level；CRM属性Owner | 本批10/10同值，已有正式承接点；独立历史差异尚未证明。 | 优先复用正式字段；全表、Owner历史和依赖审查后再提交清理。 |
| 24 / C097 | `source_not_track_reason VARCHAR(255) NULL` | `column008` | not_track_reason候选；正式旧状态历史更需核实 | P06原原因非空，但现项目ACTIVE/S0；旧20/40状态配套，当前字段被冻结。 | 待业务裁决：推荐随旧状态历史承接；不直接补当前字段。未批准历史新表/列。 |
| 25 / C098 | `source_office_code VARCHAR(255) NULL` | `column001` | department_code/department_id及组织关系候选 | 本批10/10同值；旧办事处与现主责部门的角色含义仍须核准。 | 核组织身份、角色和转换后复用；不能仅凭同值删历史上下文。 |
| 26 / C099 | `source_project_close_time DATETIME(3) NULL` | `projectCloseTime` | project_close_time/closed_at；现生命周期Owner | 7个旧关闭日期，当前项目日期均NULL；旧关闭不证明现流程完成。 | 待与旧状态/事件语义一起裁决，优先正式历史；不制造现关闭。 |
| 27 / C100 | `source_project_code VARCHAR(45) NULL` | `projectCode` | project_code；正式来源业务编码关系候选 | 现项目编码生成后冻结，旧商业编码可能不同；不能覆盖。 | 核既有来源业务编码关联；既有技术主键关系不能替代商业编码。 |
| 28 / C101 | `source_project_key VARCHAR(128) NULL` | `projectId` | 既有项目来源身份绑定；目标原生id | projectId为旧技术主键，不能写成目标主键或当新增业务属性。 | 优先复用既有来源身份关系；核消费者和现值后裁决新增列必要性。 |
| 29 / C102 | `source_project_name VARCHAR(200) NULL` | `projectName` | project_name；既有名称编辑/审计Owner | 本批10/10相同；未证明独立历史差异。 | 优先复用正式名称；先核改名历史、全表差异和依赖，再提交清理。 |
| 30 / C103 | `source_project_refresh_time DATETIME(3) NULL` | `projectRefreshTime` | project_refresh_time；PROJ Owner候选 | 9个旧刷新日期，当前对应列全NULL；旧刷新时钟不等于本系统编辑时间。 | 待旧事件解释与正式历史位置裁决，不直接覆盖现日期。 |
| 31 / C104 | `source_project_start_time DATETIME(3) NULL` | `projectStartTime` | project_start_time；指派/启动Owner候选 | 9个旧启动日期，旧注释关联经理派工；不能直接推为新流程启动。 | 待旧事件解释与正式历史位置裁决，不绕过指派/启动规则。 |
| 32 / C105 | `source_project_state VARCHAR(11) NULL` | `projectState` | status/current_stage/lifecycle_status；状态机Owner | 旧项目状态与当前阶段/生命周期不是同一编码体系。 | 按已批准历史状态范围核正式历史承接；不直写当前状态。 |
| 33 / C106 | `source_project_type VARCHAR(45) NULL` | `projectType` | project_type/project_category候选 | 旧10/afss/afxx等值不能默认为当前类型/阶段编码。 | 核原字典和既有分类转换后裁决映射，暂不认可镜像列。 |
| 34 / C107 | `source_sales_type VARCHAR(25) NULL` | `salesType` | sales_type；既有属性Owner | 本批10/10同值，旧定义含01/02/14；已有正式字段。 | 优先复用正式值域/转换；清理前核历史与全表依赖，本轮不DROP。 |
| 35 / C108 | `source_signing_method VARCHAR(10) NULL` | `column011` | signing_method/project_category候选 | 旧列注释为项目分类，10值与signing_method直接比较0/10相同。 | 先核真实业务含义和分类字典，不据新列名猜成签约方式。 |
| 36 / C109 | `source_updated_at DATETIME(3) NULL` | `updateTime` | 现update_time；另核来源业务修订Owner | 9非空旧更新时钟，不是现系统编辑时钟。 | 完整保留原时间，核正式修订历史位置，不覆盖当前审计。 |
| 37 / C110 | `source_updated_by VARCHAR(45) NULL` | `updateBy` | 现updater；另核来源人员及正式历史Owner | 9非空旧人员编码，不等于当前操作者。 | 核来源人员身份和历史位置，不生成当前账号或权限。 |

公司编码已有裁决继续执行：01→001、02→002、03→003、04→004，未知码阻断。旧compId必须先解析公司身份，不能直接当作该映射的输入码。

本轮无新增表/列提案获默认批准，未改业务代码、未执行DDL/DML、未重启服务。


## 2026-10-10 已完成的四个名称重复列清理

按照已批准复用正式字段的裁决，核对全部138个项目：source_market_name、source_system_name、source_expend_name、source_industry_name各仅本批10条有值，分别与market_name、system_name、expend_name、industry_name完全相同；其余为空。代码和当前SQL已无这些重复列，索引、外键、视图、例程、触发器无列依赖。清理实际四列后，全部138行其他字段逐列不变，项目来源绑定不变；10项目40个正式名称经真实接口查询比对通过。项目表当前118列，此数字仅描述当前结构，不表示其他重复列已全部裁决实施完成。

证据：reviewed-project-name-mirror-cleanup-20261010.json、verified-project-name-mirror-cleanup-20261010T001716725649.json、project-name-mirrors-before-20261010T001716725649.json、verified-project-name-mirror-api-20261010.json。仅处理本任务开发库；未修改Flyway执行历史，整体基线对齐仍待处理。项目编码、生命周期、历史审计及其余重复列的正式承接继续，不声称已全部解决。

## 2026-10-10 正式项目主档迁移 Owner

沿后续已确认的裁决，新建可复用的 `DPPMS_PROJECT_MASTER` 迁移适配器及 PROJ Owner API，不新增物理表或列。原项目编码优先；公司先按 `compId → t_company.id → compCode` 精确解析，再执行已批准的四项公司编码转换。创建、更新人先复用 `DPPMS_USERS` 来源绑定，再由 SYSTEM 迁移接口按当前租户核对登录名；停用历史人员允许作为审计关联，原 NULL 不填本次操作者。创建、更新时间使用原值，原更新时间 NULL 仅回退本记录原创建时间。

原状态 100 按裁决初始化为 NORMAL_CLOSED/S6，20 为 EXCEPTION_CLOSED 并保留原原因与关闭时间，31/32不生成生命周期完成事实。初始化只适用于已明确接管、版本未变化的 ACTIVE 项目；既有关闭事实不覆盖。当前任务、计划、审批、人员任期及不可变来源证据不生成或改写。

37个原字段在迁移输入中逐列保留并核对。以下为已裁决的正式承接位置；本节覆盖前期审查中的待核建议，但不将未执行的结构清理写成完成。

| 原字段 | 正式位置或处理 |
| --- | --- |
| projectId | 已有项目来源身份绑定；INT 来源绑定关联正式项目 ID，不写成业务主键 |
| projectCode、projectName | project_code、project_name |
| projectType | project_type，10=用服售后项目；字典已补齐 |
| projectState、projectCloseTime | 已批准历史初始化策略；lifecycle_status、current_stage、closed_at、project_close_time |
| column001 | 正式 department_id/department_code 精确核对；不按名称猜配、不扩大授权 |
| compId | 正式 company_id/company_code/company_name，经旧公司外键解析和编码转换 |
| customInfo | 已批准 custom_info，完整 JSON 对象，日期及其他字段不混入此对象 |
| column009 | 已批准 order_create_time，保存项目创建时的原值 |
| disabled、effectiveFrom、effectiveTo | 已批准 disabled、effective_from、effective_to，原1表示无效，不新增审批或状态流程 |
| customerProjectName、salesType、majorProjectLevel | customer_project_name、sales_type、major_project_level，NULL及空字符串不合并 |
| column004、column005、column006、column007 | market_name、system_name、expend_name、industry_name |
| column008 | not_track_reason；原20没有原因时使用已裁决业务含义“不予跟踪” |
| column010、column011、column012 | project_category、signing_method、implementation_mode，复用已核实业务码转换；未裁决值阻断 |
| projectStartTime、projectRefreshTime | project_start_time、project_refresh_time，不据旧日期生成当前任务完成 |
| createBy、createTime、updateBy、updateTime | creator、create_time、updater、update_time，复用 SYSTEM 正式用户与历史时间 |
| column013 | 已批准 proj_project_party 最终客户角色，由相关方 Owner 承接，不覆盖直接客户 |
| column002、column003 | 正式 COM 合同／订单的 ERP 上下文；需以已有精确订单关系验证，不作为新的项目镜像字段 |
| column014、isback | 用户明确删除对应9/10镜像，当前主档不新增承接列；原字段及排除裁决保留在清单中 |
| columno12_readonly | 旧实施方式编辑控制，不生成新业务镜像或改变当前权限；转换依据保留在迁移方案 |
| customConfig | 本批全 NULL；非空记录无批准承接点时阻断，不能自行丢弃或放入备注 |

首次接管使用现有 SNAPSHOT 单事务预览／执行，逐项目提供复核版本；不重置绑定、不清空目标。重复运行须与上次正式结果及37原字段完全一致，发现人工修改、运行时修改、来源修订、业务编码冲突、未知转换或失效人员绑定时阻断。原表全量读取模板复用现有流式能力；全量首次接管须沿相同复核与授权规则执行，不以本批10条验证代替全历史验收。

相关54项检查通过：历史初始化8项、Owner12项、真实MySQL隔离写入2项、SYSTEM事务及人员身份13项、INT主档6项、原表真实查询1项、既有回款适配器12项。日期字符串经通用JSON读取会变成1970年的问题已由重复同步失败测试发现并修正；原失败日志保留。MySQL检查在连接内临时克隆进行，未写正式项目。完整宿主机JDK25打包通过；实际10项目的迁移及页面结果以随后落库证据为准。

### 正式执行及真实页面核对

任务2108608417343934465经新预览2108609412811657218复核后，执行2108609510287282177为SUCCESS/UPDATED10。落库逐项核对37原列及35项正式结果，原NULL审计人及历史时间正确，其他128个项目逐列不变，39张受保护项目表1093条运行时及历史记录不变；原来源也不变，无新增物理列。项目51922在旧预览后被改动的结束日期，按用户专项裁决保留2026-10-14，原预览及业务修改证据保留，未用过期版本覆盖。

重复预览2108609613223890945为PREVIEW_READY/UNCHANGED10，正式行、来源绑定及受保护记录不变。真实浏览器分别打开正常闭环项目189、异常关闭项目194及当前实施项目198：历史编码、启动/刷新/关闭时间、异常原因及公司显示一致；198保留的新结束日期在刷新后仍正确。未据历史闭环生成现平台17项任务完成事实。正式执行证据：verified-project-master-formal-carry-20261010.json、verified-project-master-repeat-protection-20261010.json；页面证据：project-master-normal-closed-browser-20261010.txt、project-master-exception-closed-browser-20261010.txt、project-master-active-retained-date-browser-20261010.txt、project-master-active-reloaded-browser-20261010.txt。其余项目镜像物理列、旧写入路径及全量基线对齐继续处理，尚未声称清理或上线完成。


### 2026-10-10 项目主表镜像撤回完成

33个其余任务新增镜像已撤回；结合此前4个名称镜像，任务库`proj_project`现为85列（原结构及已批准5列）。138行的全部保留列逐值不变；10项目正式承接保持，项目51922结束日期仍为2026-10-14。DO、关联资料查询及前端改为正式字段，不再引用这33个主表镜像；既有`source_type`属于原来源类别，不在本次撤回集合。当前SQL/migrations的项目主表定义仅包含已批准最终结构，未追加增加后删除的SQL。执行过的旧原文、Flyway历史、来源原37列和旧回执均保持，不执行repair。

清理前发现最终客户未正式落入相关方，已阻断清理并补齐：通过既有迁移功能、INT主档同步证明与PROJ相关方Owner创建10条`FINAL_CUSTOMER`关系，完整原名及有效期进入`proj_project_party`，不按名称猜配客户ID。新关系记录其迁入审计，项目自身历史审计已由主档同步承接。58条既有相关方逐列不变；138个项目主表和其他38张项目表保持不变。使用独立`PROJECT_HEADER_V1`纠正回执，不改写旧V4；10条重复调用为REPLAY_COMPLETED，无新增关系或证据修订。

ERP购货方原码和名称通过105条项目订单引用逐项核实，已在正式`com_sales_order.customer_code/customer_name`完整承接，不覆盖项目直接客户。最终客户由相关方承接；来源主键归既有PROJ来源身份及INT绑定。回退说明/回退状态按用户第9/10项删除裁决撤回；旧实施方式只读标识是旧编辑控制，不新增权限或业务镜像；本批customConfig全NULL，扩大历史时遇非NULL继续阻断待裁决。所有排除原因及原字段仍保留在本表37列映射中。

实际10个关联资料接口在清理前后完全一致；清理后INT重复预览2108616514351357953为PREVIEW_READY/UNCHANGED10。主档核对55项、项目头Owner及补承接32项、回执优先及旧版本保护3项、前端7项分别通过（不同集合有重合，不累加为独立总数）。真实页面发现新正式日期键显示毫秒数，已修正并回归；最终页面证据保存到project-formal-fields-after-mirror-cleanup-browser-20261010.txt。

受控证据：reviewed-project-master-mirror-finalization-20261010.json；verified-project-final-customer-formal-carry-20261010.json；verified-project-final-customer-repeat-20261010.json；verified-project-master-mirror-finalization-20261010T015204989114.json；verified-project-master-after-mirror-cleanup-preview-20261010.json。尚未完成其他业务表整改、实施范围105条迁入或全量基线校验，不宣称整体上线完成。
