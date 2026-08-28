# F-PROJ-002 V1.8 数据库证据

- 环境：`npdms-50eb-mysql-1`，MySQL 8.4，隔离数据库 `npdms`。
- 当前路径：V75→V76成功，Flyway `validate`确认76项迁移全部有效。
- 空库路径：临时库从空结构执行V1→V76成功并通过`validate`，验证后已删除。
- 升级路径：临时库先以`-target=69`停在V69，再执行V70→V76共7项迁移并通过`validate`，验证后已删除。
- 重复路径：V76已存在时再次执行`migrate`返回`Schema is up to date. No migration necessary.`。
- 运行事实：V76按`tenant_id + code_root_id`从既有项目最大`project_sequence + 1`幂等修复`ROOT:<code_root_id>`流水；真实拆分创建两个子项目时分别获得连续且不冲突的编码。

V72～V75均保持不可变；V76首次试跑因列名歧义失败，确认未形成成功版本后按Flyway失败恢复流程修正并`repair`，最终成功版本校验通过。MySQL对历史迁移中的`VALUES()`语法给出弃用警告，但不影响本轮迁移正确性。
