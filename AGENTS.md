# AGENTS.md

## 1. Highest-priority source of truth

For business semantics, the approved PRD baseline is the highest-priority source of truth.

Priority:

PRD > Engineering Constitution > SDS > Feature Spec > Implementation Plan > Task > Code

Lower-level artifacts may refine higher-level artifacts but must not silently change business meaning.

## 2. Required reading before work

Before modifying design or code, read:

1. `docs/baseline/prd-v1.6.md`
2. `docs/engineering/00-engineering-chain.md`
3. the relevant SDS section under `docs/design/`
4. the relevant feature spec under `specs/`
5. the current task definition

## 3. Hard rules

- Do not modify PRD business semantics without an approved change request.
- Do not implement V3 or OUT_OF_SCOPE items as part of V1/V2.
- Do not invent business roles, approval nodes, state transitions, thresholds, gates, or data ownership.
- Do not bypass state machines by directly writing lifecycle status fields.
- Do not bypass server-side authorization.
- Do not expose or persist plaintext device passwords, private keys, tokens, or secrets.
- Do not directly access another bounded context's repository from a foreign module.
- Do not overwrite immutable history, snapshots, approved versions, audit records, or source evidence.
- Do not treat notification delivery as business success.
- Do not treat external HTTP success as business completion unless the contract explicitly defines it so.
- Do not weaken validation or authorization to make tests pass.

## 4. Missing or conflicting requirements

If a business rule is missing, ambiguous, or conflicting:

1. Do not guess.
2. Mark the item `BLOCKED_BY_SPEC`.
3. Record it in `docs/decisions/open-questions.md`.
4. Continue only on independent work that is not blocked.

## 5. Traceability

Every feature, API, database change, event, workflow, and test must reference one or more PRD requirement IDs.

Maintain:

`docs/traceability/requirement-matrix.md`

Required chain:

Requirement -> SDS -> Feature -> Code -> Test

## 6. External integrations

Every external integration must define:

- system owner
- direction
- authoritative fields
- request/response mapping
- source key
- idempotency key
- timeout
- retry
- compensation
- reconciliation
- degradation
- audit

## 7. Task execution protocol

For every task:

READ -> PLAN -> IMPLEMENT -> TEST -> SELF-REVIEW -> REPORT

Before implementation, report:

- files to modify
- requirement IDs
- domain impact
- API impact
- database impact
- authorization impact
- state-machine/workflow impact
- tests
- risks

After implementation, report:

- implemented scope
- changed files
- requirement coverage
- tests added
- test results
- known limitations
- follow-up tasks



# 项目执行效率规则

本文件适用于当前仓库及其全部子目录。

## 命令与工具选择

- 文本和文件检索优先使用 `rg`、`rg --files`，不要用 PowerShell 递归遍历或反复读取完整大文件。
- Git 状态、差异和格式检查直接使用 `git status --short`、`git diff`、`git diff --check` 等原生命令。
- PowerShell只用于Windows专属操作或短小、边界清晰的命令；长文本解析、复杂校验或多层转义效率较低时，优先使用仓库已有脚本、原生CLI，或适合结构化处理的Node.js/Python工具。
- 对大型Markdown文档先用 `rg -n` 定位目标章节，再读取必要范围；不要为了修改一个局部章节反复加载全文。
- 同一校验需要重复执行时，优先复用已有脚本；确需新增脚本时，应形成简短、可复用、可独立运行的校验脚本，避免每轮重新拼接超长命令。

## PowerShell防错规则

- 避免超长单行PowerShell、嵌套Shell字符串和不必要的管道层级。
- 双引号字符串中变量后紧跟冒号时必须使用 `${name}:`，禁止使用可能被解析为作用域变量的 `$name:`。
- 明确区分命令失败与正常无匹配，例如 `rg` 返回码1通常表示未找到匹配，不应直接当作执行异常。
- PowerShell命令失败后先记录并归纳根因，再更换为已验证写法或更合适的工具；不得重复执行同类易错命令。
- 集中完成一轮“定点检索、修改、必要校验”，避免对同一文件进行多轮等价扫描。

