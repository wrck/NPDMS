# F-AST-001 设备序列号档案与时态归属

> Feature实施状态：`REVALIDATION_REQUIRED`
> 总体工程阶段：`IMPLEMENTATION_AUDIT`
> Feature Ready Gate：`PASS / SPEC-FAST001-FEATURE-READY-20260825-01`
> Technical Plan Gate：`HISTORICAL_RECORD_NOT_IN_MASTER_TASK_CHAIN`
> Implementation Done Gate：`PENDING_MASTER_REVALIDATION`
> 当前阻断：`历史实现证据存在，但此前缺少master Feature Task，不能由Spec或证据反推Done`
> 当前任务：`建立独立Delivery Unit，逐项复核a9f8b7c5证据与master当前代码后申请Done`
> Requirement ID：`EQP-01（V1）`
> Feature Spec：`specs/features/F-AST-001-device-serial-archive-and-temporal-assignment.md`
> 历史实现证据：`a9f8b7c5`

## 权威状态纠偏

- Feature Spec继续唯一维护Ready和业务Scope，不再维护Implementation状态。
- `a9f8b7c5`、自动化、MySQL、查询计划和浏览器记录均保留为历史候选证据；本任务建立前没有权威Feature Task，因此当前不得派生Implementation Done或EQP-01覆盖完成。
- 重新裁决必须基于master当前代码、完整DoD、旧设备写边界和全部适用证据，不重做无关测试，也不修改已冻结业务语义。

## 待完成

- [ ] 在master创建F-AST-001复核Delivery Unit并声明精确边界。
- [ ] 核对历史提交、当前master实现、Feature Spec和适用测试是否一致。
- [ ] 复核普通角色旧写退役、`super_admin`旧模型受控写及无AST代理/双写边界。
- [ ] 在master最终状态执行适用验证与Code Review。
- [ ] 只有独立Done Gate通过后，才把本文件更新为`IMPLEMENTATION_COMPLETE`并重新生成追溯投影。

本记录只补齐缺失权威链，不否定或覆盖历史证据，也不提前宣布Feature完成。

## 2026-10-10 已批准软件维护及历史专项增量

需求方已明确软件版本由项目人员手工维护或上传日志自动回填，与ITR无关；BOOT取Basic Bootloader，按登记/接收时间保护较新版本。日志回填及解析历史已在主工作区具备，本任务不重复建设。正常软件历史使用具名实体列；原DPPMS历史仅一次性迁移。

用户已整表批准[31列历史实体及八项正式扩展](../../docs/generated/2026-10-10-dppms-software-version-maintenance-table-decision.md)：当前表26列不变，V414观察表整理为ast_device_software_version_record，新增九列、撤回十二镜像、八项Bak/Change转正式实体扩展；初次维护已有当前组合时保存具名初始记录。635条旧事件不包含在纠正中。批次11列以及659条软件记录、6条批次的限定纠正已另获用户批准，未与本次结构批准混用。

- [x] 双工作区入口、日志写入链、真实表结构及原28字段核对，旧备份串/原标志差异核实。
- [x] 正常历史31列、九新增、八扩展及整表规则获得用户批准。
- [ ] 完成正常手工维护、授权/并发/事务、具名历史与真实日志事实接入；先接收主工作区已具备的日志增量，不另造解析Owner。
- [ ] 完成扩展定义初始化、失败验证和真实浏览器手工维护/日志回填/历史比较验收。
- [ ] 通过迁移/同步功能预览、一次性承接原28列、重跑防重及原当前值/原生历史/审计保护。
- [ ] 具体数据纠正及生产迁移基线按各自裁决收敛；旧批次048已批准11列，正式Owner及SQL基线已整理，真实落库仍待验证。

需求方已授权按清单直接复用主工作区未提交的日志增量，覆盖本次交接顺序；仅接收本任务所需差异，来源工作区保持不动。日志已接入软件历史Owner。正常Owner及限定纠正39项、真实MySQL事务/回滚11项、前端26项通过；本轮另有已完成解析查询保护30项通过，完整后端包通过。59282实际手工保存、重开、前记录比较、保存后保留页签和已有日志状态/解析结果页面已验证。八项正式扩展已初始化，665条纠正预览和原28/9字段完整匹配，历史纠正写入仍为0。新日志上传/下载尚受指定库缺少既有V398材料version列阻断，等待用户同表裁决；正常日志回填验收完成前不执行659＋6限定纠正。以上为本工作树专项实施进度，不改变master Feature Done或原复核状态。

## 2026-10-11软件专项真实执行

指定59282/19082任务实例完成V398及材料保护、日志材料Owner/票据地址修复；15项材料与32项下载测试通过。正式API上传，真实浏览器解析、刷新当前版本、查询历史并比较，BOOT取Basic17.05.16；不宣称浏览器文件选择器上传验收。659条旧记录及6条批次随后通过迁移Owner完成限定纠正，31列及八项扩展/11列逐值读回，ID保留，5次重放通过；旧观察表及12批次镜像已核查后撤回。带实际登录上下文MySQL测试纠正了先前无认证测试对NULL审计保护的证明缺口；普通填充不关闭。425主档和51解析事实未被历史纠正修改。635条旧事件随后按独立批准经迁移入口及AST Owner撤回，保留custom_info，清理其余两列，最终30列；44项定向测试及13项真实MySQL测试通过，清理后5次撤回及5次原承接重放通过，正式659×31＋5272属性和6×11字段重新核对一致。真实页面重新读取正常维护/日志及迁入历史。不据此宣布Feature或全历史迁移完成，生产历史基线仍待处理。详细证据集中于软件专项整表裁决及实际承接记录。
