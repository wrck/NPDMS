# 当前启用范围的角色权限与项目范围修复

日期：2026-10-17。环境：当前工作树、59191后端、19191前端。阶段：8448ff88及补丁82034277均已部署、最小角色授权已应用；项目经理客户保存及重开、巡检只读、服务经理BPM本人待办审批和历史读取已通过真实浏览器验收。已办页残留撤回按钮已修正，最终浏览器复验通过。

## 已批准范围

需求方要求按当前启用范围补齐客户维护、巡检和服务经理BPM审批，修复当前联调和闭环记录的项目范围校验，保留其他会话改动。不处理已明确未启用的到货验收候选、退役项目/阶段路径、旧Demo或已隐藏的归档旧入口；不新增业务角色、审批节点或跨项目授权。

## 修改

- 联调、闭环记录列表只使用PROJ返回的当前VIEW完整项目集合；空集合或显式筛选不可见项目返回空页。新场景Query对象承接分页和服务端范围，不再将Controller VO传入Mapper。
- 详情按记录实际所属项目及当前租户检查VIEW；创建、修改和状态命令在事务中取得MANAGE并锁定重验当前范围。禁止通过修改projectId搬移记录；原状态转换、乐观版本及终态保护保留。闭环记录创建只允许草稿，不能通过创建参数跳过审批。
- 客户扩展信息更新先验证原客户所属五维范围，再应用原CRM字段保护及变更规则；新授予角色不能伪造CRM_SYNC来源。空客户组织范围继续拒绝，不创建ALL范围或客户范围映射。
- 真实浏览器发现项目维护入口仍使用全局客户查询，导致具备项目范围的项目经理无法打开维护窗口。新增项目关联客户备注维护接口：客户ID只能由服务端当前项目关联取得，要求当前项目MANAGE、锁定重验范围及关联、项目ACTIVE、同租户、原客户版本和幂等键；只更新平台备注，复用CUS原历史、审计和CRM字段保护。全局客户范围不变。项目页替换为专用备注窗口，旧客户维护页面保持原逻辑。
- 巡检允许项目经理查看本人完整可见项目的任务；查询范围不增加项目，不因此获得任务执行、归档或管理成员资格，原责任人及服务经理写入检查保留。
- 新增服务经理BPM访问约束：只读取本人发起或参与的同租户流程，仅允许审批/驳回本人当前指派待办；不允许凭通用更新权限转派、委派、加减签、抄送、退回、撤回或从通用入口创建流程。已有BPM管理员权限仍按原规则处理，其他角色不受此角色增量影响。审批页同步隐藏本次未授予的高级操作。
- 当前正式项目详情的闭环记录按钮按已有create/update/delete/submit/audit权限分别显示，修复项目经理看到无权审批按钮的问题；不修改旧Demo共用组件，不新增闭环审批权限。

## 已应用最小授权

8448ff88部署且健康检查通过后，通过SYSTEM正式权限分配接口取最新集合并集，保留既有授权；不改角色、人员关联、data_scope、客户五维范围或项目责任事实。应用回读：项目经理162个菜单节点、131项有效权限；服务经理198个菜单节点、162项有效权限，两角色dataScope均保持5。回执：`.run/manager-permission-fix-applied.json`，日志：`.run/manager-permission-application.log`。

|角色|本次新增权限|应用前有效权限|应用后有效权限|
|---|---|---:|---:|
|项目经理|客户create/update；inspection-task:query、service-issue:query|127|131|
|服务经理|上述4项；客户delete/restore；巡检任务create/prepare/assign/cancel/execute/annotate/request-archive/archive、报告generate、规则query/select、问题remediate/confirm；BPM task:query/update及process-instance:query|140|162|

不给项目经理客户删除/恢复或BPM审批；不给两角色系统配置、客户敏感查看、巡检规则维护/发布/安全审核、流程模型维护、全部流程管理或设备凭据权限。BPM仅显示个人待办/已办，不增加通用“我的流程”创建入口。

准备清单：`.run/manager-permission-fix-plan.json`；原生API应用脚本：`.run/apply-manager-permission-fix.py`，应用前已检查运行包包含范围保护。客户补丁不需要追加角色授权。

## 验证

