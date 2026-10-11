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

## 2026-10-08 序列号配置Log抽屉增量

- 按需求方明确要求，项目序列号行的配置Log直接打开当前设备抽屉，保持序列号工作台和页面地址不变；以既有AST接口合并读取归档版本及已有旧日志，沿用原下载鉴权。
- 项目配置Log取消按新旧存储区分的两个平行页签；旧历史只在当前项目有记录时折叠显示，没有旧记录时隐藏。当前项目1002有4条旧日志且配置附件上传入口仍存在，保留可查；不新增、删除或迁移历史。
- 已通过真实浏览器的设备隔离、版本展示、关闭重开、内容读取、空日志、查询失败/刷新恢复及无旧日志项目隐藏验证。组件编译和相关lint通过；详细范围与证据见[日志解析记录](../../docs/generated/2026-10-06-device-log-parsing.md)的设备抽屉补充。本次不变更上述Feature总体状态。

## 2026-10-09 设备管理分页性能修复

- 按用户授权处理现有工作台加载慢；仅优化权限SQL执行顺序与无筛选扫描索引路径，V416新增覆盖索引，不修改PRD语义、业务记录或授权范围。
- 已部署59191并完成8项权限回归和真实浏览器分页、SN筛选、无匹配、重置验收；首页接口由10～12秒降低至约1.7秒。详见[性能验收记录](../../docs/generated/2026-10-09-device-page-performance-acceptance.md)。本增量不改变上述Feature总体状态。

## 2026-10-09 序列号详情批量展示性能修复

- 按用户要求移除40台设备121次逐台补齐请求，AST分页返回版本/维保统计，IMP通过授权的项目当前安装地址批量接口供消费。保留模块表Owner及历史读功能。
- 后端13项、前端4项回归及真实40台设备浏览器验收通过：2个业务请求，约0.7秒；详见[批量验收](../../docs/generated/2026-10-09-serial-batch-acceptance.md)。本增量不改变Feature总体状态。

## 2026-10-09 当前安装地址快照投影统一

- 按用户确认复用locationSnapshot.fallbackLocation，AST位置生效时统一补齐当前快照的完整文本；安装及位置历史保留原始来源，手工地址不臆造站点引用。移除独立installationAddress读写依赖；已执行V420以V421前向修正并撤除冗余列。
- 后端13项、前端4项回归及真实浏览器通过，40台序列号只需一次分页业务请求；历史迁移仅补齐1台手工地址当前快照，24台当前安装投影一致，旧历史不改。页面保存新地址后返回序列号页立即更新；详见[快照投影验收](../../docs/generated/2026-10-09-installation-snapshot-projection-acceptance.md)。本增量不改变Feature总体状态。
