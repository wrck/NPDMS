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
