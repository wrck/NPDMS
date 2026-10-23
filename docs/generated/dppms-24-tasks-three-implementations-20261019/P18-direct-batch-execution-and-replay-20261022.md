# P18去逐项目查询：批量承接真实复核完成

仅目标`172.17.90.178:13306/npdms`、新`BASE_P18`；25项无数据库定向检查通过，最终只读保护与真实三个Run已核验。

## 原首次新增与旧重放（A10历史凭据）

| 阶段 | Run | DB秒 | 读取 | 插入 | 引用后填 | 不变 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| execute | 2113149097510801410 | 619.913 | 82676 | 82381 | 80534 | 295 |
| replay | 2113151777511673858 | 612.428 | 82676 | 0 | 0 | 82676 |

## Direct新包对已存在数据的全量验证

| 阶段 | Run | DB秒 | 读取/不变 | 插入/更新/删除 |
| --- | --- | ---: | ---: | --- |
| preview | 2113155203763126273 | 26.067 | 82676/82676 | 0/0/0 |
| execute | 2113155401822355458 | 30.261 | 82676/82676 | 0/0/0 |
| replay | 2113155611881488386 | 25.377 | 82676/82676 | 0/0/0 |

首次真实新增82381条业务关系，80534条同公司原合同号引用已回填，82676条原来源绑定完整。1839条缺合同及8条原公司NULL仍先原样保存合同号，不阻断原数据承接；1847完整当前清单见私有证据链接。

新历史P18不再逐项目调用Owner/MANAGE或actor当前公司范围。每Run仍只执行四份既有来源投影各一次；原项目ID直存，原公司已由既有源SQL提供，最后一次postfill以来源company分组、同租户、二进制原合同号及当前verified目标ID控制，不猜NULL公司。同步execute/contract:relate功能权限、tenant及目标/来源绑定历史保护保留，旧功能不改。

Direct三阶段全是已有数据的零变更复核，不是新包首次插入基准，没有重置数据。只读80534精确正向匹配证明不是新包新增后填80534。A10数组hash和Direct流式newline-rowhash算法不同，不能跨版本硬称相同；各自阶段前后完整业务/绑定行（含审计时间和last_run_id）严格相同。

最终无活动Run，12源码hash、25归档、health与PID、schema/Flyway/原任务及非P18Run/业务保护核验见证明。发布时只执行SELECT/SHOW核验；本轮没有新增DDL或迁移，原任务、59191及P22/P33实际暂缓边界保持。

[真实回执及保护摘要](P18-direct-batch-execution-and-replay-20261022.json)；[后填生产映射证据](P18-direct-batch-execution-and-replay-20261022.postfill.sql)；[最终只读证明](D:/CodexTaskArtifacts/project-detail-followup/p18-direct-batch-20261022/final-direct-proof.json)；[完整1847缺配当前清单](D:/CodexTaskArtifacts/project-detail-followup/p18-direct-batch-20261022/actual-unresolved-relations.json)。
