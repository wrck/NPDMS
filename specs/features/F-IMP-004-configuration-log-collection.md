# F-IMP-004 配置 Log 采集解析 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY`
> Requirement：`EXE-03（V1/P0）`
> Requirement切片覆盖：`EXE-03@V1=FULL`
> Owner Context：`IMP（现场实施）`
> 前置Feature：`F-IMP-003`、INT-12 Owner Feature、EQP-02 Owner Feature

## 1. 业务目标与范围

保留IMP配置调试入口，支持人工上传Log或通过INT-12统一采集任务下发手工选择/提供的命令脚本；按设备序列号形成不可覆盖原始Log引用、解析版本和框/槽/板卡候选关系。本Feature不建设设备连接或原始采集引擎。

## 2. 核心规则

- 只有F-IMP-003安装完成且属于当前配置范围的设备可发起；任务冻结设备、命令/脚本快照和认证方式。
- 保存凭证模式的凭证ID/版本/授权快照；临时模式仅保存用户名，不保存密码；只有显式选择才保存为加密凭证。
- 每次在线尝试或人工上传生成独立记录；失败任务、解析失败、无设备归属文件不计入完成事实。
- 盒式设备按序列号归档；框式设备保留整机Log并提取机框/槽位/板卡候选。人工绑定不修改原始Log，板卡更换使用时态关系。
- 当前目标设备全部有至少一条有效配置Log时才完成；设备范围或有效版本变化使来源事实版本变化。

## 3. 公开事实契约

`ConfigurationCompletionFactApi.inspect/lockAndRevalidate`返回`configurationFactId`、`COMPLETED/NOT_COMPLETED`、`factVersion`、`scopeWatermark`、每台设备当前有效结果ID/版本、未满足设备和`reopened`。不返回原始Log、解析正文、凭证或临时密码。

## 4. 数据、UI与验收

- Owner表：`imp_configuration_collection_result`、`imp_configuration_collection_parse_attempt`、`imp_configuration_component_candidate`；原始不可变Log由EQP-02统一管理。
- UI覆盖在线/手工路径、凭证/临时认证、任务与解析状态、按项目/序列号/操作人/时间查询、下载/对比/检索。
- 验收覆盖手工上传正向、INT-12回调幂等、Provider不可用、解析失败、框板候选与人工绑定、凭证权限/明文零命中、范围重开、真实MySQL和浏览器。

## 5. Feature Ready Gate

`NOT_READY`：须锁定INT-12与EQP-02公开契约、F-IMP-003安装完成事实、框板数据元/迁移证据和旧配置记录复用映射。
