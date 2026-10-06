# 公共组件修复与33文件原生增量联合验收

整合基线为 c41c12e054c5830b0390aa1fa51820a9b4ed3b42；仅合入已授权原生增量 be007ec→9b7b1b54（33文件）及本次公共修复。原生32文件保留原字节；唯一冲突的NativeAttachmentDeliveryMySqlTest保留c41拒绝/锁/权限回归，同时加入原生extra hooks。原来的两入口证据文件原字节保留，新联合证据在本目录。

公共文件Uploader与ReferenceList捕获请求Owner/字段/实例/文件槽位及世代，等待点、进度回调、失败、卸载和finally都检查请求归属。切换后忽略旧成功/失败回执，保留新操作及其幂等键。三个独立反例在c41为3失败，当前转绿；真实动态表单实例41→42及共享组件同实例浏览器回归通过，没有通过Host加key重建。原生选定执行上下文与同一文件重试语义保持。

公共读取装配真实发现4个父级读取因BusinessEntityAccessPort与其Collection别名歧义而失败。以实际Spring配置和SQL复现后，对规范读取Bean指定Primary；新测试同时验证父级读、权限拒绝、租户隔离与两接口同一实例。最终普通JAR的4个生产父级实体均读取通过。

| 最终源码对应检查 | 结果 | 限制/证据 |
|---|---|---|
| 42模块普通应用JAR/73测试类选择集 | 573通过，0失败/错误，3跳过（576项） | [逐用例结果](test-results.json)；不代表全后端 |
| 两新增原生入口MySQL/真实HTTP | 21通过，0失败/跳过 | 实际服务/事务、公共文件与材料；角色/项目/存储端口为专用夹具 |
| 原六入口MySQL/真实HTTP | 57通过，0失败/跳过 | 含12个VIEW-only/query-only真实拒绝用例；保留所有断言 |
| 到货/核对清单MySQL/真实HTTP | 5通过，0失败/跳过 | 实际原生保存、归集、回滚、撤回 |
| 上述10页原生浏览器 | 全通过，pageErrors均零 | 新截图/JSON在browser；没有完整生产角色树/外部存储声明 |
| 联合前端16文件选择集 | 107通过，0失败/跳过 | 包含迟到成功/拒绝/初始化/进度、提示框切换、ABA、卸载、解绑重试键、下载三个等待点；未运行全部UI/typecheck/build |
| 共享组件及真实动态表单切换浏览器 | 3场景通过 | 同组件实例；文件HTTP端口受控。一次字段页文件控件等待超时保留，原断言诊断复跑通过，原因未确定 |
| 普通JAR实际java -jar/Tomcat/登录过滤链 | 21请求通过 | [回执](full-tomcat-acceptance.json)；mock-enable=false，无测试库。登录fixture直接SQL写角色曾缓存陈旧而失败，独占Redis刷新后原断言通过 |
| 最终79生产目录真实Spring/Mapper读取 | 42读取通过，34范围缺失时安全拒绝，3缺列失败 | [逐实体清单](owner-inventory.md)；45显式范围绑定，不等同全接入 |
| 旧阶段合同/outbox | 复现CURRENT_STAGE_CONTRACT_REQUIRED；outbox=PENDING/retry1 | [真实运行证据](runtime-probe.json)。1002项目7阶段graph_version为空、合同0；有效新夹具经原冻结器产生1合同并synchronizeStage成功，事务回滚后0夹具行/旧合同仍0 |
| 原完整Flyway迁移/单一会话回调实验 | 两次失败 | 原V374 line72/1267；全新库unicode会话回调仍line31/1267。无repair、skip或历史SQL编辑 |

普通JAR SHA-256：b44682167c73d1f06fece78d4273dc9f49568c1f590d52266c31cdee2ced40bc。关键16个运行class与对应编译class逐字节相同，见[检查](normal-jar-class-checks.json)。全部增量源码hash见[source-files.json](source-files.json)，后端相关hash见[backend-source-files.json](backend-source-files.json)。这些选择集/夹具与历史结果有重叠，不能累加为完成总数。

## 尚未闭环与下一步

- 当前79仅2模型声明专业统一操作、77操作列表为空；所有scopeBinding均为空，不能以继承BaseBusinessEntity、目录注册或DELIVERY元数据宣称默认CRUD/表单/交付桥接全部已接入。34读取范围缺口逐项保留；项目操作目标另为8个原生身份，不能把目录/修订/引用/授权/交付要求辅助表都算作项目业务目标。
- 最小下一段公共接入是为已有明确projectId与原生权限证据的IMP/configuration、IMP/jointTest复用OwnerProjectReadScopePolicy，验证越项目/越租户/只读角色及写入继续拒绝；不复制每Owner策略、不开放租户泛读、不顺带开放旧模型写入。
- 3个读取失败分别依赖未运行的V379产品列重命名（materialRequisition/externalProcurement）与V386完工证书字段；不降低Mapper投影或断言来掩盖V374阻断。
- 项目1002的旧阶段没有冻结合同/版本来源。本公共活动不制造STAGE_NATIVE合同、不放宽唯一合同断言，不实施项目创建/详情/历史迁移。若要恢复这些旧项目重评，需要独立历史迁移授权和可追溯的冻结执行快照/Owner映射；本轮异步链路仍非ready。
- V374同时混用业务列unicode_ci与0900_ai_ci，单一连接默认无法使两个原比较都相容。只提供[只读预检](v374-readonly-preflight.sql)及[实测](v374-preflight.txt)。更晚的前向迁移无法消除先发生的V374失败；修订历史迁移/repair/skip、真实数据迁移与部署仍未授权。
- KNO增量继续hold；四禁改路径不变或不存在；资产生成下载生产服务、公共NativeGeneratedFileService锁后材料检查及全局上传完成路径未覆盖/改造。完整后端/前端回归、两个JUnit浏览器条件入口、真实ClamAV/外部存储/设备HTTP未运行，见[清单](not-run.json)。

失败/跳过不删：三UI红例、父级读取Bean红例、迁移错误、fixture启动/缓存失败及浏览器等待超时见[failure-evidence.json](failure-evidence.json)。原始日志/XML留在忽略目录，只有hash/摘要提交。所有专用Compose与Tomcat已清理，共享13306 MySQL与16379 Redis的原容器保持运行，见[清理](cleanup.json)。
