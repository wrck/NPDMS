# F-IMP-002 旧到货签收实现复用审计

> Requirement：`EXE-01（V1/P0）`
> 审计结论：`CURRENT_FORWARD / COPY_THEN_ENHANCE / PRESERVE_LEGACY`
> Feature Spec：`specs/features/F-IMP-002-arrival-acceptance.md`

## 1. 审计范围

- 后端：`pms-module-engineering` arrival Controller、Service、Mapper、DO/VO及状态命令。
- 前端：`views/pms/engineering/arrival`、对应API、项目详情内旧arrival入口和上传组件。
- 数据/配置：`V10__pms_engineering_tables.sql`、V11/V16菜单权限、V19/V20/V35样例数据、领域迁移契约。
- 状态/权限：旧tinyint状态、通用CRUD、项目/设备范围、服务端主体守卫和附件引用。
- 测试：工程模块现有后端/前端测试目录及arrival专属覆盖。

## 2. 逐项判断

| 存量对象 | 当前事实 | 判定 | F-IMP-002处理 |
|---|---|---|---|
| `pms_eng_arrival` | 一行保存项目、编码、时间、签收人、单个旧设备、数量、说明、URL和0/1/2状态 | 身份/时间/说明可候选前向迁移；无完整业务闭环 | 按第3节逐字段迁入三张Owner表；旧表不改写、不双写 |
| 旧Controller/Service/VO | 通用创建、更新、删除；客户端可提交广泛字段；创建默认状态0 | 技术骨架可参考，业务接口不可复用 | 复制通用返回/校验风格后新建聚合命令；不暴露通用删除和状态写入 |
| 旧状态命令 | 只允许0转1或2 | 不表达多批、部分签收、差异闭环、拒收、补签、豁免或项目最终事实 | 新状态机按PRD/SDS实现；旧状态仅作迁移来源标记 |
| 旧页面/API | 可增删改、选择旧设备、录数量、富文本检查/异常、上传原始附件URL、执行签收/异常 | 不可原页增强 | 新建F-IMP-002页面/API；旧页面、路由和行为保持不变 |
| `attachment_url` | 单字符串URL，无FileArtifact/FileVersion/不可变revision语义 | 不可直接作为权威证据 | 仅可在文件真实存在、可读取且成功建立FileReference后迁移；否则待核对 |
| 旧设备引用 | `equipment_id`指向旧设备模型，可空 | 不能直接充当AST稳定deviceId | 先按批准的旧设备→AST映射校验租户、SN、当前项目和版本；失败不推导签收 |
| 菜单/权限 | `pms:eng-arrival:query/create/update/delete`按钮权限，无项目范围和主体最终确认守卫 | 不可直接作为新授权 | 新权限叠加ProjectScope与项目经理/本人草稿守卫；按钮只消费allowedActions |
| V19/V20/V35样例 | 为演示页面预置状态与附件 | 不是生产证据 | 不用于证明映射、应到范围、签收完成或浏览器正向闭环 |
| 专属测试 | 未发现arrival专属后端或前端业务测试 | 无可复用业务验收 | 新增聚合、权限、迁移、并发、真实MySQL和浏览器测试；保留其他旧回归 |

## 3. 字段、状态与完整性映射

| 旧字段/状态 | 目标候选 | 映射条件 | 禁止推断/不可迁处置 |
|---|---|---|---|
| `id` | `legacy_source_id`及迁移审计引用 | 租户内旧行唯一且未被重复迁移 | 不把旧ID冒充新事实版本 |
| `project_id` | 根`project_id` | 项目引用存在且租户一致 | 无效/跨租户行保持旧记录并`PENDING_RECONCILIATION` |
| `code` | `batch_code` | 项目内唯一、非空 | 冲突不得自动加后缀改变业务身份 |
| `arrival_time` | `arrived_at` | 合法时间 | 不用创建/更新时间补造到货时间 |
| `receiver_user_id` | `signer_user_id`及发生时快照 | 用户引用存在且当时可解释 | 只保存ID不等于客户签收或项目经理最终确认 |
| `equipment_id` | 明细`device_id` | 经旧设备→AST稳定映射并重验租户/项目 | 失败时不生成设备明细或ACCEPTED |
| `quantity` | 明细来源数量候选 | 正数、单位和订单行/型号范围均可证明 | 单个整数不证明应到/实收口径，不能用于里程碑完成 |
| `inspection_result` | 来源说明快照 | 原文保留并标记legacy来源 | 不推导差异已关闭、质量通过或签收完成 |
| `exception_record` | 差异说明候选 | 能明确关联设备或订单行/数量 | 不足以推导差异类型、处置、豁免或拒收结论 |
| `attachment_url` | `FileReference`候选 | 文件存在、可读、通过PLT校验并创建不可变revision | 原URL、失效URL或测试路径不成为权威证据 |
| `status=0` | 迁移来源状态`PENDING_RECONCILIATION` | 可迁身份字段仍可保留 | 不等于新DRAFT已满足必填范围/证据 |
| `status=1` | 历史“旧系统已签”声明 | 仅作来源证据；仍须范围、设备/数量、证据和差异完整性重验 | 绝不直接映射项目`ACCEPTED`或开放EXE-02 |
| `status=2` | 差异候选来源 | 可解析明确范围时生成待处理差异候选 | 不推导拒收、处置完成或有效豁免 |
| `version/creator/time/updater/time` | 来源版本与审计 | 原值原样保存 | 不用最后更新时间充当业务确认时间 |
| `deleted` | 迁移排除/历史审计 | 删除行默认不生成新当前事实 | 不恢复软删除数据为有效签收 |

## 4. 复用结论

- 直接复用：Yudao租户、通用返回、校验注解、乐观锁、审计和场景化Query编码方式。
- 复制增强：新聚合Controller/Service/VO和新页面从技术结构起步，随后实现PRD状态、权限、范围和证据语义。
- 不可复用：通用删除、客户端状态输入、旧0/1/2状态机、旧设备直接引用、URL附件、按钮权限即授权、样例数据即完成事实。

结论：F-IMP-002执行CURRENT_FORWARD，但迁移的是可证明的来源事实，不是旧业务结论。任何缺少项目、应到范围、稳定设备/数量、有效证据或差异完整性的旧行均不得生成`ACCEPTED`，只保留旧记录和待核对处置。
