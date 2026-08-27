# Task 2 Step 1：P3-E09 V1.7 差量清单

> 清单边界：HEAD `a4aa33f`；当前核心迁移 DDL 61 表、1,250 列，SHA-256 `4305DA939E094423CE91323AE0C24919D2A31F2DD660A316788684D8A58461B1`；V1.7 差量 11 表、185 字段。Task 1 独立复审结论为 `GO`。本清单仅用于需求方逐项确认，不构成 P3-E09 批准，不写 `approvedDdlSha256`，P3-E09 继续 `BLOCKED_BY_REVIEW`。

## 1. 11 表逐表摘要

### 1.1 `imp_configuration_collection_result`（18 字段）

- 业务目的：承载 EXE-03 每次在线采集或手工上传形成的不可覆盖整机配置 Log 结果根，并保存采集时项目、设备及脚本/解析器上下文。
- 字段组：身份与租户（`id/tenant_id`）；任务和业务上下文（`collection_task_id/project_id/device_id/project_snapshot/device_snapshot`）；结果版本（`result_type_code/result_version_no/source_code`）；原始证据（`raw_log_file_id/raw_log_sha256`）；执行上下文（`script_version/parser_version/operator_user_id/operated_time`）；创建留痕（`creator/create_time`）。
- 唯一性：`tenant_id+collection_task_id+result_type_code+result_version_no`唯一；结果版本必须大于0；同租户行ID唯一。
- 时态/不可变：每次采集形成新`result_version_no`；原始文件引用和哈希不可原位覆盖；项目/设备快照冻结采集时点事实。
- 来源策略：当前表`pms_eng_configuration|pms_equipment_config_log`仅作前向字段映射依据；在线结果来自 INT-12 回调，手工上传直接形成新结果；凭证明文和临时密码禁止迁移或保存。
- 索引候选及依据：`tenant+project+device+operated_time`支撑项目/序列号/时间查询，`tenant+raw_log_sha256`支撑文件去重；均为 Q08 候选，须由 Feature 查询计划和 P3-E06 验证，不代表性能已通过。

### 1.2 `imp_configuration_collection_parse_attempt`（12 字段）

- 业务目的：追加保存同一配置 Log 的每次解析尝试、解析器版本、时间、状态和失败证据，允许规则修复后重新解析而不覆盖旧结果。
- 字段组：身份（`id/tenant_id`）；结果关系与序号（`collection_result_id/attempt_no`）；解析事实（`parser_version/parse_status_code/started_time/completed_time/error_summary/evidence_ref`）；创建留痕（`creator/create_time`）。
- 唯一性：`tenant_id+collection_result_id+attempt_no`唯一；尝试序号大于0；完成时间不得早于开始时间。
- 时态/不可变：解析尝试只追加；旧解析状态、错误摘要和证据不回写覆盖。
- 来源策略：由新平台解析过程产生；历史数据仅在能够证明具体结果与解析版本时映射，否则保留原值并形成迁移问题。
- 索引候选及依据：`tenant+collection_result_id+started_time`支撑单个 Log 的解析时间线；下游按实际查询计划复核。

### 1.3 `imp_configuration_component_candidate`（16 字段）

- 业务目的：保存框式设备从整机 Log 解析出的机框—槽位—板卡候选和匹配状态，避免未确认候选直接改写 AST 当前关系。
- 字段组：身份（`id/tenant_id`）；解析归属（`parse_attempt_id/parse_revision_no/candidate_no/parser_version`）；组件识别（`chassis_sn/slot_code/card_sn/card_model_code/card_configuration_ref`）；匹配（`match_status_code/matched_device_id`）；证据留痕（`evidence_ref/creator/create_time`）。
- 唯一性：`tenant_id+parse_attempt_id+candidate_no`唯一；候选序号大于0。
- 时态/不可变：每次解析修正形成新的尝试/候选；不得修改原始 Log；人工确认只改变后续关系形成结果，不覆写历史候选证据。
- 来源策略：由 ConfigurationCollectionResult 的解析尝试派生；无法确认板卡身份时保持待匹配，只有自动或人工核验通过后才可生成 AST 时态关系。
- 索引候选及依据：`tenant+match_status_code+create_time`支撑待匹配队列，`tenant+chassis_sn+slot_code+card_sn`支撑组件定位和人工核对。

### 1.4 `acc_satisfaction_collection_task`（27 字段）

