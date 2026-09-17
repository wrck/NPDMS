# 模板执行解耦 v1.1：唯一实施进度记录

目标分支：wrck/NPDMS / codex/liteflow-remediation。

## 已提交基线

- 旧文档基准：75dfa7d98cade30f708759720400e6b8c01e5ec0。
- v1.1方案提交：f755c5721090d34bca6412aa39ded32ccd42093c。
- P0准备提交：a6313c6b4c84db03acbd0361ab57bb00a90e7ab5。
- 本次为P1.01实现提交；自身SHA由Git历史定位，下一环节补记真实引用。
- 最新约束：版本优先、不新增多层Hash、权限码选择动作、复用业务输入、pageUrl仅展示。

## 阶段状态

| 阶段 | 状态 | 下一环节 |
|---|---|---|
| P0 | COMMITTED_PENDING_VERIFICATION（准备文档完成） | [范围与来源](template-execution-decoupling-p0.md)；对应真实验证在各改动阶段执行 |
| P1 | IMPLEMENTING | P1.01代码/测试齐备；下一项P1.02完整版本冻结，P1.03/P1.04尚未完成 |
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

P1.02～P1.04尚未实现：没有启用新模板发布格式、没有删除旧Hash验证、没有改变运行快照或业务实体输入；权限码配置在模板编辑器内的完整保存/发布以及pageUrl展示仍待后续。P2～P9未开始本轮实现。

完整JDK25/Maven、Spring/JUnit、Vue/Vitest、数据库/API/浏览器均待验证。仅提交代码/测试源码及必要契约/本进度；没有部署、迁移、重启或新旧实例切换。
