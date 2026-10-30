# BPM 短信依赖补查

2026-10-24，接用户报告“短信模板不存在”后检查。

**最新状态：用户最终明确批准“补充源环境的短信配置”。已通过原生配置 API 补齐来源渠道及四个 BPM 模板，并完成内容与运行选择接口核验。代码与测试修改仍保持撤回，未部署包、未重启、未发送测试短信、未重放审批。下文 32 项通过仅为已撤回代码方案的试验记录。**

## 已确认

- 独立测试后端 59286 在 13:56:12.950 记录 `SMS_SEND_TEMPLATE_NOT_EXISTS`，错误码 1002013002。调用链为流程完成 → `BpmMessageServiceImpl.sendMessageWhenProcessInstanceApprove` → `SmsSendServiceImpl.validateSmsTemplate`。
- 通过通知使用模板编码 `bpm_process_instance_approve`；同一 BPM 模块还依赖 `bpm_process_instance_reject`、`bpm_task_assigned`、`bpm_task_timeout`。
- 测试库四个 BPM 模板均不存在，未删除的短信渠道为零。来源库四个模板均绑定 ID 4 的 `DEBUG_DING_TALK`，不是可直接迁入的真实短信渠道。未读取、输出或复制渠道密钥。
- 当前代码仅对“收件人手机号缺失”进行不阻断处理；模板缺失按现有明确测试契约继续抛出，可能回滚审批事务。没有通过吞异常、直接改审批状态或重放用户审批掩盖此缺口。

## 本次处理边界

之前的 162 项配置检查不覆盖短信依赖，不能作为完整 BPM 运行闭环已验收的证明。该遗漏已确认，需要补齐真实渠道及四个模板，或者由用户确认测试环境不发送短信时的通知故障隔离策略后调整工程实现。

遵守用户“不启用草稿或模拟验收配置”的限制，尚未迁入或启用 DEBUG 渠道；也未虚构真实供应商模板号、签名、密钥或通知送达结果。真实渠道不可从目标现状推定，等待用户提供配置来源或明确测试环境策略。

原始无密钥快照为 `.run/project-delivery-recheck-20261024/applied/sms-before.json`，只含模板内容及渠道 ID、类型、状态和签名。

## 用户裁决与代码修复

用户后续明确：“没启用配置就触发不要阻塞代码”。据此，BPM 可选短信通知入口对收件人手机号缺失、短信模板缺失、短信渠道缺失三类情况记录带错误码、用户 ID 及模板编码的跳过告警，不阻断审批及 Owner 结果事件。停用模板或渠道沿用短信服务现有“不发送、仅记录日志”行为。没有创建或启用 DEBUG 渠道，没有改变通用短信服务和验证码接口，也没有吞掉已配置短信的参数错误或其他发送异常。

原先“模板缺失应抛出”的治理测试已由本次明确批准的通知策略替换；真实参数错误仍须抛出，授权、候选人、状态机、人工审批、不可变历史及事务失败测试继续保留。失败测试日志为 `.run/project-delivery-recheck-20261024/sms-before-fix.log`：32 项中 8 项断言失败、6 项流程错误，均暴露模板/渠道缺失阻断。修复后验证结果另行追加。未获得本次重启批准前，不替换 59286 运行进程。

修复后通过固定 JDK 25、工作树 Maven 依赖仓库及同一构建锁执行：`BpmMessageServiceImplTest` 20 项、`BpmNormalClosureFlowableTest` 12 项，合计 32 项，失败 0、错误 0、跳过 0。日志为 `.run/project-delivery-recheck-20261024/sms-after-fix.log`。Flowable 场景包含实际双节点人工审批、驳回、撤销、候选篡改、授权撤回、Owner 失败和历史一致性；通知替身改为真实缺失模板错误，确认该条件不再破坏流程事务。未修改通用短信服务，停用配置不发送的既有分支保持原样。

## 仅修改定义文件的可行性核查

当前 `BpmProcessInstanceEventListener` 是全局 Spring 组件，订阅 PROCESS_COMPLETED 并调用 `processProcessInstanceCompleted`。后者在 APPROVE/REJECT 状态下无条件调用 BPM 短信消息服务，之后才发布业务结果事件及执行可配置的 `processAfterTriggerSetting`。模板缺失异常实际来自此全局调用，不来自定义中的 HTTP 后置通知或自定义执行监听器。因此清空流程定义的 triggerSetting 不会消除当前故障；当前代码没有供定义使用的短信启停属性。未新增无效的 XML 属性、删除业务完成监听器、改变审批状态或直接改 Flowable 运行表。

结论：在当前既有实现下，单改 BPM 定义文件无法关闭这条全局短信调用。保持“不改代码、无模拟渠道”的约束时，仍需有效的真实短信配置才能通过既有校验；本次未擅自创建或启用渠道，原故障未宣称已解决。

## 最终执行：按来源补齐短信配置

用户后续明确授权补充源环境短信配置，本次据此迁入四个 BPM 模板所依赖的来源 `DEBUG_DING_TALK` 渠道。未迁入不被这四个模板引用的阿里云或腾讯渠道。通过 `/admin-api/system/sms-channel/create` 与 `/admin-api/system/sms-template/create` 正常创建，保持来源启用状态；渠道目标 ID 为 1，四个模板目标 ID 为 1～4，引用全部指向目标渠道 1。

四个编码为 `bpm_task_assigned`、`bpm_process_instance_reject`、`bpm_process_instance_approve`、`bpm_task_timeout`。逐项读回确认模板类型、编码、名称、正文、参数顺序、备注、供应商模板编号、启用状态及渠道引用与来源一致。渠道属性在内存中比对，凭据值未写入迁移证据或输出；本次仅接受与仓库既有公开示例完全一致的来源凭据，未引入额外真实渠道密钥。目标既有记录未被覆盖。

运行后端的已启用模板选择接口已返回上述四个编码。短信模板查询的 `@Cacheable` 明确 `unless = "#result == null"`，此前缺失不形成空结果缓存，新增配置无需重启生效。短信渠道为来源调试通道，不代表真实运营商短信可送达。没有调用发送接口、触发业务审批或修改历史，原审批需从正常业务入口重新办理，未宣称其已完成。

执行脚本为 `.run/project-delivery-recheck-20261024/migrate-source-sms.py`；无密钥逐操作回执为 `applied/sms-migration-events.jsonl`，一致性结果为 `applied/sms-migration-verification.json`，运行选择接口结果为 `applied/sms-runtime-selection.json`。Java 源码及相关测试与本轮修改前一致。