- 业务目的：承载 ACC-02 在初验、终验、转包付款或模板时点生成的满意度领域任务，冻结来源业务对象、业务用途、模板、阈值、交付范围与责任人。
- 字段组：身份（`id/tenant_id`）；项目和来源（`project_id/source_context/source_object_type/source_object_id/source_object_version`）；时点与用途（`business_purpose_code/applicable_timing_code/payment_stage_code/payment_stage_key`）；冻结内容（`template_id/template_version/frozen_threshold/delivery_scope_snapshot/delivery_scope_sha256`）；任务版本和整改链（`task_revision_no/prior_task_id/remediation_ref`）；状态责任（`status_code/state_machine_version/current_responsible_user_id/version`）；审计（`creator/create_time/updater/update_time`）。
- 唯一性：项目、来源对象及版本、用途、适用时点、付款阶段生成键和任务 revision 组成唯一键；revision 大于0。
- 时态/不可变：模板、阈值、业务范围和来源版本在任务创建时冻结；整改重收创建新任务 revision，旧任务不可覆盖。
- 来源策略：历史问卷/回访/转包记录只有在项目、来源业务对象和版本可证明时建立关系；不得从回访或审批状态推断客户答案、签字或通过结果。
- 索引候选及依据：`tenant+current_responsible_user_id+status_code`支撑责任人待办，`tenant+source_context+source_object_type+source_object_id`支撑来源门禁追溯。

### 1.5 `acc_satisfaction_questionnaire`（16 字段）

- 业务目的：保存任务下冻结的问卷实例、题目/必答/分值规则、阈值、规则版本及整改前序版本。
- 字段组：身份与任务（`id/tenant_id/task_id`）；模板来源（`template_id/template_version/source_questionnaire_key/source_questionnaire_version`）；冻结规则（`frozen_question_json/frozen_threshold/required_question_count/rule_version`）；版本链（`questionnaire_revision_no/prior_questionnaire_id/remediation_ref`）；创建留痕（`creator/create_time`）。
- 唯一性：`tenant_id+task_id+questionnaire_revision_no`唯一，revision 大于0。
- 时态/不可变：问卷发布/提交后题目、分值、阈值和规则版本不可覆盖；整改生成新实例和 revision。
- 来源策略：旧问卷模板头、题目、选项按顺序组装不可变快照；旧库缺少可证明的必答语义时不得猜测，进入迁移问题。
- 索引候选及依据：`tenant+task_id+create_time`支撑任务内问卷版本链查询。

### 1.6 `acc_satisfaction_response`（15 字段）

- 业务目的：不可覆盖保存客户逐次提交的答案、附件、签字及必答/逐项有效性校验事实。
- 字段组：身份（`id/tenant_id`）；问卷与幂等（`questionnaire_id/response_no/request_id`）；提交事实（`answer_json/attachment_refs_json/signature_ref/submit_time`）；有效性（`response_valid/signature_valid/required_validation_summary/item_validation_summary`）；创建留痕（`creator/create_time`）。
- 唯一性：`tenant+questionnaire+request_id`防重复提交；`tenant+questionnaire+response_no`保证提交序列唯一；序号大于0。
- 时态/不可变：答案、签字、附件和校验摘要为业务事实，只追加不删除、不覆盖；重复请求返回首次有效结果。
- 来源策略：从可证明的旧问卷结果行组装答案；签字与有效性无证据时不得由分数或流程状态推断。
- 索引候选及依据：`tenant+questionnaire_id+submit_time`支撑提交时间线和重复回传核对。

### 1.7 `acc_satisfaction_result`（21 字段）

- 业务目的：保存每次答卷的评分、签字/必答校验、达标判定、阻断原因及 ACC-04 归档事实。
- 字段组：身份与版本（`id/tenant_id/questionnaire_id/response_id/result_no`）；判定（`score/frozen_threshold/passed/response_valid/signature_valid/required_items_valid/validation_summary/blocking_reason/decision_rule_version/decision_time`）；归档（`archive_status_code/archive_artifact_id/archive_payload_sha256/archive_time`）；创建留痕（`creator/create_time`）。
- 唯一性：一个 response 仅一个结果；问卷内 result_no 唯一且大于0；`passed`限定0/1。
- 时态/不可变：评分与通过判定不可人工覆盖；整改重收形成新任务、问卷、答卷和结果；归档引用保存对应事实版本。
- 来源策略：旧分数精确转换并保留原值；通过状态仅按批准值映射，未知值进入迁移问题，不从回访/审批状态推导。
- 索引候选及依据：`tenant+passed+decision_time`支撑闭环/付款门禁和未达标治理查询。

