# F-CUT-009 旧实现与现有实现复用审计

> Requirement：`CUT-03@V2`
> 状态：`DRAFT / REVIEW_REQUIRED`

| 资产 | 当前事实 | 判定 | F-CUT-009边界 |
|---|---|---|---|
| F-CUT-003三张清单表、应用服务和P3工作台 | 已实现生成、填写、暂存、提交、详情授权投影及P3→P4正向闭环 | `DIRECT_REUSE_AND_ADD` | 导出复用同一查询投影；提交只增加无业务副作用的导航决定，不复制清单或改变状态机 |
| F-CUT-001配置修订、管理服务和页面 | 已实现DRAFT/PUBLISHED/DISABLED、复制、发布校验和不可变修订；数据库已有`navigation_rule_snapshot`载体，当前DO/REST未形成V2规则闭环 | `COPY_THEN_ENHANCE_CURRENT_FEATURE_PATH` | 在现有配置聚合新增导航规则字段/校验/编辑区；旧V1配置语义和三类采集项路径不改 |
| `cut_cutover_configuration_revision.navigation_rule_snapshot` | SDS与表结构预留，V1空值未被业务消费 | `DIRECT_REUSE_CARRIER` | 不新增导航表，不回填历史；空值保持V1基础导航 |
| 现有导出基础组件/XLSX写法 | 仓库有通用Excel响应模式，但无CUT授权清单导出 | `REUSE_TECHNICAL_STYLE_ONLY` | 复用响应/文件名/单元格编码模式；数据、权限和分组必须来自CUT服务端，不复用其他领域DTO |
| 旧`pms_cut_risk`、旧`/pms/cut-risk`及旧页面 | 无版本化清单、字段裁剪或配置导航事实 | `NOT_REUSABLE` | 保持原表、接口和页面不变，不迁移、不双写、不升级为V2事实 |
| PROJ跨模块范围能力 | 现有公开项目范围合同不归CUT拥有 | `RESERVED_PORT_ONLY` | 生产仅调用正式端口；测试可受控模拟正常闭环，不实现跨模块Provider或Yudao功能；导出不创建PLT制品 |
| 附件与参考界面 | 可辅助名称和样式 | `REFERENCE_ONLY` | 不产生导出字段、跳转目标、权限或完成结论 |

## 结论

F-CUT-009是现有P3清单与配置聚合的V2纵向增强。最小实现必须在现有服务和页面上增加导出与导航配置，不建立第二套清单、配置、流程或跨模块实现。`Q-FCUT009-001`只阻断导航目标运行分支；不授权通过旧实现或附件猜测跳转语义。