定向101项通过：BPM17项、客户31项、PROJ/联调33项、巡检20项。覆盖允许路径、跨项目/跨租户拒绝、空/占位范围、显式越范围筛选、原Owner不能被新projectId替代、范围锁重验撤销、客户越范围修改/CRM来源伪造、BPM他人/未指派待办与无关流程拒绝，以及既有状态机、幂等和历史保护。

日志：`.run/manager-permission-tests.log`（BPM、客户通过；最终巡检结果以独立日志为准）、`.run/manager-permission-owner-tests.log`、`.run/manager-permission-service-tests.log`。初次BPM链式查询替身、巡检测试责任人夹具已修正；客户既有创建夹具缺少当前必填客户级别，已补合法测试输入，未降低业务校验。

BPM操作组件通过当前Vue SFC编译器检查。浏览器发现的项目客户补丁新增41项后端检查通过（控制器3、客户命令24、生命周期6、项目概览8），覆盖项目授权、锁重验撤销、关联客户变更、关闭项目、跨租户、版本冲突和只更新CRM客户备注；前端11项检查通过（客户窗口及概览6、HTTP环境UUID兼容5）。日志：`.run/manager-permission-project-customer-tests.log`、`.run/manager-permission-project-customer-front.log`。首次补跑前端命令未使用项目测试配置，CSS加载失败；使用`vitest.pms-file.config.ts`后通过，未降低断言。

项目经理y06496（杨凯添）真实浏览器验收，非管理员替代：

- 本人项目992203060198的业务联调列表可读取，未出现权限失败，证据`manager-permission-y06496-joint-allowed.txt`。
- 巡检列表显示7条本人项目任务，能打开详情；无新增入口，上传、解析、完成执行按钮禁用，证据`manager-permission-y06496-inspection-readonly.txt`。
- 闭环记录能读取既有审批中记录；初次发现显示无权通过/驳回按钮，修正正式页配置后按钮消失，前后证据`manager-permission-y06496-closure-allowed.txt`、`manager-permission-y06496-closure-role-actions.txt`。
- 82034277部署后，新客户窗口打开、保存成功提示、关闭后重新打开均正常。保存原有空备注，不改变业务含义；客户名称/编码及CRM资料只读，仅编辑平台备注。证据`manager-permission-y06496-customer-save-reopen.txt`。正式客户API只读回查：客户2102227613952667649，version=1，remark为空串，sourceType仍为CRM_SYNC。
- 未通过浏览器写入联调/闭环业务记录。联调和闭环跨项目拒绝目前以失败测试证明。

需求方明确授权直接切换会话，已正常登录真实服务经理z01010（周文成），取消记住我。按需求方要求由GPT-6.1 Sol低推理模式执行浏览器机械操作；权限判断、样例边界和结果复核仍由主代理负责。最后一次子执行器连接失效，主会话按其提供的最小操作桥接完成复验，并由执行器读取落盘证据复核通过，未要求需求方重复登录。

服务经理真实浏览器验收：

- 个人待办和已办正常加载，初始均为空；菜单仅个人待办/已办，无流程模型或全部流程管理。
- 复用已发布独立验收定义`IT_DEFAULT_BUSINESS_20261012`，无PMS业务businessKey、无定义/节点变更。正式BPM API新建两条本次标识的隔离流程，其中一条由admin正式转办给z01010，另一条仍由admin持有作为负例，不修改既有项目审批。
- 本人流程`1776131e-c9df-11f1-9092-00ff0b78abd7`，任务`177ca2d9-c9df-11f1-9092-00ff0b78abd7`。待办仅出现本人一条；详情可见通过/拒绝/评论，无转办、委派、加减签、退回、抄送、撤回。真实浏览器通过后待办移除、已办新增，历史可重开、无重复审批按钮。正式API回查流程与任务均status=2，实际办理人z01010、审批意见准确，businessKey=null。证据`manager-permission-z01010-bpm-todo.txt`、`own-detail.txt`、`approved.txt`、`todo-after.txt`、`done-list.txt`、`history.txt`（均具有相同文件名前缀），汇总`manager-permission-z01010-bpm-result.json`。
- 他人隔离流程`17d12b83-c9df-11f1-9092-00ff0b78abd7`在真实浏览器显示“没有该操作权限”，不返回流程内容，证据`manager-permission-z01010-bpm-foreign-denied.txt`。另用真实z01010原生API验证无关流程详情/任务/模型/打印、他人指派待办审批、通用创建、全部任务管理、全部实例管理共8项均403，证据`manager-permission-z01010-bpm-live-denials.json`。
- 负例验收完成后由admin正式取消，仅操作本次新建且核验标识的隔离流程，status=4，历史保留。未直接写库或删除审批历史。
- 发现已办列表仍显示“撤回”按钮，服务端已有高级动作拒绝保护，未点击该按钮。正式已办页已按详情相同的服务经理限制隐藏，并要求既有task:update权限；只改前端，无需重启后端。最终真实浏览器复验：操作列仅“历史”，“撤回”消失，历史仍显示审批通过、周文成及准确意见，无重复审批按钮。证据`manager-permission-z01010-bpm-done-role-actions.txt`、`manager-permission-z01010-bpm-history-role-actions.txt`；原缺陷证据保留。