### 1.8 `cut_cutover_support_task`（21 字段）

- 业务目的：承载 CUT-11 独立割接保障任务根，冻结状态机版本、割接/项目/设备范围、保障窗口及当前责任上下文。
- 字段组：身份（`id/tenant_id/task_no`）；关联和范围（`cutover_task_id/project_id/device_scope_snapshot/support_scope_hash/window_start/window_end`）；状态责任（`status_code/state_machine_version/current_responsible_user_id/current_handler_user_id/current_responsibility_interval_id/version`）；来源兼容（`source_system/source_business_key`，当前策略为新平台命令创建，不据此接入已排除来源）；审计（`creator/create_time/updater/update_time`）。
- 唯一性：任务编号租户内唯一；当前 DDL 以`cutover_task_id+support_scope_hash+window_start+window_end`防重；保障窗口结束不得早于开始。
- 时态/不可变：创建时冻结状态机版本和设备范围；关闭后只读；责任变更通过责任区间与操作历史追加表达，不直接覆盖历史。
- 来源策略：`NONE_NEW/NEW_ONLY`，只由当前平台受控命令创建；`pm_project_maintenance`、历史工单/工时及目录快照均不得映射到本表。
- 索引候选及依据：`tenant+current_responsible_user_id+status_code`支撑责任任务查询，`tenant+window_start+window_end`支撑保障窗口查询。防重口径中的“同一职责”仍见 B-01。

### 1.9 `cut_cutover_support_history`（12 字段）

- 业务目的：追加保存派单、处理、接管、转单、挂起、恢复、关闭等每次状态/责任操作的前后状态、原因、证据和操作者。
- 字段组：身份（`id/tenant_id/support_task_id/history_no`）；操作事实（`action_code/status_before_code/status_after_code/operator_user_id/occurred_time/reason/evidence_ref`）；创建时间（`create_time`）。
- 唯一性：`tenant+support_task_id+history_no`唯一，历史序号大于0。
- 时态/不可变：严格追加写；状态前后值、操作者、发生时间、原因和证据不可覆盖。
- 来源策略：由 CUT-11 受控状态机命令产生；无已确认历史来源，不从维护记录或通用工单推断。
- 索引候选及依据：`tenant+support_task_id+occurred_time`支撑完整任务时间线和审计回放。

### 1.10 `cut_cutover_support_responsibility_interval`（12 字段）

- 业务目的：保存派单、接管或转单形成的责任区间；挂起/恢复不切断区间。
- 字段组：身份（`id/tenant_id/support_task_id/interval_no`）；责任与时间（`responsible_user_id/effective_from/effective_to/current_support_task_id`）；交接证据（`handover_reason/evidence_ref`）；创建留痕（`creator/create_time`）。
- 唯一性：任务内 interval_no 唯一；生成列`current_support_task_id`使一个任务最多一条`effective_to IS NULL`当前区间；结束时间不得早于开始时间。
- 时态/不可变：接管/转单在同一事务结束旧区间并追加新区间；应用层锁定任务和当前区间，阻止区间重叠及并发双成功；历史区间不可修改。
- 来源策略：只由新平台完整的派单/接管/转单证据重建；挂起不结束区间；无完整证据不得推导。
- 索引候选及依据：`tenant+support_task_id+effective_to+effective_from`支撑当前区间定位和历史时间线；物理唯一仅约束“最多一条当前区间”，不替代应用层全区间不重叠守卫。

### 1.11 `ast_device_component_relation`（15 字段）

