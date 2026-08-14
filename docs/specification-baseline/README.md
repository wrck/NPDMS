# 规格基线快照

本目录定义NPDMS实现仓库使用的只读规格输入。业务与设计唯一事实源位于规格仓库；本仓库只保存由Git提交和逐文件SHA-256锁定的快照，不在本地直接修订受管规格。

## 文件

- `allowlist.json`：允许同步的111个正式文件，必须使用排序后的精确相对路径；其中仅纳入Phase 1当前门禁的`README.md`和`gate-status.md`，不纳入过程证据或历史材料；
- `manifest.json`：应用同步后生成，记录规格仓库标识、40位源提交和逐文件SHA-256；
- `scripts/sync_specification_baseline.py`：默认只预检，显式`--apply`才写入；
- `scripts/validate_specification_baseline.py`：不访问源仓库即可校验本地快照。

## 同步

```powershell
$specRepo = 'M:/AICoding/CodexData/worktrees/09b5/项目交付平台'
$specCommit = git -C $specRepo rev-parse HEAD
py -3.13 scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json
py -3.13 scripts/sync_specification_baseline.py --source-repo $specRepo --revision $specCommit --allowlist docs/specification-baseline/allowlist.json --apply
py -3.13 scripts/validate_specification_baseline.py
```

源提交必须为完整40位提交。同步只读取Git对象，不读取源工作区未提交内容；allowlist内源文件有未提交修改或目标受管文件有本地修改时均拒绝应用。

## 升级规则

1. 先在规格仓库完成正式变更和提交；
2. 新增或移除正式资产时显式修改allowlist并评审；
3. 用新提交执行预检，核对新增、替换、保持和冲突清单；
4. 显式应用、离线校验并独立提交快照；
5. 禁止纳入gate过程证据、`docs/superpowers/`、archive、input、`需求/`或本机环境资料。