验收边界：真实浏览器验证本次上述可用入口和隔离BPM审批闭环；联调/闭环跨项目与跨租户写入保护、客户锁重验及版本冲突等以定向失败测试为证据，不宣称对现存正式项目执行过破坏性越权或审批操作。59191两次重启均取得需求方明确批准，48182不操作。

## 部署记录

首轮完整构建、ValidateOnly及既有22项运行修复保护通过。需求方明确批准后使用固定脚本部署`.run/build/releases/8448ff88-8027-4a34-80ce-f62efe129dac/manifest.json`，配置`local,dac`，健康检查通过，进程PID26400。日志：`.run/manager-permission-build.log`、`.run/manager-permission-deploy-validation.log`、`.run/manager-permission-deployment.log`。

客户补丁完整构建、ValidateOnly及既有22项保护通过，冻结清单`.run/build/releases/82034277-58a8-4c3e-8705-9f7bd01b60b6/manifest.json`。需求方再次明确批准“批准部署82034277并继续验收”，固定脚本部署成功，健康检查通过，配置`local,dac`，当前进程PID34592。日志：`.run/manager-permission-project-customer-build.log`、`.run/manager-permission-project-customer-deployment.log`。

48182保持不动。其他会话已有Feature文档、验收记录和临时文件保留在工作区，不纳入本次提交。本次只作本地提交，不推送Git。

## 工勘录入关联地点权限补丁（2026-10-17，已部署验收）

需求方报告项目992203060198打开工勘录入出现两次“没有该操作权限”和一次“客户不存在”。重新登录服务经理z01010后真实复现，排除已失效会话的干扰。三条请求均HTTP 200包装业务失败：`/pms/asset-locations/sites/page`与`/addresses/page`的业务码403；`/api/v1/pms/customers/by-code?code=162007000034`的业务码1014001000。证据`manager-permission-site-survey-open-before.txt`。项目关联客户仍存在，问题是全局地点查询和全局客户组织范围不适合项目内录入。

修复：新增`GET /api/v1/pms/projects/{projectId}/customer-overview/locations`，复用既有PROJ VIEW及当前关联客户检查，只有当前客户启用站点、站点所引用地址及客户当前地址引用可读；可选siteId须属于当前客户站点才读取任意深度位置树。租户与操作者来自服务端，不接受客户端customerId。CUS只经AST公开API读取地点，AST新增带租户及精确客户条件的场景查询；不读取他模块业务表、不授予全局资产地点权限、不修改角色或数据范围、不改原工勘保存规则。

正式`delivery-business/site-survey/index.vue`对地点选择器启用项目查询模式，其余消费者及旧工勘页面保持原路径；查询失败显示错误且不回退全局接口，原地址ID/版本引用保留。未改工勘模板、历史字段或已归档记录。新API契约记入`docs/design/10-api-design.md`。

定向后端31项通过（项目地点组合8、项目客户读取8、方法权限4、AST地点API11），日志`.run/manager-permission-site-survey-backend.log`。前端40项通过（新项目地点4、原地点引用2、当前工勘上下文34）；增强无项目用例断言后项目地点4项复跑通过，日志`.run/manager-permission-site-survey-front.log`、`.run/manager-permission-site-survey-scoped-front.log`。覆盖无关联客户空集、无项目不查全局、范围拒绝后零地点读取、跨租户引用拒绝、他客站点及位置树拒绝、保存地址引用版本不变，以及当前工勘原有上下文/回执行为。

