# F-CUT-007 旧实现复用审计

> Requirement：`CUT-01@V2`
> 结论：旧割接CRUD与旧页面没有授权KPI和P2～P6真实动作并集语义；保留旧路径，只在新统一工作台复制增强只读卡片。

| 资产 | 当前能力 | 结论 | F-CUT-007处置 |
|---|---|---|---|
| `CutTaskController/CutTaskServiceImpl` | 旧`pms_cut_task` CRUD、旧tinyint状态和单级审核 | `PRESERVE_LEGACY / DO_NOT_REUSE_RUNTIME` | 不增加KPI副作用，不从旧状态推导四项计数 |
| 旧`cut-task/index.vue` | 旧列表、表单、审核入口 | `PRESERVE_LEGACY` | 页面、路由、权限和接口零修改 |
| 新`cutover-task/index.vue` | F-CUT-002～006统一P1～P6工作台 | `COPY_THEN_ENHANCE` | Feature Ready及计划通过后增加四张只读KPI卡片，不改变阶段组件写路径 |
| `CutoverTaskQueryService`及P2/P3动作投影 | 已有任务可见性和P2/P3真实动作守卫 | `REUSE_RULES` | 提取/复用批量动作判定，不逐任务调用Controller或复制简化规则 |
| P4计划、P5审批、P6闭环查询服务 | 已有真实allowedActions、P5`myTodos`和来源完整性守卫 | `REUSE_RULES` | 待办复用既有守卫；P5取`myTodos`资格与KPI可见范围交集 |
| `cut_task`及当前P2～P6表 | 当前CUT权威任务与阶段事实 | `READ_ONLY_REUSE` | 只读聚合，不新增列、快照、缓存或状态 |
| 旧菜单/权限 | 旧`pms:cut-task:*`权限和路由 | `NOT_REUSABLE` | 新KPI复用现有`pms:cutover-task:query`，不新增旧权限映射 |
| 旧测试与示例数据 | 证明旧CRUD与旧状态 | `TEST_REFERENCE_ONLY` | 不作为授权、待办或状态KPI事实 |

## 审计结论

- 仓库现有后端、前端、配置、运行数据、状态、权限和测试均不存在CUT-01@V2四项授权KPI闭环，不能把旧列表总数改名后复用。
- 新实现只读取当前CUT权威事实，并复用现有P2～P6动作守卫；旧`pms_cut_*` Service、Controller、页面、接口、权限、数据和测试保持不变。
- 不迁移、不双写、不为KPI制造历史快照。旧来源行只有在当前CUT表中已形成可证明的当前状态时才可按精确状态谓词参与非待办KPI；待办固定排除非`NEW_PLATFORM`任务。
- 跨模块预留接口只允许测试作用域受控模拟，不能回写旧路径或注册生产fallback。
