# 合同与订单公共读取独立 WIP 验收

**生产11文件冻结不变；原54项JUnit与149请求证据保留，本次未重跑。最后两个缺口新增95次真实HTTP请求及断言PASS。原始空库迁移仍在V374第72行FAIL，未修复、未跳过。**

工作目录 `/workspace/NPDMS-unified-commerce-read-20261006`，分支 `integration/unified-commerce-read-wip-20261006`，基线 `d4fca642fe17a230286ae9ef6ed5ea883f927701`。全部未暂存、未提交、未推送；旧19文件逐字节未变，未搬入旧四个项目读取绑定。本段只处理 `COM/contract`、`COM/salesOrder` 的公共读取。

本次收尾不是纯证据增量：只增加既有 `verify_commerce_real_login.py` 的两个HTTP测试场景及证据；11生产文件、JUnit源码和JUnit运行器都没有变化，复用同一正常JAR。最终仍为24文件＝11生产、3测试支持、10证据，已冻结。

## 实现与前后变化

一个商务内容读取组件复用原生 ContractAccessService 的当前公司授权及当前项目合同关系并集。公共层在既有 BusinessEntityContentReader 上默认关闭列表 opt-in，并统一选择唯一读取来源；旧只读来源不会开启无范围列表，来源冲突拒绝。

新增原生带范围的合同/订单根对象按ID读取，以及独立的有界键游标 Query DTO/XML。原生范围解析逻辑、公司编码二进制精确匹配、公司范围不额外按部门收窄、项目关系有效规则、原控制器投影与生命周期保持。XML 原范围条件提为本 Mapper 内共享片段，空组合范围显式关闭。原合同详情服务复用根对象读取后仍返回原有相关数据；公共根对象读取不加载关系聚合。

扩展失败测试复现6项失败：订单详情101次SQL、稀疏筛选单次102次SQL；两实体偏移游标在删除前行后漏行；两实体对隐藏/跨租户/不存在ID响应不一致。修复后详情只做一次带授权范围的ID查询，不先裸查存在性；三种ID均拒绝并返回相同诊断。分页按原生编码排序规则加ID续页，一次SELECT最多取201行、处理200行、输出不超过请求页大小（上限200），不做COUNT、不在一个请求内循环翻页。

合同敏感字段依原生 sensitive-read 权限置空；订单按独立 SalesOrderRespVO 投影，金额/币种等未投影字段始终不输出。筛选只观察授权后的公开投影；支持 EQ/NE/IN/LIKE字面包含/IS_NULL/NOT_NULL，隐藏字段和其余比较拒绝。稀疏筛选可返回空 PARTIAL 页，客户端须继续使用 nextCursor；现有前端 useBusinessEntity 按 completeness/cursor 判断完成，没有按空成员列表截断，但本段未运行浏览器。

完整当前差异见 [implementation.patch](implementation.patch)，14个实现/测试文件（11生产、3测试支持）及未变原生依据哈希见 [source-files.json](source-files.json)。前版13文件及前版补丁完整保存在 `.run/commerce-domain-reads-20261006/previous-46/`；所有历史结果重叠，不累加。

## 完整本段验收清单

- [x] 54项指定JUnit/MySQL回归：33项新商务测试、21项原有测试，2026-10-06T12:55:08Z BUILD SUCCESS。
- [x] 正常 yudao-server.jar 构建与真实Tomcat启动；不是测试范围JAR或MockMvc。mock-enable=false，租户开启。
- [x] 真实密码登录、安全过滤链；匿名、无角色、只有公共路由权限拒绝；同一已登录令牌不能替换租户。
- [x] 真实 PermissionApi/PermissionService 缓存代理及Redis；确认用户角色与菜单角色缓存已预热。通过生产 assign-role-menu / assign-user-role HTTP入口撤权与恢复，未在登录后用SQL改角色，未手动清Redis；同一令牌列表/详情立即按新权限拒绝。
- [x] 敏感角色经生产入口授予/撤销：合同金额允许→置空，敏感筛选允许→拒绝；订单金额仍不输出。
- [x] 真实 OrganizationScope Provider：公司/部门变化、大小写不匹配、损坏编码、未来生效、已过期、停用、跨租户事实均验证；公司级目录保留跨部门可见口径。
- [x] 真实 ProjectScope Provider：项目VIEW授权创建/撤销使用生产项目授权HTTP入口；普通读取用户不具有super_admin角色。当前项目合同关系结束、订单合同关系转移立即关闭相应读取。
- [x] 当前合同/订单公司转移后，无正向授权缓存残留。受控接口测试同时覆盖Owner缺失/损坏/不可用及原生审计行为；真实Provider停服故障注入未运行。
- [x] 列表/详情投影一致；隐藏、外租户、不存在ID外部错误码/消息一致；修订身份拒绝，不能用当前对象代替历史。
- [x] 两实体删除前行后续页不漏；原生大小写/重音同值排序用ID续页；跨实体游标拒绝；继续页重新解析当前授权。
- [x] 最后95请求：真实公司与项目授权同时生效，包含公司独有、项目独有、两路重叠对象；合同和订单都只返回重叠对象一次。七个场景验证两路金额遮罩/允许、撤销公司一路保留项目及重叠、恢复公司后撤销项目一路保留公司及重叠、金额角色撤销、两路都撤销。使用同一读取令牌；项目/金额角色撤销走生产HTTP，公司范围变化为独占组织夹具。
- [x] 同一环境1万行授权稀疏样本：前9950无权限、末50有权限，50条在3页按17/17/16完整覆盖，无重复、无漏行。P_S记录真实SQL的RowsExamined10005/38/21、RowsSent50/33/16、SQL耗时22.668706/0.891211/0.651081ms，HTTP110.1/187.1/103.0ms。每页EXPLAIN FORMAT=JSON及EXPLAIN ANALYZE、原索引定义、完整可重放合成数据均保留。
- [x] 原始迁移表结构与索引上的1万订单：详情1次订单SELECT，356.8ms；稀疏唯一匹配经51次请求完整找到，每次1次订单SELECT，整轮48178.1ms。SQL摘要/RowsExamined证据保留；这是该样本的工作量验证，不是生产负载压测。
- [x] 即使原生写用户，公共未声明CREATE/SAVE/DELETE/COMPLETE仍拒绝；HTTP拒绝前后商务表、公共账本、审计、outbox计数不变。JUnit另核验版本、原字段、无事件或项目锁副作用。
- [x] 旧19文件、四条禁止路径、KNO、V374、旧Asset下载服务未改变；无全局FileUpload.complete钩子、部署、合并、提交或推送。
- [x] 两个本段Compose项目容器/网络与正常服务器进程已清理；原共享MySQL13306/Redis16379保持原容器运行。