- 业务目的：保存机框—槽位—板卡时态关系、来源和证据，支撑框式产品配置追溯及换板历史。
- 字段组：身份（`id/tenant_id`）；机框/槽位（`chassis_device_id/chassis_sn/slot_code/current_slot_code`）；板卡（`card_device_id/card_sn/card_model_code`）；时态（`effective_from/effective_to`）；来源证据（`relation_source_code/evidence_ref`）；创建留痕（`creator/create_time`）。
- 唯一性：生成列`current_slot_code`与`tenant+chassis_device_id`保证同一机框槽位同一时点最多一张当前板卡；结束时间不得早于开始时间。
- 时态/不可变：换板必须结束旧关系并创建新关系；自动匹配与人工绑定均保留来源、解析版本关联证据和时间，不覆盖历史。
- 来源策略：由已核验 ConfigurationCollectionResult 候选或设备档案事实派生；身份歧义时保持待匹配，不建立正式关系。
- 索引候选及依据：`tenant+card_sn+effective_to`支撑板卡当前位置/历史查询，`tenant+chassis_sn+effective_from`支撑机框组件时间线。

> C 类汇总：其余主键、租户行唯一键、同域外键、非空、字符集/排序规则、通用审计列、CHECK 及 124 项 Q08 普通索引均按 ADR-0019～ADR-0023、ADR-0025 和 Q07/Q08 技术契约逐项登记；本报告不抄录 1,900 项物理寄存器，索引仍须 Feature 查询计划和 P3-E06 验证。

## 2. A 类：PRD/用户已锁定但当前 DDL 存在缺口

### A-01 EQP-02 配置 Log 受控软删除事实缺失

- 依据：EQP-02 已明确配置 Log 采用受控软删除，并保存删除人、删除时间、删除说明和启用/已删除状态；被版本历史、割接或巡检证据引用时普通工程师不得删除，管理员受控删除后仍保留引用元数据及文件哈希。
- 当前缺口：`imp_configuration_collection_result`没有`status_code/deleted/deleted_by/deleted_time/delete_reason`（或语义等价列），也没有“受引用后仅管理员受控删除”的持久化结果；该项是确定缺口，不再作为待确认问题。
- 建议裁决：在配置 Log 结果根补齐受控软删除状态、删除人、删除时间、删除说明；原始文件哈希、版本、设备/项目快照和引用元数据保持不可变。删除权限与引用校验由应用服务执行并写审计。

### A-02 EXE-03 每次采集记录的认证方式与非秘密连接元数据缺失

- 依据：EXE-03 明确每次操作保存采集方式、设备连接端点、认证方式、凭证ID及版本/授权快照或临时登录用户名、统一采集任务编号和脚本版本；临时密码不得保存。
- 当前缺口：结果根已有`source_code/collection_task_id/script_version`，但没有`connection_endpoint/auth_mode_code/credential_id/credential_version/credential_grant_snapshot/temporary_login_username`或语义等价引用。若这些事实只存在上游任务表，当前 61 表核心 DDL又没有可核验的采集任务物理表与不可变引用契约。
- 建议裁决：优先由统一采集任务 Owner 表持有上述字段，并由结果根保存不可变`collection_task_id`；若该 Owner 表不属于本核心子集，P3-E09必须登记其前向迁移依赖和字段契约，不能以“以后实现”消解当前字段责任。任何结构均不得含临时密码或凭证明文。

### A-03 EQP-02 配置版本、人工补录与设备核对状态缺失

- 依据：EQP-02 明确保存配置版本、解析状态/字段/错误、人工版本值及来源、前序版本、设备序列号核对状态；解析失败允许人工补录但不覆盖解析值，序列号冲突进入待核对。
- 当前缺口：结果根仅有结果版本、解析器版本、原始文件引用/哈希；解析尝试只有解析状态和错误摘要。没有`parsed_config_version/manual_config_version/version_source_code/prior_result_id/device_binding_status_code`或等价结构，无法同时保留自动值、人工值、差异与待核对事实。
- 建议裁决：在结果根或独立不可变版本事实表补齐自动版本、人工版本、来源、前序结果和设备核对状态；人工修正必须追加版本/留痕，不得覆盖原始解析结果。

### A-04 ACC-02 任务指派人与客户填写入口身份缺失

- 依据：ACC-02 明确保存指派人、二维码/外发链接标识、客户联系人，并要求一次性链接限定问卷实例、记录链接生命周期且不记录完整令牌。
- 当前缺口：`acc_satisfaction_collection_task`只有当前责任人，没有指派人/指派时间；问卷与答卷没有客户联系人快照、外发入口ID/链接生命周期引用，也没有可核验的独立表承载这些业务事实。
- 建议裁决：任务补齐`assigned_by/assigned_time`；链接令牌由基础平台安全能力保存摘要和生命周期，问卷或答卷保存不可变入口引用及客户联系人快照，禁止保存完整访问令牌。

