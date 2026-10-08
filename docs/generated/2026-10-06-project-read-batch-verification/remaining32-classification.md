# 剩余32个读取缺口有界分类

本段仅选择COM/deliveryScope与旧IMP/installation记录复用公共projectId读取策略。其他30项维持拒绝；没有把辅助目录、修订、引用或批次映射成Project Owner。逐文件依据hash在同名JSON。

| 实体 | 分类 | 决定/依据 |
|---|---|---|
| AST/assetProductOfficial | NON_PROJECT_CATALOG | 无projectId；现目录权限为pms:imp-material-exch:query，不能等同资产目录原生读取授权。 |
| AST/assetProductType | NON_PROJECT_CATALOG | 无projectId；声明controlled-import导入权限，受控目录读取/冲突投影需独立来源映射。 |
| AST/device | STRICT_NATIVE_OWNER | 设备有可空projectId并另有客户/合同/组织归属，不能把当前项目字段作为唯一读取授权。 |
| AST/site | NON_PROJECT_CATALOG | 站点是客户/地点Owner，只有customerId；工程项目与物理站点不是同一个范围。 |
| COM/authorityCandidate | STRICT_NATIVE_OWNER | 权威候选是对账决策输入，权限为authority:reconcile；matchedOwner关系不形成公共读取范围。 |
| COM/contract | DOMAIN_SCOPE | 合同读取经ContractAccessService组织/公司/合同可见集合，不具有单一projectId。 |
| COM/crmExecutionOrder | DOMAIN_SCOPE | primaryProjectId是商务执行单关联，原生查询沿合同/组织授权；不推为独占Project Owner。 |
| COM/deliveryScope | IMPLEMENT_SHARED_PROJECT_READ | DeliveryScopeDO.projectId+租户；目录/控制器scope:query相同；原生page明确PROJECT_VIEW及租户/历史参数。 |
| COM/salesOrder | DOMAIN_SCOPE | 订单按ContractAccessService合同/公司范围读取，没有单一Project Owner。 |
| CUS/customerContact | DOMAIN_SCOPE | 联系人经customerId所属客户授权，不能凭租户或未证明的项目关系放行。 |
| CUS/customerMaster | DOMAIN_SCOPE | 客户组织/办事处/业务Owner范围；没有projectId，需客户公开读取契约。 |
| CUS/customerServiceLevel | DOMAIN_SCOPE | 等级按授权客户、组织及不可变等级版本读取，不能以Project VIEW替代。 |
| CUT/cutoverApprovalInstance | STRICT_NATIVE_OWNER | 原生detail区分发起人、当前合格审批人、终态摘要与改派投影；query+PROJECT_VIEW不足以返回完整目录字段。 |
| CUT/cutoverClosure | STRICT_NATIVE_OWNER | 原生detail先授权Task项目并requireSource校验任务/项目/冻结方案/审批/版本一致性；直接projectId绕过来源检查。 |
| CUT/cutoverConfigurationRevision | NON_PROJECT_CATALOG | 配置修订无项目字段；需要配置发布/有效版本/目录Owner策略，而非Project Owner。 |
| CUT/cutoverSpareApplicationReference | PARENT_OR_MULTI_OWNER | 备件申请引用同时持有cutoverTaskId、platformRequestId、externalRequestId与来源上下文；冗余projectId不代替原任务/申请授权。 |
| IMP/arrivalAcceptance | DEFERRED_OWNER | 到货验收QueryService与RequestContext明确COM/AST依赖接通前不注册生产实现；仅声明/Controller契约不能证明生产受信范围已可用。 |
| IMP/docTemplate | NON_PROJECT_CATALOG | 业务文档模板只有parentTemplateId，无Project Owner；模板发布与目录范围需单独策略。 |
| IMP/installation | IMPLEMENT_SHARED_PROJECT_READ | 旧imp_eng_installation记录的DO.projectId+租户、Controllerquery与目录权限相同；原生Mapper无更宽公共授权替代，新增公共Project VIEW裁剪。新InstallationRecord设备分配/确认流程未改造。 |
| PLT/authorization | STRICT_NATIVE_OWNER | 设备授权必须保留用户/设备/协议/命令模板/有效期约束，projectId不能授权凭据目录。 |
| PLT/authorizationGrant | STRICT_NATIVE_OWNER | Grant是主体、资源、动作、范围及有效期凭据，非Project业务根；需原生授权维护与可见策略。 |
| PLT/businessViewRevision | PARENT_OR_MULTI_OWNER | 业务视图不可变修订按发布Owner/版本授权，ownerContext不是项目字段。 |
| PLT/collectionTask | STRICT_NATIVE_OWNER | 旧task.projectId是String且任务依赖设备/协议/命令/凭证/有效期授权，不能转换后只套Project VIEW。 |
| PLT/collectionTemplate | NON_PROJECT_CATALOG | 采集模板按ownerContext/设备/发布版本选择，无Project Owner。 |
| PLT/deliveryRequirement | PARENT_OR_MULTI_OWNER | 交付要求有业务Owner、任务码、项目/冻结来源多种身份；不能把要求行当业务body或仅靠projectId授权。 |
| PLT/formInstance | PARENT_OR_MULTI_OWNER | 旧FormInstance目录不等于新动态表单Owner实例；需保留模板/执行Owner/字段模式与原生范围。 |
| PRJ/portfolio | PARENT_OR_MULTI_OWNER | 项目组合独立于项目树，ownerUserId不映射Project VIEW；需要组合Owner/成员范围。 |
| PRJ/project | DEFERRED_OWNER | 项目根创建/详情/历史迁移是已声明独立活动，本段不接入或改造其业务查询/生命周期。 |
| PRJ/projectTemplate | NON_PROJECT_CATALOG | 项目模板是租户目录/发布版本而非项目实例；不可将无projectId的模板映射到项目树。 |
| PRJ/taskAssignment | PARENT_OR_MULTI_OWNER | 分派只持有projectTaskId，原Task有执行/责任人范围且不是现79目录中的父模型；不能猜projectId或复用不存在的父目录身份。 |
| PRJ/teamBatchChange | PARENT_OR_MULTI_OWNER | 批次ALL/SELECTED横跨项目与用户移交明细，没有唯一Project Owner，需批次/明细集合范围。 |
| SRV/inspectionRule | NON_PROJECT_CATALOG | 巡检规则为发布/版本目录，无Project Owner；需服务域规则目录/版本授权。 |

这些停止项需要的具体工作分别是原生公开数据范围/投影适配、可信Owner关联或生产受信上下文接通；本段不猜关系、补造Owner或默认以租户开放。项目根维持独立活动；KNO、V374和旧阶段合同不改。
