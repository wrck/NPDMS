# SDS Phase 1 V1.8 工程化自审

> 日期：2026-08-20<br>
> 状态：`MACHINE_PASS_AFTER_REPAIR`<br>
> 阶段结论：`RE_REVIEW_REQUIRED / NOT_READY_FOR_PHASE_2_V1.8`<br>
> 已评审候选：`5a4698f`（`NO_GO`）<br>
> 修复候选：`PENDING`<br>
> 范围：PRD V1.8的100项V1/V2正式需求及Phase 1正式分册

## 1. 修复自审

| 复审发现 | 处置 | 自审状态 |
|---|---|---|
| PM-10/CLO-02错列V2、INT-04错列V1 | 修正02e版本范围并增加版本列负向校验 | CLOSED_IN_WORKTREE |
| ConfigurationLog Owner缺失 | AST/EQP-02拥有原始文件、不可变解析版本和设备关联；IMP发布业务结果；补跨Context契约和EQP-02追溯 | CLOSED_IN_WORKTREE |
| 巡检状态与守卫缺失 | 恢复九状态、在线预检及INS-05～07顺序 | CLOSED_IN_WORKTREE |
| ServiceHandoverCreated双Producer | 仅ACC发布，Service Operations消费只读引用 | CLOSED_IN_WORKTREE |
| PM-10命令权限无落点 | 明确服务经理回退与工程管理部关闭岗关闭/重开边界 | CLOSED_IN_WORKTREE |
| 机器门禁语义覆盖不足 | 新增版本、Owner、状态、事件、授权和文档治理负向测试 | CLOSED_IN_WORKTREE |
| 正式架构混入运行证据 | 移除提交、批次、构建和实现放行描述，只保留稳定架构假设 | CLOSED_IN_WORKTREE |
| 独立复审未绑定提交 | 登记`dc3ed2a`为首轮NO-GO；后续评审均绑定固定提交 | CLOSED |
| SRV-01仍拥有ServiceHandover | 改为只读ServiceHandoverReference；ACC-06保持交接事实唯一Owner | CLOSED_IN_WORKTREE |
| 02d事件缺少Requirement追溯 | 每个契约显式登记Requirement ID，并由生成器为全部相关需求链接02d | CLOSED_IN_WORKTREE |
| PM-10重开副作用缺失 | 补齐原因、可恢复阶段、新责任事项和外部终止任务保护 | CLOSED_IN_WORKTREE |
| 六类矛盾设计可绕过字符串门禁 | 改为表格行、唯一性、精确角色、状态守卫和门禁元数据校验；六个对抗用例均转绿 | CLOSED_IN_WORKTREE |
| 第二轮独立复审 | 登记`5a4698f`为NO-GO；本轮修复候选仍为PENDING | RE_REVIEW_REQUIRED |

## 2. 可复现校验

```text
py -3 -B scripts/validate_sds_phase1.py --root .
py -3 -B -m unittest scripts.tests.test_validate_sds_phase1 -v
py -3 -B -m unittest discover -s scripts/tests
py -3 -B scripts/validate_prd_baseline.py --prd docs/baseline/prd-v1.8.md --report docs/reports/2026-08-19-PRD-V1.8基线变更报告.md --expected-version V1.8 --expected-status 正式基线
git diff --check
```

- Phase 1定点测试：24/24通过。
- 脚本全量单元测试：270/270通过。
- 正式需求Owner映射：100/100项由13 个 Owner唯一承接。
- PRD正式基线：67/67通过；语义问题0项。
- 13领域生成：正式100项、编号V3 31项、OUT_OF_SCOPE 9项。
- Phase 2/3、核心迁移契约和81个领域实体迁移契约交叉校验均通过。

## 3. 未关闭项

- 修复候选尚未固定提交，`5a4698f`的NO-GO结论保持有效。
- 新候选必须执行fresh-context重新复审；本自审不得将自身升级为GO。
- Phase 2物理模型和实现契约不属于本轮Phase 1修复范围。

## 4. 当前结论

`MACHINE_PASS_AFTER_REPAIR / RE_REVIEW_REQUIRED / NOT_READY_FOR_PHASE_2_V1.8`
