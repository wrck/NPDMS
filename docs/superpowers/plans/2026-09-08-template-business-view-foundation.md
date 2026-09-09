# 单一模板与业务视图基础实施计划

## 目标、架构与批准

需求方已在本轮批准：直接升级现有模板，不维护双模型；在业务视图注册/版本/Owner/权限基础上增加PAGE与DYNAMIC_FORM复用，方便后续专用页面接入。PLT持有注册，PROJ持有模板、WorkBinding和完成判定，领域页面/表单使用原Owner API。技术栈保持JDK25/Spring/MyBatis/MySQL8.4及Vue3/TypeScript/现有动态表单引擎。

本计划同时是F-PLT-003、F-PROJ-009唯一当前计划；既有Feature的直接消费者增量仍引用其当前Task，不替代其唯一计划。写边界/交接以DU-20260908-TEMPLATE-BUSINESS-VIEW为准。

## Task 1：契约及领域基础

- [x] 已批准Scope、Owner、单模型及PAGE/DYNAMIC_FORM两条复用路径。
- [x] master提交DU认领0a3a7e8a，隔离工作树p903准入通过。
- [x] 在SDS08落位视图来源、注册生命周期、图与分支精确领域规则。
- [x] 在SDS09/机器合同加性落位view_source、dynamic_form_revision_id及允许草稿的published_at；约束视图来源与字段组合、停用时间。
- [x] 在`pms-module-project/.../domain/deliveryconfiguration/`实现唯一阶段图结构校验和只读后置解析，56项行为测试通过。
- [x] 在`pms-module-platform/.../domain/businessview/`实现视图字段/受控目录匹配/草稿发布与停用历史保护，23项行为测试通过。

领域组件无REST、数据库写入或Spring生产装配；只实现已明确规则，不调用业务实体创建。各完成后补正向、失败及历史测试，不用Mock宣称集成完成。

## Task 2：共享契约交接与持久化

PM01在d31f7903释放共享文件，本DU经7a27daf8、397f5d77接续并同步，准入通过。SDS10注册/定义/模板管理契约已落位，继续集中实施：

- `/api/v1/pms/business-views`的草稿、版本、发布、停用、精确查询；明确If-Match、Idempotency-Key、功能权限、Owner权限交集及错误响应。
- 定义/模板管理API的复制、验证、图与精确引用；复用现有模板身份与前端入口。
- 注册及定义Mapper查询只用场景化Query；锁/联表/动态集合进入XML，不写SQL注解。
- 加性Flyway采用实际master窗口下一编号，不提前预约。不存在新模板根、LEGACY/GRAPH运行开关或历史推图。
- 版本、审计、幂等及Outbox复用已有PLT完成点，不增加通用注册工作流。

## Task 3：模板与消费者统一

- 精确定义库与模板组合、StageTransition、Stage/Task绑定及交付要求；发布成功后必须能被同一消费链解释。
- 正式创建同事务冻结图、节点、任务树、绑定、Gate及ACC交付要求；任何失败整体回滚。
- 阶段读取/解析统一使用冻结关系，不保留S编号或排序兜底。
- 切换前核查存量真实可写项目是否有可证明关系图；没有证据则登记受影响对象和迁移限制，不覆盖历史、不擅自禁写、不擅自保留旧算法。
- 共享项目详情及F-PROJ-001 Task已由PM01释放并纳入本DU；运行切换受Q-FPROJ009-001实际历史缺图约束，不因写边界释放自动获历史迁移授权。
- Q-TPLACC-001独立验收/范围绑定不实施；其缺口不扩大阻断独立模板基础。

## Task 4：页面/动态表单宿主与配置界面

- `src/components/BusinessView/`提供共用宿主、受控精确组件映射及上下文接口；不改全局routerHelper的菜单行为。
- PAGE用薄适配器复用`ProjectRequirementAnalysisPanel.vue`，保留原业务API和版本/权限；不把SOL业务表单改走通用PATCH。
- DYNAMIC_FORM复用`DynamicFormInstanceForm.vue`的codec/runtime/保存链，抽出共用内容组件，原抽屉继续使用该组件。
- 处理changed/dirty-change及切换保护，读视图不创建实体、不完成任务。
- 继续使用`project-templates/index.vue`入口，拆出定义、关系、绑定、交付要求和校验组件；不建立平行模板页面。

## Task 5：种子、验证与交付

- 受管种子覆盖所有定义类型、S0～S6全要素、S0→S4、匹配优先级/部分限定/无匹配/停用；不伪造CRM属性值或不存在的Provider。
- JDK25定向领域测试→API与数据库测试→前端类型与组件测试→真实浏览器配置和实际宿主闭环。
- 数据库固定使用23316/npdms_test，先确认当前Owner窗口；不reset/clean/repair，不中断其他会话服务。
- 新迁移按适用义务验证空库、批准基线升级、validate与重复migrate；无隔离/清理授权时记录实际未执行，不伪造PASS。
- 最终复核单模板、权限交集、幂等/并发、回滚、历史保护、无任意执行及Owner边界。
- 只提交本DU文件，master选择性集成；状态及投影集中收口，不自动推送。

## 当前非完成项

契约不明或写边界冲突时仅阻断依赖部分。F-PLT-003在SDS08/09/10落位后已Ready；F-PROJ-009配置端可实施，完整运行切换仍受Q-FPROJ009-001约束。领域79项测试仅证明对应规则，真实消费者、MySQL/Flyway和浏览器验收分别记录，不自动Done。
