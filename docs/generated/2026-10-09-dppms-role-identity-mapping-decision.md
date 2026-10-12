# 角色32项来源身份：用户授权编码转换

用户批准原角色清单及例外，并明确按常量名转换对应编码，不能直接使用常量名。采用与t_role既有业务字符串一致的小驼峰编码；这是一项用户授权的转换，不把转换结果宣称为旧代码字符串字面量。指定任务库运行2108559110330822657已迁入31条来源关系（25个新角色、6条采用已有身份），停用角色不迁入、表结构不变。实际核查发现13个新角色25处原NULL审计人被框架填成管理员，经用户限定批准后纠正运行2108568396289216514已完成；25个新角色及31条绑定逐字段核对正确，原15个角色定义和菜单权限保持。重复纠正预览13条UNCHANGED。随后用户角色关系承接已按单独批准规则完成，不将其关系新增误写为角色定义变化。

| 来源 | ID | 原表名称 | 原业务标识/整数符号 | 转换业务码 | 正式code | 处理 |
|---|---|---|---|---|---|
| `t_role` | 1 | 超级管理员 | `admin` | `admin` | super_admin | 用户明确批准关联既有平台super_admin并授予平台超级管理员权限；取消独立历史管理员提案。 |
| `t_role` | 2 | 普通用户 | `user` | `user` | user | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 5 | 项目成员 | `projectMember` | `projectMember` | projectMember | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 6 | 项目管理员 | `projectAdmin` | `projectAdmin` | projectAdmin | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 7 | 项目经理 | `projectManager` | `projectManager` | PROJECT_MANAGER | 复用既有PROJECT_MANAGER，不覆写角色定义/权限 |
| `t_role` | 8 | 安服质量监督员 | `projectAFQC` | `projectAFQC` | projectAFQC | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 9 | 研发质量监督员 | `projectYFQC` | `projectYFQC` | projectYFQC | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 10 | 子项目管理员 | `projectSubAdmin` | `projectSubAdmin` | projectSubAdmin | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 11 | 项目区域负责人 | `projectAreaManager` | `projectAreaManager` | projectAreaManager | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 12 | 项目售前 | `projectPreSales` | `projectPreSales` | projectPreSales | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 13 | 用户管理员 | `userManager` | `userManager` | userManager | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 14 | 财务AP | `financialAP` | `financialAP` | financialAP | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 15 | 数据查询角色 | `dataViewer` | `dataViewer` | dataViewer | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `t_role` | 16 | 项目转包结算人员 | `dispatchSettleStaff` | `dispatchSettleStaff` | dispatchSettleStaff | 以t_role原业务身份迁入；新增正式角色行，不新增字段 |
| `fnd_roles` | 1 | 管理员 | `ROLE_ADMIN` | `admin` | admin | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 3 | 普通用户 | `ROLE_COMMON` | `common` | user | 关联同义t_role/既有角色，不另建同义角色 |
| `fnd_roles` | 5 | 项目管理员 | `ROLE_PROJECT_ADMIN` | `projectAdmin` | projectAdmin | 关联同义t_role/既有角色，不另建同义角色 |
| `fnd_roles` | 6 | 项目查阅员 | `ROLE_PROJECT_VIEWER` | `projectViewer` | projectViewer | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 9 | 办事处主任 | `ROLE_AREA_LEADER` | `areaLeader` | areaLeader | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 10 | 工程管理部主管 | `ROLE_ENGINEEMANAGER_LEADER` | `engineeManagerLeader` | engineeManagerLeader | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 11 | 服务经理 | `ROLE_SERVICEMANAGER` | `serviceManager` | SERVICE_MANAGER | 关联同义t_role/既有角色，不另建同义角色 |
| `fnd_roles` | 12 | 项目经理 | `ROLE_PROGRAMMANAGER` | `programManager` | PROJECT_MANAGER | 关联同义t_role/既有角色，不另建同义角色 |
| `fnd_roles` | 13 | 工程管理部 | `ROLE_ENGINEEMANAGER` | `engineeManager` | engineeManager | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 14 | 回访人员 | `ROLE_CALLBACKPER` | `callbackPer` | callbackPer | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 15 | &aelig;&micro;&egrave;&macr;&auml;&ordm;&ordm;&aring; | `ROLE_SALESPEOPLE` | `salesPeople` | 不迁入 | 不迁入：用户明确裁决停用角色不迁入；其用户角色关联不形成正式授权。 |
| `fnd_roles` | 16 | 财务人员 | `ROLE_FINANCIAL_STAFF` | `financialStaff` | financialStaff | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 17 | 售前测试专员 | `ROLE_PRESALES_STAFF` | `presalesStaff` | presalesStaff | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 18 | 技术公告管理员 | `ROLE_PROB_ADMIN` | `probAdmin` | probAdmin | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 19 | 技术支持人员 | `ROLE_PROB_SUPPORTER` | `probSupporter` | probSupporter | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 20 | 研发人员 | `ROLE_PROB_RD` | `probRD` | probRD | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 21 | 项目维保回访专员 | `ROLE_WARRANTY_CALLBACKER` | `warrantyCallbacker` | warrantyCallbacker | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |
| `fnd_roles` | 22 | 产品组件管理员 | `ROLE_COMPONENT_ADMIN` | `componentAdmin` | componentAdmin | 按用户指示由角色常量语义转为小驼峰业务码；原角色独立身份按批准清单承接。 |

## 已明确处理

- 用户明确批准t_role1超级管理员关联平台super_admin并授予平台超级管理员权限；不再使用独立code=admin。
- 停用角色不迁入，本源fnd_roles15及相关用户角色关联排除；完整列明来源及用户裁决理由。
- t_role11项目区域负责人按已批准清单独立承接。
- 普通用户/项目管理员按同义t_role关联；PROJECT_MANAGER/SERVICE_MANAGER复用现有角色。
- t_role sort=100-priority；独立fnd sort=100+来源ID；新增普通业务角色CUSTOM=2、SELF=5，不自动授予菜单按钮。
- 已有角色定义与权限不覆盖；超级管理员权限依据用户本次明确裁决授予。
- 编码转换依用户最新裁决执行：ROLE_PROJECT_VIEWER→projectViewer等小驼峰业务码；该转换结果不声称是旧源码原有字符串字面量。ROLE_*名称、整数ID及新编码三者分别记录。