逐例JUnit结果、前后失败和未运行项见 [test-results.json](test-results.json)；[real-login-acceptance.json](real-login-acceptance.json) 根cases保留原149，finalTwoGaps保存新增95，数量不是累计测试数。新场景、缓存键、每页实际SQL/扫描行/耗时/执行计划及合成数据hash均在finalTwoGaps。正常JAR SHA256：`ecd3a00d89113f940c5207a3fa9ea6d154b8df24984e19bd3803bb97985e3f73`。

## 保留失败与限制

前版46项已归档；新增失败快照52项中46 PASS / 6 FAIL；首个修复快照52 PASS，最终54 PASS，不能相加。HTTP首轮7请求因脚本把内部ACCESS_DENIED名称当作外部消息而失败；生产已正确403拒绝，脚本改成既有外部错误码1010006005的精确断言，保留失败及原脚本，并重建独占环境再跑149请求。

原始Flyway两次在新空库的V374第72行报1267排序规则冲突，V373成功、V374失败及部分DDL状态保留。未改SQL、未repair、未指定target或skip。真实链验证使用V373加部分V374的测试库；HTTP通过不代表完整迁移通过。

当前索引的稀疏授权首条计划是 idx_sales_order_no 按tenant查10005行，再按BINARY公司条件过滤50条；后两条使用编码/ID键范围，仅扫描38/21行。LIMIT201限制取回行数，不保证物理扫描成本恒定。此样本首SQL约22.7ms，不是生产负载或固定性能阈值验收。建议后续另行授权后按真实分布评估保留二进制公司与项目OR语义的前向索引/查询计划；本次没有增加索引或修改历史迁移。1万行合成数据存于 `.run/commerce-final-gaps-20261006/authorization-sparse-fixture.sql`，SHA256 `a4396a8fd1841bbcb3cbfefe17fd3ed1f919f305950ef01e02dc2530d7639417`，只可重放到任务独占库。

OrganizationScope没有现有维护HTTP入口，本段只用独占数据库合成事实变化验证真实Provider；商务公司、部门、关系变化也是测试夹具，不声称覆盖生产维护写流程。角色和项目授权撤销走真实生产入口。键游标保留删除前行及原生排序语义，但不是并发快照；排序编码修改、游标之前插入等并发行为、生产负载与规模未验。整后端测试树、79模型、前端/浏览器、全部剩余Owner、默认运行视图和统一交付件桥接整体闭环未运行或未完成。

## 复现与证据

```bash
source /workspace/toolchains/activate.sh
COM_READ_MAVEN_SETTINGS=/workspace/toolchains/maven-settings.xml bash scripts/tests/verify_commerce_domain_reads_mysql.sh
```

JUnit运行器使用独占tmpfs MySQL27611及原兼容夹具固定端口27461，退出清理。真实链的独占Compose与完整正常服务器参数、JAR构建命令见 [verification-environment.json](verification-environment.json)，合成夹具及HTTP断言见 `scripts/tests/verify_commerce_real_login.py`。准备新空库/Redis并尝试完整原始迁移，保留原始失败；以记录参数启动正常JAR，health UP后执行Python脚本。新增缺口模式为 `python3 scripts/tests/verify_commerce_real_login.py --final-gaps`，使用 `.run/commerce-final-gaps-20261006/` 配置；只跑两个缺口，不重复原149。此脚本拒绝复用已有夹具ID，测试后停止仅本段服务器并清理本段Compose。换工作目录时替换记录中的绝对工作目录。

原生依据及投影边界见 [native-read-audit.json](native-read-audit.json)，资源与冻结检查见 [boundaries.json](boundaries.json)，原始日志/源码快照定位见 [private-evidence-manifest.json](private-evidence-manifest.json)，全部WIP文件哈希见 [wip-files.json](wip-files.json)。
