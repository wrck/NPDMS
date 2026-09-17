# 模板执行解耦 v1.1：唯一实施进度记录

目标分支：wrck/NPDMS / codex/liteflow-remediation。

## 已提交基线

- 旧文档基准：75dfa7d98cade30f708759720400e6b8c01e5ec0。
- v1.1方案提交：f755c5721090d34bca6412aa39ded32ccd42093c。
- P0准备提交：a6313c6b4c84db03acbd0361ab57bb00a90e7ab5。
- P1.01实现提交：f6d28931608bb39c484c8b9a23da79b81e4a76e1。
- P1.02.1实现提交：65ec5445fc59ac13c2b81a875806936b790107f5。
- P1.02.2实现提交：3cba4ac7bfcb59ddc319827b39f9f674044fe198。
- P1-V1验证入口提交：4811a1a08c4c7e972a4c32092298cc08fc1c372d。
- 本次为P1-V2真实构建缺口修复提交；自身SHA由Git历史定位，下一环节补记真实引用。
- 最新约束：版本优先、不新增多层Hash、权限码选择动作、复用业务输入、pageUrl仅展示。

## 阶段状态

| 阶段 | 状态 | 下一环节 |
|---|---|---|
| P0 | COMMITTED_PENDING_VERIFICATION（准备文档完成） | [范围与来源](template-execution-decoupling-p0.md)；对应真实验证在各改动阶段执行 |
| P1 | IMPLEMENTING | P1.01、P1.02.1～P1.02.2代码/测试源码齐备；P1.02完整版本冻结仍未完成，下一项新格式冻结与发布接线；P1.03/P1.04未完成 |
| P2 | PLANNED | 逐业务审计配置与独立路径 |
| P3 | PLANNED | 权威结果及条件性能力 |
| P4 | PLANNED | 独立订阅及有界恢复 |
| P5 | PLANNED | 证据政策与正式推进 |
| P6 | PLANNED | 原业务输入、事实与事务 |
| P7 | PLANNED | 公共分派接线 |
| P8 | PLANNED | URL/实体/待办真实UI |
| P9 | PLANNED | 全阶段自审与实际验收记录 |

## P0 实际完成及验证

C0为当前目录三组业务14项操作和直接消费者边界；确认实际权限共用，未审计深层行为分配到对应阶段。不是全部模块独立化审计。

当前源文件字节核对后重跑13项已有Node客户端恢复测试，全部通过；对应TypeScript单文件严格检查通过。没有执行框架、JDK25/Maven、数据库、API或浏览器验证；不修改原Feature状态。

仅新增P0范围记录并更新本进度，未修改生产代码、旧历史、数据库或运行配置。实际下一项P1.01不能被本记录视为完成。

## P1.01 权限目录与操作消歧

基准：a6313c6b4c84db03acbd0361ab57bb00a90e7ab5。对应PM-03/F-PROJ-009，接口契约补充在SDS04a第13节；原Feature Ready/Done未修改。

实现：ProjectBusinessOperationProvider增加默认可选permissionCodes元数据；三组真实Provider登记原Controller中的14项功能权限映射。旧操作描述符、方法、版本和回调不变；无权限映射的旧Provider保留精确查找和目录读取，不猜测权限。

ProjectOperationPermissionIndex将权限与命令选择分开：Owner/实体/权限均精确匹配，必要时使用已有operationCode；多个动作或版本均无默认选中项。注册表复用此索引，保留真实方法存在性校验、精确find和唯一运行适配判断。不新增Hash或业务版本字段。

原目录GET增加可空permissionCode；新增同权限边界的只读GET /operation-catalog/resolve返回status/selected/candidates。前端API类型和调用已接入；不存在从配置解析结果直接授权执行业务的路径。RESOLVED与runtimeAvailable分别返回，不能因为其中一个版本暂不可运行就自动选另一个版本。

### 实际验证

- JDK21实际编译原Descriptor、兼容Provider、纯权限索引和同一套场景源码，javac -Werror -Xlint:all通过。
- 25个纯Java具名场景通过，0失败；覆盖唯一/共用权限、操作与版本歧义、跨Owner/实体、未知/重复映射、旧Provider、精确版本保留、不可变集合、读取次数和查询不执行命令。
- 修改的TypeScript API和新Vitest文件只做语法转译，0语法错误；不等于全前端类型/组件测试。
- 三个Provider原operations正文保留；SDS原有1～12节字节保留，仅追加本接口设计。对应源文件已核对原Git Blob，差异空白检查通过。
- 新增JUnit桥接、实际Registry/Controller/JSON/配置权限声明测试、三个真实Provider与Controller权限对应测试、前端API调用Vitest源码。这些框架测试未运行，不计入25个已通过场景。

