# 项目992203060198割接审批配置修复

用户反馈割接上线出现“该审批使用原模块业务表单，请通过业务页面绑定办理；不能以空表单替代。”，并授权安全修复完成后运行重启。

原任务绑定PMS_DELIVERY_CUTOVER_APPROVAL，其流程定义使用formType20业务表单。保留原流程及前端业务表单保护。根据已存在的项目任务描述及Q-TPL-FLOW-20260922-004决议，配置独立pms-cutover-done-approval项目任务审批：从当前项目任务发起，由有效服务经理人工审批，作为外部CUTOVER_COMPLETED事实接入前的过渡依据。没有把原业务表单替换为空表单，也没有认定旧割接业务表单已提交。

新增BPM Owner适配CutoverTaskApprovalGuard、独立BPMN及7项隔离Flowable/H2测试。通过既有公开ProjectNodeExecutionApi、ProjectParticipantFactApi、ProjectScopeApi及AdminUserApi校验并锁定当前项目任务执行轮次、租户、项目有效服务经理和数据范围；固定身份使用引擎identity link保存，审批前重验，防止变量篡改、转派及审批人变更；要求真正人工审批。原业务实现不变。7项测试全部通过，日志.run/cutover-approval-tests.log。

联合发布清单.run/build/releases/f43cd7b8-a9df-45a4-aae8-dde842d6a11e/manifest.json构建及发布校验通过，22项既有修复保持。按照用户明确授权，通过scripts/worktree-backend.ps1重启59191，local,dac，PID19724，backend healthy。DAC未执行启停，独立readiness HTTP200。

通过正式BPM配置接口保存并发布独立模型abf43099-c49e-11f1-9893-00ff0b78abd7，流程定义pms-cutover-done-approval:1:39b31dd1-c49f-11f1-844a-00ff0b78abd7，formType10、active。引擎发布重排extensionElements及XML命名空间，按完整流程节点/属性/表达式/监听器语义比对一致，仅忽略扩展元素序列化顺序及图形布局，不放松审批契约。

完成联系人事务修复后，992203060198另建项目计划，通过正式预览、生效改动唯一直接节点task:DEPLOY_CUTOVER的审批定义引用，其他节点仅由原能力重新判定，无交付件、里程碑或门禁定义修改。当前有效计划2104870198458802217，联系人正确绑定保持。配置回执.run/cutover-approval-config.json。公共模板993009900016相同引用只更新草稿，Compiler valid=true、issues=[]，未发布，也没有扩展修复其他项目的割接绑定。

真实浏览器在localhost:19191打开项目割接上线：正常显示任务审批办理、审批状态未发起、提交审批及流程图；流程节点为发起人→服务经理审批→结束，原业务表单提示消失。没有发起真实割接审批或代服务经理作业务决定；浏览器验证覆盖原报错消除及配置入口，人工审批闭环由隔离Flowable测试验证。23条原有已结束执行证据逐条比对保持一致。未提交或推送Git。