### A-05 ACC-02 导出审计落点未形成可追溯契约

- 依据：ACC-02 明确有权限用户可导出，必须保存导出条件、范围摘要、文件哈希、下载人和时间；这是用户已确认的统一导出留痕规则。
- 当前缺口：4张满意度表没有导出审计字段；核心 DDL 中也没有能回指满意度业务对象、条件和权限范围的明确导出审计落点。
- 建议裁决：不把导出日志塞入不可变答卷/结果表；由基础平台通用导出审计表承载`business_context/object_scope/query_condition_summary/data_scope_summary/file_hash/exporter/export_time/result`，并建立 ACC-02 对该契约的强制引用和验收测试。

### A-06 CUT-11 挂起/恢复业务事实缺失

- 依据：CUT-11 明确挂起原因、预计恢复时间、外部责任方、挂起人/时间、实际恢复时间、恢复操作人；挂起不结束责任区间且不改变当前责任人/处理人。
- 当前缺口：任务根无上述字段；历史表通用`reason/evidence_ref`只能保存原因，不能结构化表达预计恢复、外部责任方、实际恢复和提醒查询口径。
- 建议裁决：任务根保存当前挂起事实（`suspend_reason/expected_resume_time/external_responsible_party/suspended_by/suspended_time`），恢复时追加历史并保存`resumed_by/resumed_time`；任务当前字段可随合法状态迁移更新，但历史表完整保留前后值。增加`tenant+status_code+expected_resume_time`候选索引用于到期提醒。

### A-07 CUT-11 派单、开始处理与关闭事实缺失

- 依据：CUT-11 字段清单明确派单时间、处理开始时间、关闭人、关闭时间；关闭前必须保存处理结果、必填步骤/证据和关联待办完成情况，关闭后只读。
- 当前缺口：任务根没有`assigned_time/processing_started_time/processing_result/closed_by/closed_time/closure_evidence_ref`或语义等价字段；历史表通用动作记录不能单独承担关闭后高频、稳定的当前终态事实与门禁引用。
- 建议裁决：任务根补齐上述当前事实并由受控状态机原子写入，历史表继续追加完整动作；关闭条件快照/证据引用不可覆盖，关闭后禁止普通更新接口。

### A-08 CUT-11 割接关联异常与待同步状态缺失

- 依据：CUT-11 明确 CUT 任务不可用或关联事件失败时保障任务保持当前状态并标记“割接状态待同步”，通知失败进入重试且不得回滚合法流转。
- 当前缺口：任务根没有关联同步状态、关联 CUT 版本、水位或最后失败信息；历史表也不能支持稳定的待同步查询和补偿幂等。
- 建议裁决：补齐`cutover_task_version/cutover_sync_status_code/last_sync_error/last_sync_time`或建立统一集成补偿记录并由任务根保存当前同步状态；通知重试与业务状态分离。

## 3. B 类：必须由需求方确认的业务语义问题

### B-01 CUT-11“同一职责和时间窗不得重复创建”中的职责如何持久化？

- 当前事实：PRD 已锁定同一割接任务可有多张分工任务，但“同一职责和时间窗”不得重复。当前 DDL 没有独立职责字段，使用`support_scope_hash+window_start+window_end`参与唯一键；`support_scope_hash`描述设备/保障范围，不能证明等同于业务职责。
- 需要确认：职责是可配置的稳定分类（如现场保障、配置核查、观察保障等），还是由设备范围/任务描述整体计算的不可读摘要？这会直接改变字段、唯一键、迁移规则、查询和分工模型。
- 推荐方案：**A：新增可配置字典字段`responsibility_type_code`，防重键采用`tenant_id+cutover_task_id+responsibility_type_code+support_scope_hash+window_start+window_end`。** 职责编码表达业务分工，范围哈希表达同一职责下的设备/范围；字典只扩展分类，不允许绕过责任与关闭门禁。
- 备选方案：B：仅以`support_scope_hash`代表职责与范围，不新增职责字段。结构较少，但无法按职责查询、解释重复冲突或稳定支持不同职责作用于相同设备范围。
- 影响：选择A将新增字段、字典、唯一键和创建接口参数，并要求任务展示职责；选择B维持当前DDL，但必须正式确认“职责=完整保障范围摘要”的业务定义，否则现唯一键不能证明满足PRD。