### 尚未完成/启用

P1.01提交时P1.02～P1.04尚未实现（后续增量见下节）：没有启用新模板发布格式、没有删除旧Hash验证、没有改变运行快照或业务实体输入；权限码配置在模板编辑器内的完整保存/发布以及pageUrl展示仍待后续。P2～P9未开始本轮实现。

完整JDK25/Maven、Spring/JUnit、Vue/Vitest、数据库/API/浏览器均待验证。仅提交代码/测试源码及必要契约/本进度；没有部署、迁移、重启或新旧实例切换。

## P1.02.1 已发布快照读取与复制防降级

基准：f6d28931608bb39c484c8b9a23da79b81e4a76e1。按实施计划允许的独立子步骤推进P1.02；本子步骤不等于完整版本冻结完成，P1仍为IMPLEMENTING。

### 审计与实现

审计了TemplateExecutionSnapshot、TemplateCompiler、ProjectTemplateV2ServiceImpl、旧Hasher，以及ProjectRuntimeGraphFreezer、ProjectPlanInitializationService和ProjectPlanDraftService直接消费路径。本次仅修改模板V2读取/复制、运行图新写入口及新增格式Reader，不宣称其他计划/运行读取点已全部收敛。

- 新增TemplateExecutionSnapshotReader：先检查原始JSON对象必须明确携带受支持的整数schema，再绑定模型；拒绝缺失、null、字符串、小数、未知版本和溢出数字，不能利用模型默认值或类型转换恢复成v2。字符串输入检查版本后仍绑定原文，不经JSON树重序列化改变小数精度或旧Hash；Reader只负责格式边界，发布元数据与旧Hash仍由发布服务校验。
- 模板运行读取和revision兼容投影继续通过同一verifiedExecutionSnapshot；原有匹配候选也复用该路径。旧schema2/compiler/hash校验保留，不调用当前Compiler或活体Definition补救坏发布记录。
- 发布版本复制先检查PUBLISHED及V2快照，再读取冻结Designer；缺快照、Hash损坏、版本/编译器不一致、缺Designer或Designer格式/元数据异常均在创建副本前拒绝。真正Legacy发布版仍沿原显式导入路径；草稿复制入口不变。
- ProjectRuntimeGraphFreezer由“schema>=2”改为精确版本判定；兼容投影先检查原始schema，未知格式在合同/关系写入前拒绝。既有冻结合同构造、历史Resolver及业务规则不改。
- 新增三组JUnit测试源码：原始格式与原文小数精度/回读/旧Hash稳定性、公开复制负向/冻结Designer只读、运行图类型化及兼容入口写前拒绝。未修改或删除旧测试。

本次是恢复F-PROJ-009既有“未知格式拒绝、半V2禁止降级”的约束，不改变API/数据库设计，不另行修改SDS或历史Feature状态。

### 本次实际验证

- 修改前两个生产类和本进度文件的本地副本均已逐字节核对远端Git Blob。
- 使用JDK21的JavacTask.parse解析全部6个本次Java文件，0语法错误；这不是类型检查、JDK25构建或JUnit执行。
- 差异自审核对：原发布方法、旧Hash算法、业务操作、历史Resolver及数据库文件不在改动范围；修改差异通过空白检查。
- 当前环境只有JDK21、无Maven，且依赖下载未成功；三组JUnit及原兼容回归未执行，不记录测试通过。Spring装配、JDK25/Maven、数据库/API/浏览器仍待真实验证。

### P1.02剩余范围 / 下一环节

完整新版本格式、全部规则/决策闭包与执行配置冻结、发布不可覆盖、Compiler/Publisher/Reader/复制/计划全部消费者接线、数据库可空契约检查及新旧格式回归仍未完成。审计发现ProjectPlanDraftService.prepare仍直接反序列化effective快照，计划初始化也需核验传入快照与来源版本的一致性；这些是下一增量的确定待整改项，不记成仅待验证。

新格式发布保持未启用，旧Hasher保持不变；不通过本子步骤对历史快照补字段、重算Hash或自动迁移。P1.03/P1.04及P2～P9继续按原顺序推进，没有部署、迁移、重启或实例切换。


## P1.02.2 计划快照来源与精确合同集合

基准：65ec5445fc59ac13c2b81a875806936b790107f5。对应PM-03/F-PROJ-009及PM-01直接计划消费者；恢复既有精确版本/不可变快照约束，不变更API或数据库设计。