完整构建成功、既有22项修复保护及固定部署脚本ValidateOnly通过；冻结清单`.run/build/releases/5792a363-edf2-41b8-9d15-abfecd9c8633/manifest.json`。需求方明确批准“批准部署5792a363并验收”后，经固定脚本重启59191并部署，配置`local,dac`，健康检查通过，PID6684。日志`.run/manager-permission-site-survey-build.log`、`.run/manager-permission-site-survey-deploy-validation.log`、`.run/manager-permission-site-survey-deployment.log`。48182未操作，未新增角色授权。其他会话项目成员字段等工作保留，完整产物包含这些既有源码改动，已在本次批准问题中明确说明；本补丁未提交或推送。

部署后真实浏览器验证：

- 服务经理z01010正常登录后，首页显示周文成，工勘人员默认周文成；项目经理y06496正常登录后，首页显示杨凯添，工勘人员默认杨凯添。两者均以真实角色会话验收，不使用管理员替代。部署后初次浏览器实际为管理员，因此该轮结果未计入；正常退出并分别重新登录后采集以下证据。
- 两角色打开项目992203060198的新增工勘，均不再出现两次“没有该操作权限”和“客户不存在”。实际页面请求只使用项目地点新接口，业务code=0，返回关联客户2102227613952667649的22个站点；不再调用两个全局分页及按编码客户查找。证据`manager-permission-site-survey-z01010-open-after.txt`、`manager-permission-site-survey-y06496-open-after.txt`，含DOM与本轮请求摘要。
- 两角色的已有站点下拉均显示当前客户选项；选择现有站点后可展开“园区→楼栋”任意深度位置树。证据`manager-permission-site-survey-z01010-site-options.txt`、`manager-permission-site-survey-z01010-location-tree.txt`及对应y06496文件。只变更未保存输入，随后按页面确认放弃，未新建站点或保存工勘。
- 项目经理重开已有归档工勘，原日期2026-10-09、原地点、历史表单值可读且编辑控件禁用，底部仅有关闭按钮。证据`manager-permission-site-survey-y06496-archived-detail.txt`。本补丁未改模板、原历史或归档状态。

两角色原生API实测分别确认当前客户22个站点、22个地址、选定站点2个位置节点；全局站点/地址列表仍403，伪造他客站点930835仍403，无权项目2110018733130579976仍1014024033（项目范围拒绝），未返回地点数据。证据`manager-permission-site-survey-z01010-api.json`、`manager-permission-site-survey-y06496-api.json`。只读检查由`.run/manager-permission-site-survey-api-check.py`经正式认证/API执行，不读取浏览器认证存储，不持久化密码或Token。


## 工勘保存入口权限补齐（2026-10-17，运行授权已应用）

保存失败日志明确为 BusinessModelController.execute 的方法权限拒绝：用户2108539871029019609访问 `/api/v1/pms/business-models/SOL/siteSurvey/operations/create` 被拒绝，尚未进入工勘Owner。两经理角色已具备工勘create/update，但缺少V402定义的通用操作路由门槛。按当前启用范围，仅通过正式角色授权API为SERVICE_MANAGER、PROJECT_MANAGER增加隐藏按钮970000000000099224（pms:business-model:operate）。原角色菜单取当前并集保留，角色dataScope及dataScopeDeptIds不变；未增加工勘delete、通用模型query/管理或其他原生业务权限。记录 `.run/manager-survey-operation-gate-applied.json`。通用守卫仍要求该入口权限与Owner具体操作权限同时成立，工勘Owner继续检查MANAGE项目范围及执行入口。

真实浏览器使用项目经理y06496，在独立诊断标签创建隔离草稿2111367504712691713（地点与备注MANAGER-SURVEY-GATE-20261017），收到code=0、outcome=SAVED、version=1并正常重开。要求结束日期使用项目原值2026-10-14，回读确认项目日期未变。用户原标签及未保存输入未操作。

