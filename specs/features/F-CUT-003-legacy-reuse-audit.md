# F-CUT-003 旧清单与现有载体复用审计

> Requirement：`CUT-03（V1/P0）`
> 审计结论：`DIRECT_REUSE / COPY_THEN_ENHANCE / DO_NOT_REUSE / PRESERVE_EXISTING`
> Feature Spec：`specs/features/F-CUT-003-p3-dynamic-checklist-and-manual-fallback.md`

## 1. 审计范围

- 旧CUT风险/调研栈：`pms_cut_risk`、`CutRiskController/Service/DO/Mapper`、旧`cut-risk`页面和权限；
- F-CUT-001：配置修订、统一采集项定义、动态绑定规则、风险/调研矩阵及已发布版本查询；
- F-CUT-002：`CutoverTask`、当前评估、P3/P4状态机和新任务工作台；
- PLT：`FileArtifactApi`、`FileBusinessObjectPolicyProvider`、公共文件Fact和Access Ticket；
- INT-12：正式SDS中的CollectionTask创建、状态和结果引用边界；
- SDS三张目标表及ADR-0030迁移登记。

## 2. 逐项判断

| 载体 | 当前事实 | 结论 | F-CUT-003处理 |
|---|---|---|---|
| `pms_cut_risk` | 可变风险/调研行，状态与处理动作属于旧流程；缺配置修订、清单版本、稳定项键、结果版本和选择区间 | `DO_NOT_REUSE / PRESERVE_EXISTING` | 不迁移、不双写、不反推清单、答案、必填或通过结果 |
| 旧CutRisk Controller/Service/Mapper/DO | 通用CRUD及开始/闭环/挂起动作，允许原位更新 | `DO_NOT_REUSE / PRESERVE_EXISTING` | 新建checklist聚合、命令和场景Query；旧接口行为不变 |
| 旧`cut-risk`页面 | 独立风险页，不是P3同工作台，控件和状态硬编码 | `DO_NOT_REUSE / PRESERVE_EXISTING` | 在F-CUT-002新任务工作台P3步骤新增面板，不改旧页 |
| F-CUT-001配置根/项定义/绑定规则 | 已发布不可变版本，稳定项键、Schema、工作方式、已知及扩展维度完整 | `DIRECT_REUSE` | 只读当前适用发布版本并冻结修订/定义/规则快照，不复制配置真值 |
| F-CUT-001风险/调研矩阵规则 | 已覆盖三类项、12类调研和5类97项双机检查 | `DIRECT_REUSE` | 复用配置数据；清单匹配服务只消费，不另建矩阵 |
| F-CUT-002 Task/Assessment/StageHistory | 已形成P3入口、最终等级和P3/P4状态 | `DIRECT_REUSE` | 清单作为任务从属聚合；提交在同一CUT事务推进既有任务 |
| F-CUT-002工作台 | 已有P2～P6步骤骨架 | `COPY_THEN_ENHANCE` | 只在新`cutover-task`页面P3步骤增加清单组件，不触碰旧页 |
| PLT公共文件接口 | 已拥有上传、扫描、存储、公共Fact和Access Ticket | `DIRECT_REUSE` | CUT新增业务策略Provider；不改PLT接口、不读取PLT表 |
| INT-12 CollectionTask | SDS已有REST、状态与结果引用，但仓库无可由CUT实现的生产Owner API | `INTERFACE_ONLY` | 定义CUT最窄消费端口和失败事实；不实现连接器、执行引擎或Provider |
| `cut_cutover_checklist*` SDS载体 | 三表已进入Phase 2/3冻结模型，尚无产品实现 | `COPY_THEN_ENHANCE` | 按SDS前向创建三表与新聚合，不新增第四张真值表 |

## 3. 迁移与历史边界

- 三张清单表为`NEW_ONLY / FEATURE_FORWARD_MIGRATION`，没有可批准的旧业务来源。
- `pms_cut_risk`只作为旧实现审计证据；其`code/name/type/status/handler`不能推导稳定项键、配置版本、结果来源、必填性、答案或通过事实。
- 旧页面、接口和数据保持可达，不删除、不更新、不双写；新清单提交不触发旧风险状态。
- F-CUT-001已发布配置和F-CUT-002任务事实是运行依赖，不属于迁移输入。

## 4. 结论

F-CUT-003复用F-CUT-001配置真值、F-CUT-002任务内核和PLT公共文件能力；复制增强新工作台P3区域；对旧风险栈采用`DO_NOT_REUSE / PRESERVE_EXISTING`。INT-12及外部数据源只保留消费接口，不能因CUT实现需要而复制跨模块Owner。
