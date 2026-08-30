# F-COM-001 旧实现与既有切片复用审计

> 状态：`REVIEW_REQUIRED`
> Requirement：`COM-01@V1`
> 审计对象：当前NPDMS后端、前端、配置、数据/迁移、状态、权限与测试

| 类别 | 当前事实 | 判定 | F-COM-001处置 |
|---|---|---|---|
| 后端API | `DeliveryScopeApi`已有`getAvailableSlices/previewSplit/applySplit`，只表达F-PROJ-002父项目可分割余量 | `DIRECT_REUSE_WITH_CONTRACT_PRESERVATION` | 保持三接口语义和调用方不变；新增`getAssignedScope` DTO/Provider，不用余量接口适配或降级 |
| 后端服务 | `DeliveryScopeService`已实现预览、分配/释放、订单行稳定锁序、版本、Outbox和幂等来源前缀 | `COPY_THEN_ENHANCE` | 保留既有服务；新建COM-01应用服务承接来源副本、工作台、当前范围和冲突，必要时提取已验证公共规则但不改变旧调用语义 |
| 持久层 | V70及DO/Mapper已有`com_order_line/com_delivery_scope/com_delivery_scope_detail/com_outbox_event` | `FORWARD_EXTEND` | 复用表身份和历史；新Flyway增加缺失Owner表/关系/字段/约束，不修改V70 |
| 前端 | 无COM工作台；Yudao CRM合同页面是CRM销售合同CRUD | `DO_NOT_REUSE_BUSINESS_UI` | 只复用通用表格、表单、分页和权限组件；新建COM页面，不修改或复制CRM业务语义 |
| 配置 | `pms-module-commerce`已纳入Reactor和统一装配，无COM专属Job/菜单 | `DIRECT_REUSE_BASELINE` | 沿用模块/POM；新增种子必须前向、幂等，外部ERP Job不在本Feature实现 |
| 数据/迁移 | V72含F-PROJ-002受控种子；正式旧来源为四张外部/遗留表，迁移契约仍要求逐行资格 | `EVIDENCE_ONLY` | V72不升格为生产事实；按字段/状态/完整性映射迁移，可疑行进入问题记录，不双写 |
| 状态机 | 当前范围状态仅`ACTIVE/RELEASED/CONFLICT`，V70订单行字段名为`quantity_status`且值仅`CONFIRMED/PENDING_AUTHORITY` | `DIRECT_REUSE_AND_COMPLETE` | 保留字段和值；不得改名为`authority_status`或双写；补来源变化触发与冲突处置规则，不从旧tinyint或文本猜测状态 |
| 权限 | 无COM正式菜单与权限；CRM合同权限属于CRM | `DO_NOT_REUSE_PERMISSION_KEYS` | 新增五个COM权限；不复用`crm:contract:*`，服务端同时校验项目/公司范围 |
| 测试 | `DeliveryScopeServiceTest`覆盖既有切片的预览、分配、重放和冲突 | `EVIDENCE_REUSE` | 保留作为回归；实现完成后再补COM-01新增合同/订单/当前范围/迁移/MySQL/浏览器验证，不以旧绿测证明新Feature完成 |

## 迁移资格

- Contract：只有租户、所属公司和合同号可证明时创建主档；订单来源合同号只作关系核对，不能单独创建主档。
- SalesOrder：只合并确定性来源/公司/订单类型/订单号业务键；冲突组保持迁移问题。
- OrderLine：必须解析稳定订单行键、订单归属、数量、单位和权威状态；空键、歧义键或未知状态不迁。
- DeliveryScope：必须同时解析项目、订单行和正数`projectQuantity`；审计的该列填充率为0，`orderQuantity/deliverQuantity/openQuantity`只能保留原值而不能替代，故当前旧范围行进入`plt_migration_issue`且不进入指标或当前范围。
- DeliveryScopeDetail：旧表无可靠地点/维度明细，不从名称、备注或附件推导；仅新业务明确创建。

迁移统一使用PLT Owner四表：每个源行原值进入`plt_migration_source_record`；合格的旧键—新键关系进入`plt_external_key_mapping`；空键、多义、状态未知、数量/单位/模型/地点缺失进入`plt_migration_issue`；`plt_migration_batch`保存抽取、资格、迁入和问题计数。前向新增列不设伪默认值，既有V70行缺正式事实时原值保留并由查询资格谓词排除，不写不存在的核对业务状态。

结论：现有F-PROJ-002 COM切片可以保留并扩展，但不是完整COM-01。Feature Ready前须由独立复审确认上述复用判定及物理/迁移合同；实施不得修改旧CRM功能或把测试种子、附件/XLSX当成权威来源。
