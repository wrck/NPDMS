# 设备清单慢查询：本地修复及测试库SQL复验

2026-10-23。工作树：`C:/Users/user/Documents/Codex/2026-10-06/task-3/project-detail-followup`；分支：`fix/dppms-migration-consolidation-20261012`。

按需求方本轮明确范围，本地修复，连接`172.17.90.178:13306/npdms`只读复验SQL。未发布远端，也未加载本轮代码至59284；下述SQL时间不等于HTTP或浏览器页面时间。

## 定位与修复

当前源码默认页先通过COM取得正式授权合同，随后再查询AST设备合同候选并做Java交集。本轮真实数据已完成上一项组织回填；COM授权合同79,187个，设备候选查询返回74,995个原文键，精确交集74,643个。该候选SQL本次单独耗时9.2963秒，是当前默认页的主要额外查询成本。

默认无筛选页改为直接将COM正式授权集合传给既有最终可见SQL，取消这一次AST合同枚举。最终仍按`CAST(d.contract_no AS BINARY)`精确匹配；没有设备的授权合同不会命中设备。原候选查询排除了已通过组织授权的设备，这些设备本就通过原权限并集可见，取消预枚举不会增加授权。SN等有业务筛选的查询和设备详情仍走原小候选路径。

SYSTEM组织范围及PROJ项目范围已经支持同步请求内复用。本轮装配测试核实：AST与COM各自调用Owner API，同一请求成功读取只执行一次，下一请求重新读取；不将两个API调用点误报成两次数据库查询。列表行查询与总数查询各一次，分别承担取页和计数，均保留。

先前已经完成的“PROJ授权与AST已有项目键求交”、当页延保聚合、组织字段直接投影继续保留。本轮未改SQL匹配条件、租户隔离、Owner授权、合同唯一性/发货公司冲突保护、分页排序或数据范围；未引入字符集转换、跨模块联表、跨请求缓存或新的数据阻断。

## 测试库串行实测

直接加载当前工作树真实MyBatis XML与场景Query对象，生成BoundSql并独立绑定参数执行；连接的`@@transaction_read_only=1`。核验现有admin启用且拥有PROJ既有super_admin角色，SYSTEM当前有效组织范围为0。输入项目为PROJ该分支对应的79,429个当前租户项目，并未据此授予全部设备权限。

| 查询 | 修复前SQL秒数 | 修复后SQL秒数 |
| --- | ---: | ---: |
| PROJ当前项目输入 | 0.5414 | 0.4901 |
| COM正式授权合同 | 2.3387 | 2.0796 |
| AST设备合同候选 | 9.2963 | 取消 |
| AST已有项目候选 | 0.0033 | 0.0026 |
| 最终可见设备总数 | 2.4189 | 2.2921 |
| 当前页10条及原投影 | 0.6352 | 0.6826 |
| 合计 | 15.2338 | 5.5470 |

本次串行查询合计减少63.59%，主要来自取消候选枚举。其他SQL的时间差为本轮实际测值，不据此宣称各条SQL都稳定提速。时间包含数据库执行、参数发送及结果读取，排除JVM启动、生产拦截器、其他权限读取、HTTP和页面渲染；不是多轮压测或延迟承诺。

正式合同SQL仍有79,431个参数；最终计数SQL有79,188个参数、约158万字符。没有将剩余SQL生成/解析成本误计为数据库执行时间。本轮未为压缩SQL改写匹配或引入额外JSON/临时表机制。

结果核验：

- 两套查询的设备总数均为3,154,458；当前页10条的全部投影字段和顺序完全一致。
- COM授权结果前后相同。直接传递授权比旧交集多4,544个键，按原最终精确条件对这些键单独全范围计数为0，核实没有新增可见设备。
- H2执行真实Mapper的用例验证：大小写和尾空格变体、其他租户、删除记录、未授权合同及NULL合同不会因省去候选枚举而取得可见性；空权限仍返回空。
- `ast_device`及COM合同列均为utf8mb4，排序规则不同；生产仍通过既有Owner接口和原文精确条件处理，没有增加CONVERT/COLLATE或改变列定义。

## 自动验证及现场采样边界

新性能回归先在修改前失败，明确命中冗余候选扫描；新增精确匹配SQL用例当时通过。修复后7类29项定向检查全部通过，覆盖默认页、省去枚举、精确SN路径、租户、三路权限、合同Owner保护、请求内复用、异常清理和重试。使用工作树固定JDK25/Maven、同一构建锁及既有依赖仓库。

另一次正式登录的59284旧运行包请求44.149秒，业务成功、总数3,154,458、10行。其PROCESSLIST采样同时捕获了其他来源连接，且INFO在65,535字符处截断；这些采样不能作为完整SQL、精确调用次数或44.149秒逐条归因。上述性能结论来自独立执行完整MyBatis BoundSql的只读测试，未将截断SQL拿来执行。

## 证据与重现

私有证据根：`D:/CodexTaskArtifacts/project-detail-followup/device-page-performance-20261023`。

- `native-current-summary.json`、`native-after-summary.json`：前后完整计时、总数及结果摘要；对应`*-bound.json`含真实XML生成的SQL及参数。
- `current-selectVisibleDeviceCount-plan.json`、`current-selectVisibleDeviceList-plan.json`：只读执行计划。
- `after-extra-contract-visibility-rows.json`：额外授权键命中数0。
- `native-actor-scope-proof.json`、`readonly-metadata.json`：正式输入依据、只读状态、索引与列定义。
- `baseline-trace.json`：旧运行包采样，保留其边界，不替代独立SQL测量。
- `RenderDeviceSql.java`、`render-sql.ps1`、`benchmark-current.py`、`benchmark-after.py`：仓库外只读测量工具。Renderer沿用工作树工具链及同一互斥锁；基线SQL及参数作为历史输入保留。
- 自动检查失败证据：`D:/CodexTaskArtifacts/project-detail-followup/domain-merge-20261023-092829/backend-tests-20261023-164656/proof.json`。
- 修复后29项通过证据：`D:/CodexTaskArtifacts/project-detail-followup/domain-merge-20261023-092829/backend-tests-20261023-164813/proof.json`。

定向验证命令为`python -X utf8 D:/CodexTaskArtifacts/project-detail-followup/domain-merge-20261023-092829/run-merge-tests.py --selectors D:/CodexTaskArtifacts/project-detail-followup/device-page-performance-20261023/fix-selectors.json`；原始SQL测量命令为同目录下的`benchmark-current.py`及`benchmark-after.py`，两者依次执行，不并发制造负载。

本轮仅修改列表范围组装与相应回归测试、证据文档。未执行DDL、迁移、真实同步或业务数据修正；未修改原任务、未操作59191或19191、未重启服务、未提交或推送。远端页面的修复后HTTP/浏览器耗时仍未经验证，不改变Feature总体完成状态。