两角色经正式登录/API，对同一隔离草稿的删除均403，对各自不可见项目的创建均403，拒绝后原草稿版本及内容未变。证据 `manager-permission-survey-operation-gate-check.json`。验收草稿随后以现有管理员权限经正式Owner删除命令清理，回执deleted=true；未删除原业务记录。工勘任务2107838747846483970上下文仍无recoverableError，原归档对象关联保留。无服务重启，48182未操作，其他会话源码改动保留。

删除后工作区异常仍未复现：当前项目工勘任务已完成，经理工作台为只读，原有归档关联可正常读取。本轮草稿清理使用正式Owner API，不等同原阶段/任务删除按钮的浏览器闭环；需对准需求方原账号及项目继续定位，不能据此宣称该缺陷已修复。


## 阶段/任务工勘删除后工作台异常（2026-10-17，部署前记录，最终结果见下节）

需求方随后明确异常项目为16200723052503N，实际匹配项目16200723052503N-0（992203060197），S1现场工勘任务2107837711337496578为IN_PROGRESS。正式API创建隔离草稿2111369731665817601，确认其已自动关联任务，再通过既有管理员权限执行Owner删除；成功回执SAVED返回后立即读取business/context与workbench，二者均返回recoverableError=OWNER_FACT_UNAVAILABLE。证据 `manager-permission-survey-delete-repro.json`。隔离草稿已经删除，用户原对象2111369598907707393未修改。此证据替代此前在错误项目992203060198未复现的边界，原记录按时间保留。

根因：Owner删除只排入异步规则重评，提交瞬间仍有活动关联指向已删除工勘；工作台同步检查Owner事实时失败。修复在SOL原生删除和PROJ当前引用清理之间建立同一事务：新公开内部API由Owner在所有原授权/范围/状态/委外引用/版本检查及删除成功后调用，PROJ按认证租户/操作者、项目MANAGE范围、有效计划及当前未结束执行轮次，精确关闭同Owner/类型/对象ID的阶段和任务活动关联，使用现有关联CAS、取消关联时间及审计保留原行。旧计划、已结束轮次及其他对象均不清理。清理冲突导致Owner删除、回执、审计和Outbox整体回滚。旧工勘接口/实现不改，原异步规则重评保留，不直接写生命周期状态、不增加角色授权。内部契约已追加至10-api-design。

固定工具链及工作树锁下49项定向测试通过：真实Mapper SQL的内存数据库验证1、删除引用服务6、既有自动关联11、Owner写入口10、Owner数据库事务与回执16、装配1、原写权限3、原操作入口策略1。覆盖租户/项目/Owner/对象隔离、阶段与任务、结束与旧轮次排除、空权限拒绝、CAS冲突回滚、Owner删除与引用及回执整体回滚、同幂等键重试。SQL测试仅将MySQL位字面量转换为嵌入库语法，不等同真实MySQL或浏览器验收。日志 `.run/survey-deletion-backend-tests.log`。

完整后端构建成功，既有22项运行修复保护通过；冻结产物 `.run/build/releases/c643da0e-484c-49d3-97d2-165354bd15ca/manifest.json`，固定部署脚本ValidateOnly通过，未修改运行服务。日志 `.run/survey-deletion-build.log`、`.run/survey-deletion-deploy-validation.log`。该产物包含当前工作树已有项目成员等后端改动，其他会话源码未覆盖。待本次59191重启批准后部署并验证真实项目工作台；48182保持不动。本修复未提交或推送。

## 最终部署与原项目权限复测（2026-10-17）

需求方批准c643da0e后，固定部署脚本完成59191部署，local,dac、PID18752、健康检查通过，日志`.run/survey-deletion-deployment.log`。48182未操作；保留其他会话改动，未提交或推送。

原项目16200723010901N-0（992203060198）分别使用真实项目经理y06496与服务经理z01010重新登录复测，证据统一为本目录`full-permission-retest-*`：

