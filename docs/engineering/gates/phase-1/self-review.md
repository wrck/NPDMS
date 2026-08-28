# SDS Phase 1 V1.8 工程化自审

> 日期：2026-08-20<br>
> 状态：`MACHINE_PASS_AFTER_REPAIR`<br>
> 阶段结论：`APPROVED / READY_FOR_PHASE_2_V1.8`<br>
> 已评审候选：`4792f11`（`GO`）<br>
> 核心修复：`537ab5a`（`VERIFIED`）<br>
> 范围：PRD V1.8的100项V1/V2正式需求及Phase 1正式分册

## 1. 修复自审

| 复审发现 | 处置 | 自审状态 |
|---|---|---|
| PM-10/CLO-02错列V2、INT-04错列V1 | 修正02e版本范围并增加版本列负向校验 | CLOSED_IN_`5a4698f` |
| ConfigurationLog Owner缺失 | AST/EQP-02拥有原始文件、不可变解析版本和设备关联；IMP发布业务结果；补跨Context契约和EQP-02追溯 | CLOSED_IN_`5a4698f` |
| 巡检状态与守卫缺失 | 恢复九状态、在线预检及INS-05～07顺序 | CLOSED_IN_`5a4698f` |
| ServiceHandoverCreated双Producer | 仅ACC发布，Service Operations消费只读引用 | CLOSED_IN_`5a4698f` |
| PM-10命令权限无落点 | 明确服务经理回退与工程管理部关闭岗关闭/重开边界 | CLOSED_IN_`5a4698f` |
| 机器门禁语义覆盖不足 | 新增版本、Owner、状态、事件、授权和文档治理负向测试 | PARTIALLY_CLOSED_IN_`5a4698f` |
| 正式架构混入运行证据 | 移除提交、批次、构建和实现放行描述，只保留稳定架构假设 | CLOSED_IN_`5a4698f` |
| 独立复审未绑定提交 | 登记`dc3ed2a`为首轮NO-GO；后续评审均绑定固定提交 | CLOSED |
| SRV-01仍拥有ServiceHandover | 改为只读ServiceHandoverReference；ACC-06保持交接事实唯一Owner | CLOSED_IN_`9b56dae` |
| 02d事件缺少Requirement追溯 | 每个契约显式登记Requirement ID，并由生成器为全部相关需求链接02d | CLOSED_IN_`9b56dae` |
| PM-10重开副作用缺失 | 补齐原因、可恢复阶段、新责任事项和外部终止任务保护 | CLOSED_IN_`9b56dae` |
| 六类矛盾设计可绕过第一版结构化门禁 | 统一规范化Markdown单元格和表形；增加正文冲突声明扫描；审查者原始六个变体均转绿 | CLOSED_IN_`b65a39e` |
| 追溯生成器缺少只读检查 | 增加`--check`，内存重建、保留Feature列并在漂移时非零退出且不改文件 | CLOSED_IN_`6c8a7fa` |
| 干净CRLF检出导致P3-E09三项测试失败 | 禁用哈希绑定DDL的换行转换并把既有`5EB974…4249`物理字节写入Git对象 | CLOSED_IN_`0fac3ab` |
| HTML实体、跨行或无关否定词可隐藏业务冲突，合法查询/否定放行语句存在误报 | 解码实体并去除格式干扰；按巡检、PM-10、运行证据和Gate的局部业务动作识别；增加攻击与合法反例 | CLOSED_IN_`fb38703` |
| DDL的`binary`属性隐藏SQL文本差异 | 改为`-text diff`，物理字节继续冻结、文本diff可见、merge保持未指定 | CLOSED_IN_`4f72fea` |
| GFM无前导`|`的数据行可隐藏第二Producer/Owner | 按表头和分隔行识别连续GFM表格块；块内支持可选首尾`|`，块外含`|`正文不参与解析 | CLOSED_IN_`fcf3ba9` |
| 代码块或HTML注释中的伪表被误判为正式契约 | 表格识别前遮蔽HTML注释，跳过围栏代码和缩进代码；真实GFM解析保持不变 | CLOSED_IN_`c5659e2` |
| 手写Markdown状态机仍有围栏info、代码内注释和列表相对缩进缺陷 | 用`markdown-it-py 4.2.0`提取真实GFM table token；增加验证依赖清单和六组完整边界回归 | CLOSED_IN_`aece769` |
| 引用式链接可隐藏第二Producer或Owner | 从Markdown内联子token提取渲染后的可见文本；完整、折叠和快捷引用链接均执行唯一性校验 | CLOSED_IN_`6d312d6` |
| HTML属性或注释内的`>`可使不可见token残片进入业务键 | 在内联token层忽略`html_inline`；不再依赖正则删除HTML源码，Producer与Owner两侧均补反例 | CLOSED_IN_`537ab5a` |
| 第九轮独立复审 | 固定候选`4792f11`完成fresh-context反证；核心修复`537ab5a`验证通过，无Critical、Required或Optional | GO |

## 2. 可复现校验

```text
py -3 -B scripts/validate_sds_phase1.py --root .
py -3 -B -m unittest scripts.tests.test_validate_sds_phase1 -v
py -3 -B scripts/generate_requirement_traceability.py --prd docs/baseline/prd-v1.8.md --domains specs/001-project-delivery-platform/domains --output docs/traceability/requirement-matrix.md --check
py -3 -B -m unittest discover -s scripts/tests
py -3 -B scripts/validate_prd_baseline.py --prd docs/baseline/prd-v1.8.md --report docs/reports/2026-08-19-PRD-V1.8基线变更报告.md --expected-version V1.8 --expected-status 正式基线
git diff --check
```

- Phase 1定点测试：61/61通过。
- 追溯生成器定点测试：3/3通过；正式矩阵`--check`通过。
- `core.autocrlf=true`干净检出：DDL SHA-256仍为`5EB974…4249`，此前3个P3-E09错误关闭。
- 脚本全量单元测试：312/312通过。
- 正式需求Owner映射：100/100项由13 个 Owner唯一承接。
- PRD正式基线：67/67通过；语义问题0项。
- 13领域生成：正式100项、编号V3 31项、OUT_OF_SCOPE 9项。
- Phase 2/3、核心迁移契约和81个领域实体迁移契约交叉校验均通过。

## 3. 后置边界

- Phase 2物理模型和实现契约不属于本轮Phase 1修复范围。
- P3-E09只在物理模型变化后重验证；Q08仍为候选索引。
- `AI-MIG-000`只在Release包含历史迁移或数据切换时适用，并只允许在批准窗口内执行。

## 4. 当前结论

`MACHINE_PASS_AFTER_REPAIR / APPROVED / READY_FOR_PHASE_2_V1.8`
