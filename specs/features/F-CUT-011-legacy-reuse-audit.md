# F-CUT-011 旧实现与现有实现复用审计

> Requirement：`CUT-08@V2`
> 状态：`DRAFT / REVIEW_REQUIRED`

| 资产 | 当前事实 | 判定 | F-CUT-011边界 |
|---|---|---|---|
| F-CUT-002 P2评估 | 已有不可变SUBMITTED评估及`sparePartApplied`，任务负责人、项目和设备范围已冻结 | `DIRECT_REUSE` | 作为备件需求来源及授权上下文，不修改P2问卷或判级 |
| F-CUT-003 P3清单 | 已有版本化清单、系统匹配项和当前结果；正式风险类别含`MAJOR_PROJECT_SPARES` | `DIRECT_REUSE` | 仅以当前适用系统匹配项身份形成需求来源，不解析附件或自由文本造需求 |
| F-CUT-005 P5审批详情 | 已有冻结P2来源和完整审批工作台 | `DIRECT_REUSE_AND_ADD_READ_ONLY_PROJECTION` | 增加备件保障只读卡片，不进入评审结果、动作或状态机 |
| F-CUT-006 P6闭环 | 明确不以本地备件生命周期建立门禁 | `PRESERVE_UNCHANGED` | 不修改P6准入、提交、归档和结果 |
| 旧`pms_cut_task/risk/plan/execution/observation` | 无外部申请引用、单调状态版本、人工证据文件事实或可信备件生命周期 | `NOT_REUSABLE` | 不迁移、不双写、不从备注/风险文本推导外部申请或成功状态 |
| 工程物料申请旧页面/表 | 存在“备件库”等工程物料字段，但属于不同业务Owner和旧CRUD | `NOT_REUSABLE` | 不复制页面、状态、权限、表或库存语义到CUT |
| INT-06备件系统 | 正式SDS定义外部Owner和双向边界，当前仓库无CUT可用生产Provider | `RESERVED_PORT_ONLY` | CUT只定义消费端口；`src/test`确定性替身跑正常闭环，不实现连接器/认证/第三方功能 |
| PLT文件事实 | 已有不可变FileArtifact公开事实 | `DIRECT_REUSE_BY_API` | 人工证据仅保存稳定文件事实引用，不复制文件正文或绕过PLT |
| CT-08线框和附件 | 提供区块名称与样式参考 | `REFERENCE_ONLY` | 不产生业务字段、状态、数量、角色、权限或完成结论 |

## 结论

F-CUT-011必须作为CUT-08完整纵向协同Feature，在现有任务工作台和P5详情上增加CUT自有引用/快照/证据闭环。旧CUT与工程物料实现均不能升级为外部备件权威事实；跨模块功能只保留接口并以测试替身验证正常链。
