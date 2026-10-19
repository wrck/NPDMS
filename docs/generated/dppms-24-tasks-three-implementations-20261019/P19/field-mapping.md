# P19 原表逐字段承载清单

当前独立C实现以[本轮已决变更](../base-sync-department-code-preview-20261019.md)和[实际XML](../base-sync-department-code-preview-20261019.sql)为准；下方旧阶段原文保留。四个项目状态扩展已暂缓，不读状态原表或正式扩展定义；相关方预览现停在原项目ID冲突。需解决同一40项项目身份冲突，不能接管已有相关方或项目。

合并组件：P19。以下来自2026-10-14字段盘点；保留明确不迁、技术追溯及待关联字段，不把技术绑定算作正式业务承接。

本清单的当前差异以本任务README、三种实现的映射JSON/实际SQL和2026-10-18已裁决修复为准。`C-base-sync-mappings.json`提供当前INSERT列到运行参数的实际对应，不能把本旧清单当作三种实现字段完全相同。

| 原表.字段 | 类型 / NULL | 正式承接或处置 | 规则 / 状态 | 组件 |
|---|---|---|---|---|
| pm_project_related_party.id | int(11) / NO | proj_project_party.source_record_key | 原id作为正式来源记录键及INT绑定，不冒充目标主键。；业务字段/正式关系 | P19 |
| pm_project_related_party.projectId | int(11) / YES | proj_project_party.project_id | 通过P17或本任务已批准的PROJ原项目身份解析正式project_id；不将原数值直接落正式外键。 经P17原项目绑定解析，不自动新建项目。；业务字段/正式关系 | P19 |
| pm_project_related_party.partyRole | varchar(45) / YES | proj_project_party.party_role | 原10服务渠道、20出货渠道、30代理商，保留原角色。；业务字段/正式关系 | P19 |
| pm_project_related_party.partyCode | varchar(45) / YES | proj_project_party.party_code | 原值保留，按PROJ Owner及完整前值保护承接。；业务字段/正式关系 | P19 |
| pm_project_related_party.partyName | varchar(45) / YES | proj_project_party.party_name | 原值保留，按PROJ Owner及完整前值保护承接。；业务字段/正式关系 | P19 |
| pm_project_related_party.createTime | datetime / YES | proj_project_party.create_time | 原值保留，按PROJ Owner及完整前值保护承接。；业务字段/正式关系 | P19 |
| pm_project_related_party.createBy | varchar(45) / YES | proj_project_party.creator | 能精确关联则存正式用户ID字符串，否则保留批准原码；NULL保持。；业务字段/正式关系 | P19 |
| pm_project_related_party.updateTime | datetime / YES | proj_project_party.update_time | 原更新时间；NULL时用本行原创建时间。；业务字段/正式关系 | P19 |
| pm_project_related_party.updateBy | varchar(45) / YES | proj_project_party.updater | 能精确关联则存正式用户ID字符串，否则保留批准原码；NULL保持。；业务字段/正式关系 | P19 |
| pm_project_related_party.effectiveFrom | datetime / YES | proj_project_party.effective_from | 原值保留，按PROJ Owner及完整前值保护承接。；业务字段/正式关系 | P19 |
| pm_project_related_party.effectiveTo | datetime / YES | proj_project_party.effective_to | 原值保留，按PROJ Owner及完整前值保护承接。；业务字段/正式关系 | P19 |
| pm_project_state.projectId | int(11) / NO | PROJ项目扩展entity_id | 通过P17精确解析正式项目ID，不直写旧ID。；业务字段/正式关系 | P19 |
| pm_project_state.projectPlanState | varchar(10) / YES | 暂不迁入 | 工程过程缓存随原工程过程范围暂缓。；已批准暂缓 | P19 |
| pm_project_state.projectplanTime | datetime / YES | 暂不迁入 | 工程过程缓存随原工程过程范围暂缓。；已批准暂缓 | P19 |
| pm_project_state.shipmentState | varchar(11) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.shipment_status | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P19 |
| pm_project_state.shipmentTime | datetime / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.shipment_status_updated_at | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P19 |
| pm_project_state.executionState | varchar(45) / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.execution_status | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P19 |
| pm_project_state.executionStateTime | datetime / YES | C：暂不承接；历史A/B目标：plt_entity_extension_value.values_json.execution_status_updated_at | C不读取/校验/写入该属性，原始来源保留；A/B已审核处置作为历史保留。 | P19 |
| pm_project_state.closeProcessState | varchar(45) / YES | 不迁入 | 审批过程状态已明确不承接，不解释为项目闭环日期。；已批准排除 | P19 |
| pm_project_state.closeProcessStateTime | datetime / YES | 不迁入 | 审批过程状态已明确不承接，不解释为项目闭环日期。；已批准排除 | P19 |