- 两角色指定的归档文件、实施范围、交付范围工作台、合同订单工作台入口均隐藏。客户维护以原空备注保存并重开成功，CRM来源字段仍只读；证据`*-navigation.txt`、`*-customer-save-reopen.txt`。
- 两角色工勘隔离草稿创建、保存、回读成功；关联地点仅返回项目客户范围，全球地点仍拒绝，他客站点及无权项目拒绝。经理删除仍403，无权项目创建仍403。隔离草稿已由正式Owner命令清理，项目原截止日期不变；证据`*-survey-saved.txt`、`*-site-survey-*-api.json`、`full-permission-retest-survey-operation-gate.json`。
- 原报告项目197的工勘任务已被实际业务完成，未修改或重开历史；使用项目199当前活动任务关联隔离草稿后删除，连续5次立即读取上下文和工作台均无Owner事实缺失，原引用未变。原项目198完成任务也连续读取正常，并在真实浏览器重开工作台。删除采用正式Owner API，未宣称经理浏览器具备删除按钮。证据`full-permission-retest-survey-deletion.json`、`full-permission-retest-y06496-survey-task-after-delete.txt`。
- 联调页面稳定加载；闭环原审批记录可读，项目经理无审批动作，服务经理保留已有审批入口，未操作既有审批。两角色跨项目分页为空、他项目详情/创建/伪造Owner更新拒绝，隔离负例原值未变且已清理。证据`*-joint-stable.txt`、`*-closure.txt`、`full-permission-retest-owner-scope.json`。初次joint快照尚为上一面板，不计入通过证据。
- 巡检原项目7条可读，实际详情ID18；项目经理仅查看，服务经理可选文件及执行其已有动作，未选文件时上传解析仍禁用。无权项目列表为空，项目经理执行拒绝。未上传、执行设备命令或完成原任务。证据`full-permission-retest-inspection-api.json`、`full-permission-retest-inspection-boundaries.json`、`full-permission-retest-y06496-inspection-detail-valid.txt`及服务经理详情文件。以任务编码误作ID的初次404不计入验收。
- 服务经理真实浏览器办理本次独立隔离BPM流程35e97c3e-ca04-11f1-af8b-00ff0b78abd7，通过后正式回查流程与任务status=2、实际办理人为z01010、businessKey为空。已办及历史正常，无撤回或重复审批。手动搜索刷新后待办为空，证据`full-permission-retest-z01010-bpm-todo-refreshed.txt`；早期todo-after为缓存画面，不作为移除证据。他人流程内容不返回，8项越权API均403，负例流程已正式取消；通过的审批历史保留。证据同前缀`bpm-result.json`、`bpm-history.txt`、`bpm-live-denials.json`。

### 需求分析附件入口与展示调整

上传组件隐藏的原因是角色原有文件按钮位于未授予的基础设施祖先下，登录权限元数据过滤了这些按钮；后端已有文件授权。通过正式SYSTEM菜单/授权API在已授权PMS根目录下建立同权限隐藏按钮别名，仅映射两角色已经拥有的query/upload/download/preview/archive。有效权限键集合前后完全相同，数据范围不变，未授予基础设施菜单、全局文件管理或file:manage。运行配置立即生效，不重启。前向种子`V498__pms_business_file_permission_entries.sql`补齐可重复初始化；MySQL只读候选SELECT及执行计划验证通过、当前候选0条，未执行迁移DML。证据`full-permission-retest-file-entry-grants.json`、`full-permission-retest-file-entry-seed-readonly.json`。

两角色重新登录后真实浏览器均显示草稿文件选择组件；未选文件时“上传并绑定”禁用。完成版本继续只读、无上传入口。未向用户草稿上传测试文件或保存正文。证据`*-requirement-upload-restored.txt`、`*-requirement-frozen.txt`。

按后续明确要求，需求分析移除展示模板选择栏，干净草稿默认取既定模板最新兼容版本；已产生本地修改时不自动换表单，完成版本保持原布局。草稿第6项展示名改为“6、客户当前运行业务情况”，不修改持久化历史模板或业务字段。真实浏览器确认新标题、模板选择栏消失、上传入口保留及冻结版只读；证据`full-permission-retest-requirement-simplified.txt`及`-frozen.txt`。EntityForm定向运行测试11项通过，覆盖最新模板、未保存值、授权和历史只读；首次未指定项目配置的CSS加载失败不计通过，改用既有vitest.pms-file.config.ts执行成功。用户原标签及未保存输入未主动刷新、保存或丢弃。
