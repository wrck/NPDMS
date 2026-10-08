# 项目创建与历史迁移本机取证

日期：2026-10-06。分支：fix/visio-template-followup。起始 HEAD：fda3bb55b0fb8e1fbd6f44f44cf4dbc21d015721。

## 已核实事实

旧系统仅只读，未连接其数据库。源码根目录为 D:/EclipseWorkspace/Parctice/PMS/PMS-struts；本记录只保存归纳与定位，不复制原源码或凭据。

- ProjectAction.java:373 insertProject：按合同预览；提交前查合同是否已有项目；调用 Service 后发通知。
- ProjectServiceImpl.java:150、418：提交时重新查询来源。项目名、客户、组织及订单日期覆盖表单；类型和代理商在表单为空时补充。主档、组、合同关系、成员、产品行一并初始化。旧组号算法自身标有并发缺陷，不复用实现。
- ProjectDaoImpl.java:155 -> sql-map-project-config.xml:1317；sql-map-config.xml:28 确认 config2 并未加载。
- 合同号筛选 pm_order_data_from_sap；orderType=0 排退货；按旧订单行 id DESC LIMIT 1；通过 orderExecNumber 关联 pm_project_property_from_sms。
- salesType=01 时项目名优先订单名，否则执行单名，NULL 再回退订单名；客户/公司/下单时间来自订单；销售人员、办事处、市场/系统/拓展/行业、客户项目名称、重大级别来自执行单。
- projectcreate.jsp:75、267、317：实际提交校验服务经理与所属公司；项目经理星号不等于 JS 必填校验。实施方式已从 SMS 映射时禁改；直签前缀与分类配置为旧规则，不能绕过新系统权威映射直接硬编码。
- Project.java:90：项目开始时间是指派项目经理时间，不能以订单创建时间替代。
- projectmodify.jsp:1979 起：详情包含项目、合同、销售、组织、客户、参与方、项目及订单日期。状态、成员及业务子页各自有来源。

## 现有实现与确定缺陷

- NPDMS /pms/project 是退役只读链；/pms/projects 与 project-master-detail 是新链，本次不增强旧链。
- CreationSourceResolver 当前分别选择最早订单和最新执行单，未保证取值来自同一订单执行单关系。
- ProjectManualCreationApplicationService 已有平台幂等事务、模板实例化、合同行锁绑定及执行单抢占保护；应复用。
- applyCommerceSource 仅写部分 CRM 字段，未写来源项目名、合同号、订单客户、销售类型，前端带入不能代替服务端取值。
- 创建页面异步选择合同无过期响应保护；无合同客户时提前返回，导致有有效订单的合同无法解析。

## 待核实/阻断边界

- 同合同多订单：同步模型按来源/公司/订单类型/订单号聚合，未保留旧行 id。不得用新自增 id 或下单日期冒充旧排序证据。
- 历史项目状态到新运行模型、历史模板/审批/门禁事实映射须按已批准映射处理；不得直接造 S0-S6 完成事实。
- 此记录不宣称实施或测试完成。原有 3 个未跟踪文件保持不动。

## 用户确认与实施边界

用户已确认创建只需订单、执行单基本信息；多订单使用合同下订单头选择，不复刻无法证明的旧行排序。历史先迁只读档案，不激活新运行流程。新创建继续使用现有公司/办事处联合授权、CUS 客户身份、平台幂等事务、模板冻结实例化与初始化；不拷贝旧状态 30/31/32、MAX+1 组号、通知即完成或弱校验。旧创建页面、退役 API 与旧源码保持不改。

签约方式、项目类别、实施方式仍依新系统字典明确选择；未根据旧前缀或非空重大级别自行猜映射。来源重大级别在模板匹配前取值；客户名称采用 CUS 权威身份，源客户名称保留在商务预览。项目开始时间不填订单时间；订单时间和同步时间在商务详情展示。参与方/销售人员展示已有项目记录或标注“执行单”的来源信息，不凭姓名授予新角色。

事务补充证据：config-spring/applicationContext-action.xml:136 把 ProjectAction 注入 projectServiceAgent；applicationContext-service.xml:93 的代理指向 ProjectServiceImpl；applicationContext.xml:116、124、132 使用 DataSourceTransactionManager / TransactionProxyFactoryBean，对 insert* 配置 PROPAGATION_REQUIRED。本次新实现复用平台事务而非复制旧代理配置；恢复后生产 Mapper XML 的 MySQL 锁/回滚 6 项已通过；完整应用事务与 HTTP 验收仍被公共启动循环阻塞，详见 README.md。

## 2026-10-07 来源身份复核补记

[最终客户身份只读追溯](final-customer-identity-audit.md) 更正早期“旧最终客户必填”的表述：该校验实际在 JSP 注释块中。用户指定 CRM 连接已确认；缺失的是本批执行单到真实最终客户 FK 的可验证关联，不再要求确认客户语义或笼统提供另一连接。早期购货方代码查询不能作为最终客户身份结论。十项仍未迁入；此次补记保留此前测试和取证的历史范围。
