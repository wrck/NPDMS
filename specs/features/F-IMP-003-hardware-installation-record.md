# F-IMP-003 硬件安装与位置生效 Feature Spec

> 文档状态：`DRAFT`
> Feature Ready：`NOT_READY`
> Requirement：`EXE-02（V1/P0）`
> Requirement切片覆盖：`EXE-02@V1=FULL`
> Owner Context：`IMP（现场实施）`
> 前置Feature：`F-IMP-002`、`F-AST-001`
> AST支撑Task：`T-FIMP001-AST-01`

## 1. 业务目标与范围

按已签收设备保存安装、迁移或拆除记录、AST结构化位置/受控文本降级、安装人/时间和模板要求的照片；项目经理确认后通过AST命令使设备当前位置生效并保留历史。

## 2. 核心规则

- 对象必须已由F-IMP-002签收且当前直接归属本项目；未签、拒收或仅参与其他项目的设备不得确认安装。
- 每台设备同一时点一个当前有效安装记录；更换位置或纠正创建新版本，不覆盖证据。
- 有`siteId/siteLocationId`时结构化引用是权威位置；文本降级标记`UNRESOLVED`，不参与结构化权限或自动解析。
- 确认前不更新AST当前位置；AST位置命令失败时标记待重试且不计入完整里程碑。
- 已签且要求安装的设备全部有有效记录或具体设备豁免时才形成完成事实；新签设备加入范围后必须重验。

## 3. 公开事实契约

`InstallationCompletionFactApi.inspect/lockAndRevalidate`按项目、设备范围和期望版本返回`installationFactId`、`COMPLETED/NOT_COMPLETED`、`factVersion`、`scopeWatermark`、未满足设备、位置同步待重试项和`reopened`；不返回照片或位置敏感正文。

## 4. 数据、UI与验收

- Owner表：`imp_installation_record`、`imp_installation_item`、`imp_installation_evidence`；位置引用只通过AST公开契约。
- UI覆盖待安装列表、位置选择/待维护降级、照片、提交/确认/退回和历史版本。
- 验收覆盖未签/越权设备、照片或位置缺失、结构化/文本降级、AST同步失败补偿、范围重开、历史不覆盖、真实MySQL和浏览器。

## 5. Feature Ready Gate

`NOT_READY`：须锁定F-IMP-002签收事实、AST设备/地点/位置生效契约、旧安装记录映射和证据模板输入。
