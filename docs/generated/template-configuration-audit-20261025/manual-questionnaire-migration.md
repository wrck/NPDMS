# SAT_MANUAL_EXAMPLE v1 定向补齐与验证

日期：2026-10-25（Asia/Shanghai）。本次依据用户明确授权，仅向测试库补齐实际在用的 `SAT_MANUAL_EXAMPLE v1`，保留已有配置、业务数据和历史；不迁移其他未使用配置。

## 结果

已在 59286 对应的测试环境通过现有模板管理接口完成创建、修订和发布。未直接写生命周期状态、未覆盖历史发布版本、未执行 DDL/Flyway、未重启应用。

| 项目 | 源库 | 测试库 |
|---|---|---|
| 数据库 | `127.0.0.1:24306/npdms_domain_test` | `172.17.90.178:13306/npdms` |
| 模板编码 | `SAT_MANUAL_EXAMPLE` | `SAT_MANUAL_EXAMPLE` |
| 模板 ID | `992209210001` | `2114075378510712833` |
| v1 修订 ID | `992209210002` | `2114075378854645762` |
| 状态 | PUBLISHED | PUBLISHED |

测试端使用正式服务生成新 ID 和发布生效时间，保留源/目标映射；没有复制源审计身份、源发布时间，也没有迁移调查实例或修改项目冻结引用。

## 内容和关联配置

- 题目：必填总体评价评分题、可选改进建议文本题；题目、选项、必填规则和长度限制与源定义一致。
- 评分：`SUM_V1`，满分 100、阈值 80、精度 2、`HALF_UP`，规则版本 `SAT-EXAMPLE-V1`。未改变源规则。
- 适用维度：项目类型/签约模式/实施模式均为 `*`，用途 `ACCEPTANCE`，触发时点 `MANUAL`，优先级 100；保持源值，不扩大为自动触发配置。
- 自包含 schemaVersion=1 问卷，不引用其他表单、字典、业务视图或外部文件；无需迁移额外依赖。
- 问卷内容规范化 SHA256：`74c0b0e2c304ca933c559de25ec57b78c8c1a94cd2e2c2d569e7da43107534d8`。

## 执行与实际验证

使用既有 admin 会话，服务端核实用户 1、`super_admin` 及问卷管理权限。经 `/admin-api/api/v1/pms/satisfaction-questionnaire-templates` 依次创建根、创建 v1、携带 Idempotency-Key 发布。服务端执行正式的定义解析、评分投影一致性、发布维度冲突和版本校验；正常产生发布审计及幂等记录。

以下检查通过：

1. API 回读模板及唯一 v1 修订：两者 PUBLISHED，根 currentRevisionId 正确，题目和适用维度等业务字段与源内容一致。
2. 相同发布请求重放：`replayed=true`，没有新建修订或再次推进版本。
3. 按现有消费者 Mapper 的租户、当前发布指针、有效区间及适用维度谓词，在测试数据库执行只读候选查询：唯一命中新修订，无同优先级歧义。
4. 同谓词使用其他租户、无效触发时点：均返回空，未扩大匹配范围。这里只验证源配置元组（含 `*`）的候选查询；不声明其他具体项目维度自动通配，也未修改匹配语义。
5. 91 张配置及业务/历史保护表前后逐原行摘要比较：原记录修改 0、删除 0；这 91 张表中仅问卷根、问卷修订各新增 1 条。既有默认竣工满意度问卷、调查实例、项目任务/计划/执行契约、材料、文件引用和迁移历史均保持原样。

页面只读复核尝试因新检查页未能连接而未完成；未向现有用户页注入身份或提升权限，原项目页保留。未创建调查、填写客户答案、提交评分、推送通知，也未重新执行完整业务 UI 验收；上述结果证明配置已发布、API 可读和候选谓词可用，不冒称客户调查全流程验收。

## 可复查证据

- `.run/template-full-audit-20261025/migrate-manual-questionnaire.py`：限范围的正式 API 导入与只读验证；重复执行会核对源内容和既有修订，不覆盖不一致数据。
- `.run/template-full-audit-20261025/manual-questionnaire/source.json`：迁移时锁定的源业务配置。
- `manual-questionnaire/created-template.json`、`created-revision.json`、`published.json`、`publish-replay.json`、`readback.json`：创建、发布和幂等回读结果。
- `manual-questionnaire/selection-checks.json`：唯一命中及租户/时点隔离结果。
- `manual-questionnaire/before.json`、`after.json`、`result.json`：91 表原行摘要及新增白名单验证。产物未保存会话 Token 或其他认证凭据。

本记录补充此前使用范围判定中唯一确认的问卷缺项，不更改此前历史审查结果，也不将其他配置差异升级为迁移事项。未提交或推送 Git。
