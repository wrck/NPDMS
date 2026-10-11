# F-IMP-005 业务联调配置收集

> Feature实施状态：`NOT_STARTED`
> 总体工程阶段：`FEATURE_READY_BLOCKED`
> Feature Ready Gate：`NOT_READY`
> Requirement：`EXE-04@V1=FULL`
> Feature Spec：`specs/features/F-IMP-005-joint-debugging-configuration.md`

## 当前最小工作单元

- 审评F-IMP-004基线事实、INT-12/show tech模板契约、旧联调映射及`JointDebuggingCompletionFactApi`。
- Ready前不进入Technical Plan或实现。

## 2026-10-07 已授权的文件入口局部增量

- 用户要求设备日志操作回到项目详情的业务联调入口。已复制增强现有联调页及采集窗口，沿用原“命令采集与日志”操作，在窗口内接通上传、设备文件历史与来源文件查询；旧页面、原证据附件和业务状态动作保持原行为。
- 在`npdms_domain_test`项目1002通过真实浏览器上传授权派生日志，两台设备成功归档v4并分别完成解析回传；验证文件历史、来源/单设备内容读取、关闭重开及未匹配SN整批拒绝。基线关联、对比与完成事实没有实施，本Feature总体Ready/Done状态不因该文件入口增量晋级。
- 证据见[日志解析记录](../../docs/generated/2026-10-06-device-log-parsing.md)的项目详情入口补充。
- 需求方最终位置修正（2026-10-07）：项目级日志入口现与“新增联调”并列，记录行/详情的命令窗口只承接该设备及联调记录，项目全部设备文件同时在“配置Log”可见。真实浏览器验证按钮位置、项目与记录范围、共享文件历史及关闭重开；原业务单据不变，前述记录窗口内的项目上传页签已移除。
