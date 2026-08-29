# F-IMP-005 业务联调配置收集 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY`
> Requirement：`EXE-04（V1/P0）`
> Requirement切片覆盖：`EXE-04@V1=FULL`
> Owner Context：`IMP（现场实施）`
> 前置Feature：`F-IMP-004`、INT-12 Owner Feature、show tech命令模板Owner Feature

## 1. 业务目标与范围

为已完成配置调试的设备提供在线show tech采集或手工上传，冻结设备范围、已发布命令模板版本和认证方式；按设备归档联调结果，并与同设备F-IMP-004基线Log形成前后对比。

## 2. 核心规则

- 在线任务只选择已发布且适用设备类型的show tech模板，通过INT-12下发；手工文件形成新记录，不改写原在线任务为成功。
- 凭证/临时认证边界与F-IMP-004一致；临时密码不落库、不入日志。
- 每条结果必须关联同设备的有效F-IMP-004基线；缺少基线可保存为待对比，不计入完成。
- 所有目标设备均有有效联调记录且所需对比完成时才形成里程碑完成事实；失败、待解析或重开使事实版本变化。

## 3. 公开事实契约

`JointDebuggingCompletionFactApi.inspect/lockAndRevalidate`返回`debuggingFactId`、`COMPLETED/NOT_COMPLETED`、`factVersion`、`scopeWatermark`、每台设备联调结果/对比版本、未满足设备和`reopened`；不返回Log、对比正文或凭证。

## 4. 数据、UI与验收

- Owner表：`imp_joint_debugging_result`、`imp_joint_debugging_item`；业务任务+结果版本唯一。
- UI覆盖设备、发布模板、认证方式、在线/手工路径、基线关联、对比和完成率。
- 验收覆盖在线/手工正向、模板/凭证越权、回调幂等、Provider不可用、缺基线待对比、重试不覆盖失败证据、范围重开、真实MySQL和浏览器。

## 5. Feature Ready Gate

`NOT_READY`：须锁定F-IMP-004基线事实、INT-12、已发布show tech模板与旧联调记录复用映射。