实现：ProjectPlanDraftService.prepare在编译草稿和读取运行实体之前核验有效计划ID、租户、项目、状态及原始快照格式；预览和正式启用复用同一路径。异常有效快照不能经对象默认值、当前Compiler或Designer恢复为合法版本。

ProjectPlanInitializationService回读项目指定的已发布模板版本，比较完整类型化快照（包括匹配、收口、准入、准出、规则等），不同则在计划/轮次/定时器写入前拒绝；持久化和定时器使用该已校验发布快照。合同集合不再只比数量：新增无框架TemplateExecutionContractSet验证节点精确覆盖、节点/实例/合同ID唯一性和有效ID。阶段合同核验租户/项目，任务合同核验其实际持有的租户字段；任务归属仍由原创建链保证，本次不虚构DO中不存在的projectId字段或新增跨表查询。

文件：两个计划Service、新增TemplateExecutionContractSet及其场景/桥接测试、两个计划边界测试，原初始化测试仅补充有效发布/范围夹具并保留全部旧断言。本进度是唯一状态记录。

实际验证：修改前两个Service、原初始化测试及进度已核对Git Blob；JDK21以-Werror -Xlint:all实际编译新合同集合生产类及场景，26项通过；8个Java文件语法解析0错误；差异空白检查通过。框架测试源码覆盖完整快照差异、来源替换、合同错配、异常格式、预览/启用写前拒绝及正常阶段/任务轮次；JUnit/Spring/JDK25/Maven/数据库/API/浏览器未运行。当前容器Git和依赖站点DNS不可用，无Maven，不复用历史PASS。

旧Hasher、Compiler发布格式、业务命令、历史Resolver、数据库和部署配置保持不变。下一项仍为P1.02新版本完整冻结/不可变发布及其全部Reader接线，未启用新格式，不将P1父阶段或P2～P9提前标为完成。

## P1-V1 分支定向构建与可复现验证入口

基准：3cba4ac7bfcb59ddc319827b39f9f674044fe198。实施计划I12的本阶段验证支撑，不替代P1.02生产出口或P9全阶段验收。

已审计现有integration-code-regression.yml：仅PR/master及手动触发，前述分支提交不能据此推导CI执行。新增独立template-execution-decoupling.yml，在目标分支相关代码推送时用JDK25/Maven编译依赖并执行Template、ProjectPlan、RuntimeGraph定向测试；缺少关键测试报告、零测试、失败、错误或跳过均明确失败。复用仓库现有Actions版本，不修改既有工作流、生产服务、部署或数据库。

只授予contents:read，checkout不保留凭据；日志与JUnit报告留在本仓库Actions artifact。手动运行或提交明确含[复现包]时，额外归档当前HEAD的Git跟踪源码用于隔离复现，不包含.git、Runner配置、Maven settings或未跟踪文件，保留3天。

本地实际执行：YAML结构检查、所有run块bash -n和内嵌Python语法检查通过。工作流提交时尚无该HEAD的远端运行结果，不能记录CI或JUnit通过；本地仍无JDK25/Maven，Git直连DNS失败。下一生产项仍为P1.02新格式冻结/发布接线，P1与P2～P9状态不变。


## P1-V2 操作公开契约的显式序列化依赖

基准：4811a1a08c4c7e972a4c32092298cc08fc1c372d。首轮真实JDK25 CI（run 35255389400 / job 105317637207）FAIL：project-api的ProjectOperationCommand和ProjectOperationResult引用tools.jackson.databind.JsonNode但模块没有声明Jackson依赖，导致4项编译错误；后续Project测试尚未执行。不是依赖下载故障，不把这轮写成测试通过。

修复：project-api显式声明仓库已管理版本的tools.jackson.core:jackson-databind，保持原DTO、权限和运行语义不变，采用与platform-api相同的依赖方式。为后续独立复现，定向工作流仅在提交显式含[离线复现]时归档JDK、Maven bin/boot/lib及repository；不归档Maven settings/toolchains、Git配置、Runner配置或环境转储，产物仅保留1天。

本地POM/XML、工作流YAML、Shell及Python语法检查通过；此修复尚待新HEAD真实构建。已通过源码归档获得4811a1a0完整工作树，本地Git tree与远端e46835e19b4fabbee87d81c85b99a81ab338c092一致；后续直接对准确源码实施，不凭片段猜测。P1.02生产出口及P2～P9仍未完成，不覆盖前述FAIL。
