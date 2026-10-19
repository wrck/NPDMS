# 18项失败定位与修复进度

P15已结束：运行2111093319956353026，PREVIEW_READY，读取3848169行，CREATED3847355、ISSUE814。未执行迁移。ISSUE仍需处理，不计为全量业务通过。

| 任务 | 定位 | 本轮处理 |
|---|---|---|
| P04 | 正式P01来源公司/部门别名尚缺；预览不创建依赖 | 保留失败，不回源猜配 |
| P08 | 原人员缺已核实平台账号 | 保留失败，不造账号 |
| P10 | 流式完整运行被固定传为full=false | 修复直写分块运行标志；加载后再核验V481 ERP列依赖 |
| P12 | fb_items.id全部NULL，实际无主键/自增；item34976行均非空唯一 | 恢复已审核产品编码身份item，保留原id字段，不再强制数字身份 |
| P13 | 整批预览未进入调用事务 | 新直写预览加入实际REPEATABLE_READ事务，不删除事务保护 |
| P16 | 流式完整运行被固定传为full=false | 修复直写分块运行标志 |
| P18 | 同上 | 同上 |
| P19 | 同上 | 同上 |
| P21 | V481未执行，project_id仍NOT NULL | 已有批准迁移文件；本轮不执行DDL，不造project_id |
| P22 | 流式完整运行被固定传为full=false | 修复直写分块运行标志 |
| P23 | 正式审计账号/绑定未齐；当前入口定义与早先通用直写记录不一致 | 保留本轮回执，未以旧Owner结果宣称直写通过 |
| P24 | 流式完整运行被固定传为full=false | 修复直写分块运行标志 |
| P25 | 同上 | 同上 |
| P31 | 缺int_sync_project_scope_publication，属于已有V482 | 不绕过发布范围校验，不执行DDL |
| P33 | 同上 | 同上 |
| P34 | SNAPSHOT全量读取超过10000行上限 | 改为RESTART_ALL流式，完整字段+重复次数身份贯穿分块，不伪造主键，不提高整批阈值 |
| P35 | 缺V482发布表；另有原八属性正式定义依赖 | 保留已批准范围及定义校验，不发布定义 |
| P37 | 冻结定义无可执行批准ID/Owner上下文 | 未借用旧153行试迁范围；仍未完成该配置闭环，不声称修复 |

定向验证：targeted-230420.log为55项（54通过，1原条件跳过），覆盖SyncRunTransactionTest、DirectProductTransactionTest、SpringJdbcStreamingReaderTest和ReviewedDirectConfigurationTest；首轮targeted-230143因旧产品身份测试及测试JVM192MB不足失败，保留日志，修正后增大仅测试JVM堆至1024MB重跑。额外执行器事务测试另见targeted-230607.log。

候选修复包：D:/CodexTaskArtifacts/project-detail-followup/direct-remediation-20261016/runtime-candidate/yudao-server.jar。构建targeted-230520.log成功，未覆盖现运行包。尚未重启59284加载，因此修复不等同真实预览已通过。未操作59191，未执行任何业务迁移或数据库DDL。
