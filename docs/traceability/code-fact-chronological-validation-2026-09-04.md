# 三分支按时间逐提交重放验证

- 基线：`220486237b9570ab3d2b0663df39c89be2a5ec69`
- 代码重放Head：`bc0175c5a5b2fe3174335b938b523254e29124e3`
- 来源固定HEAD：
  - `codex/f-acc-001-sds@58576666af682bed1a5ea8e40043ff77dde4b2c7`
  - `prereq-parallel-check-kKiAdn@cdfbd71a1722f9696c1dbb8713566de9e88ff97c`
  - `codex/f-cut-001-matrices@faed8387d09a82c018f5f03efbbf4b148ffbac69`
- 原则：所有模块代码均进入逐提交扫描；一个文件或hunk不符合时，不拒绝同提交及同分支其他代码。

| 检查 | 退出码 |
|---|---:|
| Requirement追溯生成 | `2` |
| Requirement追溯只读检查 | `2` |
| git diff --check | `0` |
| Flyway版本唯一性 | `0` |
| Maven Reactor模块唯一性 | `0` |
| 源码冲突标记检查 | `0` |
| 全Reactor Maven package | `1` |
| 前端依赖安装 | `1` |
| 前端TypeScript检查 | `97` |
| 前端生产构建 | `97` |

完整日志由本次GitHub Actions artifact保存。开放的Feature Gate继续保持IN_PROGRESS；上述检查不自动构成Implementation Done。
