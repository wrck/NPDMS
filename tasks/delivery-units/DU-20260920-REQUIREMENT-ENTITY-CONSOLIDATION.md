# DU-20260920-REQUIREMENT-ENTITY-CONSOLIDATION

> 状态：CLAIMED
> Requirement / Feature：PRE-04 / F-SOL-003
> Owner / 协调者：当前需求分析实体合并任务
> 工作树 / 分支：E:/AICoding/Projects/NPDMS / codex/domain-migration
> 授权：2026-09-20需求方确认统一新旧实现、保留当前与历史修订存储；当前页面11项正文和8项结构化业务字段落实体字段，只有模板自定义项使用扩展字段。

## 写入边界

- engineering：requirement 包内 RequirementAnalysisDO、RequirementAnalysisRevisionDO、新增业务明细值对象及其类型处理器；RequirementAnalysisMapper.xml；RequirementAnalysisEntityProvider、RequirementAnalysisEntityQueryService、RequirementAnalysisEntityCommands、RequirementAnalysisImportService、RequirementAnalysisFormPolicyProvider、RequirementAnalysisRevisionFilePolicy；对应字段映射、查询、命令、附件批处理和持久化专项测试。
- frontend：requirement-analysis/entity/entityForm.ts及其测试；必要的EntityForm.vue数据绑定适配，保留其他任务的交底书面板改动。
- sql：新增前向 requirement_analysis_business_fields 迁移，编号实施时确认；不改既有迁移，不执行开发库数据迁移。
- 文档：本DU记录本轮结果；F-SOL-003规格中的本次字段落位差量。原DU-20260909-DELIVERY-DEMO-BUSINESS-UI的旧表/动态实例实施及历史验收保留，本轮仅承接新实体字段与该规格差量，不接管其他模板、宿主或共享PLT实施。

## 数据与协作边界

SOL继续唯一拥有正文与生命周期；当前实体与修订继承同一字段定义。PLT继续拥有表单配置、真正自定义扩展和受控文件。旧实体/冻结表单/完成历史不删除、不覆盖；旧扩展中既有业务字段仅作历史来源，通过显式映射承接，不双写。

现场培训、满意度、物料、设备等当前在途改动不纳入本任务。复用既有公共API，不增加第二套表单、权限或版本框架。固定测试设施使用前核实占用；不停止其他任务服务，不清库，不推送。

## 完成条件

固定字段保存与回读、旧绑定和历史兼容、修订复制、扩展隔离、租户/权限/并发失败路径及附件批处理定向验证。数据库与浏览器未实际执行的部分如实保留，不能用编译代替业务验收。

## 追加授权与边界（2026-09-20）

需求方确认旧 sol-requirement 的 BUSINESS 写入口收敛为历史只读，INTERFACE 保持功能。追加 RequirementServiceImpl、ErrorCodeConstants 中专用错误码、旧 engineering/requirement/index.vue 及对应测试；不删除旧记录、不转换旧状态。实体收敛同时覆盖 RequirementAnalysisFields、EntityController 比较投影及 ImportMapper 类型映射。
