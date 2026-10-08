# 已应用迁移原文的只读取证

这些文件是从用户授权的本机 NPDMS 工作树读取的历史原文，只作为来源证据保存，不位于 Flyway 执行目录，不自动执行、不替换主链迁移。SQL 字节原样保留，`.gitattributes` 禁止对本目录 SQL 换行归一化；manifest 保存 SHA256 和逐行 CRC32。

- V388/V389/V390/V410：`E:\AICoding\Projects\NPDMS\sql\migrations` 中未跟踪、未提交文件；通过同仓库 `git worktree list` 定位，不是全盘扫描。四份 Flyway checksum 均与源库历史一致。
- V409：原 `M:\AICoding\CodexData\worktrees\4600\NPDMS` 中 tracked、无修改；来源 `27e04716dd26bc6864e1b47c71396fc0910b4849`，checksum 与源库一致。
- V411：`C:\Users\user\Documents\Codex\2026-10-06\task-3\project-detail-followup` 中 tracked、无修改；来源 `4d522dbc35e528ba385de0f257950b7250744251`，checksum `-1686642005` 与源库一致。
- 远程 `6e4408bb` 的 V400 项目详情 DDL 与本地 V411 相同，但少一行编号说明注释，checksum 不同，不能直接替代历史原文。

另外，目标 V388–397 与源 V399–408 的十份内容 checksum 匹配，但版本号不同；主链仍不能直接用作该既有库升级目录。找到原文不等于迁移对齐完成，更不等于完整应用或真实模板自动完成验收通过。全量历史匹配清单继续核对，禁止 repair、跳版本或篡改历史。排序规则前置对齐裁决仍待用户答复。
