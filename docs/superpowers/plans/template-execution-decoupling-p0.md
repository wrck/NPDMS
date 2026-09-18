# P0：v1.1 范围、字段来源与保护边界

基准：`f755c5721090d34bca6412aa39ded32ccd42093c`（方案修订），其生产树与 `75dfa7d98cade30f708759720400e6b8c01e5ec0` 一致。日期：2026-09-17。

本记录关闭 v1.1 的准备阶段，不表示已经完整审计下列业务的所有行为或真实验收通过。唯一进度见 [progress](template-execution-decoupling-progress.md)。不导入另一分支/会话的迁移任务。

## 已核对的依据与环境

已读当前 `AGENTS.md`、`docs/README.md`、工程链2.7及开发/发布边界、PRD V1.8相关模板与冻结说明、SDS04a、F-PROJ-009规格及Task相关部分。原Feature状态不因本记录晋级；SDS04a的旧schema2 Hash仍用于已有快照，P1.02改实际发布格式前修订相关设计和读取消费者。

本容器不是用户完整仓库工作区，无法检查用户机器上的未提交修改；不操作或覆盖该工作区。分支通过GitHub连接器读取和非强制快进写入。容器Git直连不可用，无Maven/pnpm，JDK21及Node22/tsc可执行。框架和真实环境测试待后续，不阻止已齐备环节代码/测试源码提交。

## C0：当前会话的真实接入范围

C0冻结为当前项目操作目录已登记的以下三组业务和14项动作，以及这些操作的直接模板、执行、回调与页面消费者。这里只确认目录/接口存在及权限对应，不把登记数量当作全链路审计数量。未列出的其他Owner不在本轮自动扩展范围。

| Owner/实体 | 实际操作 | 原功能权限 |
|---|---|---|
| SOL / SITE_SURVEY | CREATE | pms:eng-site-survey:create |
| SOL / SITE_SURVEY | UPDATE、CONFIRM、REJECT、ARCHIVE | pms:eng-site-survey:update（共用，必须区分动作） |
| SOL / SITE_SURVEY | DELETE | pms:eng-site-survey:delete |
| SOL / REQUIREMENT_ANALYSIS | CREATE、SAVE、COMPLETE、COPY | pms:requirement-analysis:manage（共用） |
| ACC / ACCEPTANCE | CREATE_DRAFT、UPDATE_DRAFT、PUBLISH、REVOKE | pms:acceptance:report:write（共用） |

来源是当前三份 OperationProvider 和三个实际 Controller，不从 label、ownerAction 或 operationCode 前缀猜权限。查询权限、文件下载权限与这些写权限不合并。ACCEPTANCE 是现有绑定对象类型，不能按报告名称改成另一个对象类型。

### 接入及消费者定位

- 工勘：`pms-module-engineering/.../controller/admin/sitesurvey/entity/SiteSurveyEntityController.java`、`service/sitesurvey/entity/SiteSurveyOperationProvider.java`、真实 SiteSurveyEntityService 命令与表单/地点/关联回调。
- 需求分析：`.../controller/admin/requirement/RequirementAnalysisEntityController.java`、`service/requirement/RequirementAnalysisOperationProvider.java`、EntityCommands/Access/ExecutionAccess/Provider及版本/表单/文件回调。
- 验收报告：`pms-module-project/.../controller/admin/acceptancereport/AcceptanceReportController.java`、`service/acceptancereport/AcceptanceReportOperationProvider.java`、报告命令、文件及原报告事件。
- 公共目录：ProjectBusinessOperationProvider/Descriptor、ProjectBusinessOperationRegistry、ProjectBusinessOperationCatalogController、前端 `src/api/pms/project/project-templates/operations.ts`。
- 运行/呈现：已在本会话交付的模板编译/快照、受控执行器、Owner Scope、结果Fanout/节点处理器、operationClient/Host、BusinessViewHost及C0页面。对应实际文件及全调用链须在变更所在阶段重新读取，不以此定位清单冒称已审查内容。

## 字段责任与不增加内容

| 信息 | 来源/处理 | 使用边界 |
|---|---|---|
| 原权限码、原动作 | Owner目录从现有业务权限声明；共用必须消歧 | 只用于选择，不授予权限；P1.01 |
| 模板发布版本及完整快照 | 模板发布服务；草稿CAS仍使用现有机制 | 全部执行规则随版本冻结，已发布不可改；P1.02 |
| 原业务DTO/ID/版本 | 原Controller/Command及Owner并发检查 | 不强制所有实体加项目身份或多套版本；P2/P6 |
| 已有操作版本 | 原登记目前均1；保留兼容，由解析输出承接 | 无歧义才能直选，绝不取latest；P1/P7 |
| 结果/修订/状态 | 原业务权威数据，按每项能力确认 | 历史和NEW_RESULT不具备能力则不启用；P3/P5 |
| 页面URL | 实际路由登记和参数映射 | 只是展示，不作为业务写API；P1.04/P8 |
| 轮次、证据、订阅 | 项目现有身份与模板版本、节点局部键 | 不反向要求普通业务提供；P4/P5 |
| 新Hash、多重schema组合、默认writerEpoch | 不建设/不要求配置；既有基础设施内部机制保留 | 只有真实缺口才能另作最小设计，不批量加表 |

## 旧行为保护和必须先核对的后续部分

P1.01只扩展配置目录的权限元数据与无副作用解析；不改变原业务Controller、Service状态、数据库、命令执行器、旧模板/JSON/Hash或原精确操作查找行为。缺少权限元数据的旧Provider保留可读和精确查找，不猜权限、不进入权限码简写。

P1.02前获取旧发布记录/真实序列化样本和Reader/Writer完整清单；历史schema2验证不能被新简化规则删除。P2前逐业务完整审计独立配置及所有修改回调；P3前审计真实结果/Outbox唯一来源及历史能力；P4/P5前核实存储复用、订阅/证据/正式状态锁序；P6前核实事务和全入口覆盖；P8前核实实际路由/页面及编辑恢复。以上尚未完成，不把准备阶段写成行为已验收。

不变的Owner业务规则、旧表/页面/API、历史快照、审计和事实不被本阶段重写。没有本地完整工作树，也不据此断言分支没有其他操作者；每次提交前重新核对HEAD及被改文件。

## 本次实际验证与出口

- 当前HEAD的 operationClient.ts 和 operationClient.selfReview.node.test.mjs 已核对Git Blob，与历史检查点中的真实源码一致后复跑。
- Node22：13项已有客户端恢复测试通过，0失败；单文件TypeScript严格检查通过。这是既有保护基线，不是新权限目录测试。
- JSON示例和计划文档围栏检查在方案提交前已执行。
- 未运行JDK25/Maven、Spring/JUnit/Mockito、Vue/Vitest、数据库/API/浏览器；不复用旧PASS为这些层级通过。

P0按v1.1出口完成：范围、来源、限制、保护和下一环节清楚。下一环节P1.01实现目录权限映射及精确消歧，补真实核心测试和目录API消费者；P1父阶段仍须等待P1.02～P1.04。
