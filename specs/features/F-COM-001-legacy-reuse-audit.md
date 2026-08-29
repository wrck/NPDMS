# F-COM-001 旧实现复用审计

> Requirement：`COM-01@V1`
> 审计结论：`COMPLETE`
> 规则：旧实现逐项分类为`DIRECT_REUSE / COPY_THEN_ENHANCE / DO_NOT_REUSE`；未列项不得静默进入Technical Plan

## 1. 审计范围

- `pms-module-commerce`全部API、DTO、Service、DO、Mapper和测试；
- `sql/migrations/V70__commerce_delivery_scope_slice.sql`；
- F-PROJ-002对`DeliveryScopeApi`的消费与回归契约；
- Yudao CRM合同API、列表、表单、详情、审批和权限组件；
- 已批准COM物理DDL、ADR-0023当前范围粒度及ADR-0036/0037的Feature-forward差量。

仓库中不存在其他COM合同、销售订单、订单行管理后端或PMS Commerce页面；不存在ERP网络适配器。旧源库及迁移证据仅用于批准物理模型，不授权本Feature执行历史数据迁移。

## 2. 判定矩阵

| ID | 旧资产 | 判定 | 新目标/使用方式 | 依据与约束 |
|---|---|---|---|---|
| REUSE-01 | `DeliveryScopeApi`及DTO：可用切片、预览、应用 | `DIRECT_REUSE` | 保持公开方法与F-PROJ-002错误/原子语义；目标实现适配完整COM模型 | 已有稳定跨Context调用方；PROJ不得依赖COM实现或表 |
| REUSE-02 | `DeliveryScopeService`数量、版本、锁、幂等和Outbox算法 | `COPY_THEN_ENHANCE` | 复制到新的完整COM应用服务后补Owner、办事处发生时快照、验收绑定及冲突守卫 | 现服务只处理父项目拆分，直接修改会把完整COM倒置为单一消费者切片 |
| REUSE-03 | `OrderLineDO/Mapper`与`com_order_line` | `COPY_THEN_ENHANCE` | 新建`SalesOrderLine`模型并迁入批准的`com_sales_order_line`；旧表仅作一次性转换输入 | V70缺合同/订单完整业务身份及部分必填目标字段；来源版本、单位精度、状态和必填快照必须按ADR-0036确定性转换 |
| REUSE-04 | `DeliveryScopeDO/DetailDO`及Mapper | `COPY_THEN_ENHANCE` | 新类绑定批准字段、主明细粒度、办事处发生时快照和有效区间；复杂锁查询进入Mapper XML | V70明细办事处编码只作来源证据，目标在范围主记录保存PROJ部门快照并要求产品/设备类型/序列号主体；现Mapper含SQL注解和位置参数，不能扩散 |
| REUSE-05 | `com_delivery_scope*` V70结构 | `COPY_THEN_ENHANCE` | 前向迁移执行受控结构转换，保留历史与当前范围，最终单Owner | 已执行迁移不得修改；目标DDL同名表语义不同，禁止长期双写或并行真值 |
| REUSE-06 | `com_outbox_event`与Assigned/Released事件 | `DIRECT_REUSE` | 复用事务Outbox和事件名；载荷按SDS补齐但不携带商务正文 | 事件设计已冻结Producer、Consumer和幂等语义 |
| REUSE-07 | `DeliveryScopeServiceTest`八类拆分测试 | `DIRECT_REUSE` | 作为兼容回归继续执行，并新增完整COM测试 | 现测试只证明F-PROJ-002切片，不证明COM-01完整完成 |
| REUSE-08 | Yudao CRM合同API、CRUD表单、列表、详情、BPM审批和CRM权限 | `DO_NOT_REUSE` | 保持零修改；新建PMS Commerce API、页面、路由和权限 | CRM拥有销售上下文而非ERP商务事实；可编辑CRM合同及审批状态与COM-01只读Owner冲突；用户禁止未授权修改Yudao基础平台 |
| REUSE-09 | Yudao/Element Plus通用前端组件和主题变量 | `DIRECT_REUSE` | 通过现有公共import用于新PMS页面 | 只复用通用表现组件，不复制CRM业务状态、权限或API |
| REUSE-10 | 当前仓库ERP网络连接实现 | `DO_NOT_REUSE` | 无资产；只定义`CommerceAuthorityWriteApi`和受控本地Provider边界 | 第三方平台功能只预留接口，不实现连接器 |

## 3. 实施约束

1. Technical Plan必须把REUSE-01～10逐项绑定到Task、目标文件和验证，不得以“整体重写”绕过旧行为回归。
2. 增强服务、DO、Mapper和页面先复制到新类/新页面后再改造；旧公开API在切换前后保持兼容，旧Yudao CRM资产零修改。
3. V70转换必须使用合入时的下一个Flyway编号，验证空库、当前基线升级、重复迁移和转换前后数量/范围/事件对账；不修改V70。
4. 新增查询遵守场景Query对象、`LambdaQueryWrapperX`和Mapper XML规则；不得新增SQL注解、`${}`、`.last(...)`、`Map`或长位置参数。
5. Implementation Done候选必须证明：F-PROJ-002回归通过、Yudao CRM路径零修改、新PMS Commerce真实浏览器闭环、ERP适配器不存在、无COM双Owner或长期双写。

结论：对应旧实现已全部完成三类判定，无`PENDING/TODO/待确认`项。本审计只锁定Feature Ready输入，不代表Technical Plan或Implementation通过。
