# 本地源库与测试服务器模板配置只读核查

核查日期：2026-10-25（Asia/Shanghai）。当前工作树 HEAD `c674a8a8793cd061056b5dbe85ba7afd6dd1ac9a`，包含既有未提交改动；本报告不是 master 交付证明。

## 结论

不能确认两库模板配置全面一致，也不能将本轮数据库核查视为所有配置功能的完整业务验收。五套正式项目模板的最新设计内容、六套发布文档模板的版本内容一致；采集、巡检、部分表单/问卷、定义库和部分平台配置存在缺项。历史、示例与当前有效配置必须分开处理，不宜整库覆盖。

## 数据源与方法

- 源库：`127.0.0.1:24306/npdms_domain_test`，从既有 Docker 实例连接配置读取凭据。
- 测试库：`172.17.90.178:13306/npdms`，复用 59286 的既有连接配置。
- 两端分别使用 `START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY`；限定租户 1 和非删除记录，包含历史修订。两端快照不是同一个分布式事务，期间并行操作可能造成时点差异。
- 覆盖源库 72 张、测试库 71 张相关表，包括配置、菜单权限依赖及少量运行关系。原始主键差异仅作定位；项目模板按模板编码解析最新 PUBLISHED 修订，业务视图引用转为自然键，忽略内嵌视图快照后比较 Designer；不将迁移 ID、发布时间差异直接判成业务内容不一致。
- 依据 PRD 配置基础能力和历史冻结约束、PM-03/SOL-01 追溯入口，以及当前源码消费者。只读关系检查覆盖修订父项、动态表单当前发布指针、问卷指针、文档继承与版本指针、定义引用和项目模板业务视图绑定；结果均未发现所检查关系的悬空外键。未覆盖的引用不据此宣称完整无误。
- 未执行创建、保存、发布、停用、应用模板、采集命令、流程启动或业务数据迁移；未重启服务。审计产物保存摘要、差异字段和配置标识，不保存凭据或采集命令正文。

## 逐项结果

| 功能 | 核查结果 | 结论与边界 |
|---|---|---|
| 正式项目模板与匹配条件 | 两库均有 5 套 ACTIVE；最新 Designer 规范化摘要 5/5 一致；测试库最新发布修订均为 v2，视图绑定缺项为 0 | 当前配置一致；未用新项目逐个验证自动匹配、裁剪、审批、完成规则与门禁执行 |
| 阶段/任务/里程碑/交付件/门禁/流转 | Designer 随正式模板一起比较；源库存在独立定义明细，测试库独立明细为 0 | V2 的 `getRevisionContent` 读取完整 execution_snapshot 转运行内容，不能仅以旧明细表为空判运行故障；独立定义库维护能力仍有数据差异 |
| 历史项目模板 | 源 38 套（26 RETIRED、7 DRAFT、5 ACTIVE），测试 6 套（1 DRAFT、5 ACTIVE） | 缺少历史/草稿不是正式 5 套丢失。不得复制覆盖现有测试修订 |
| 项目活动计划冻结 | 源 287、测试 3 个活动计划；源无本项缺项；测试 84572 的 PROJECT_ASSIGNMENT 缺视图快照，该任务当前 DONE | 历史遗留；本轮不覆盖已完成记录，也不将其等同于最新模板缺陷 |
| 动态表单及打印模板 | 源 17、测试 12 套；共有 12 套的当前发布表单内容摘要与 availability 均相同 | 需求分析 v10/v9、培训与培训打印 v2/v1 的修订号不同，但内容相同。未实测 PDF/Word 输出和附件上传 |
| 缺少动态表单 | `5`、`CONFIRM_QA_1789910001`、`SOL_PRE04_DISABLED_COMPATIBLE`、`SOL_PRE04_DUPLICATE_CORE_EXAMPLE`、`SOL_PRE04_MISSING_CORE_EXAMPLE` | 2 DISABLED、3 ENABLED；后两项为失败示例，不应未经筛选导入正式配置 |
| 普通表单模板 | 源 10、测试 8；共有内容无差异；缺 FT-2026-004、FT-2026-009 | 是否需带入须按用途筛选，不自动发布 |
| 文档模板及继承 | 源 7、测试 6；六套已发布版本的 sections/overrides/excluded 等业务内容一致；父模板/当前版本关系有效 | 唯一未带入 DT-REQ-WIRELESS-001 是草稿。真实浏览器已验证 6 条列表及数据网需求分析模板 10 章详情 |
| 满意度问卷模板 | 源 4、测试 1；修订 5/1 | 默认工程实施问卷存在；额外草稿、示例、可视化验收模板未齐，不等于默认问卷丢失；评分和人工启动未执行 |
| 采集模板 | 源 22、测试 0；源有 3 条 PUBLISHED，其余为 DRAFT/RETIRED | 这是当前可用配置差异，测试库无法复用源库这 3 条发布采集模板；未下发设备命令 |
| 巡检规则配置 | 源规则修订 15，测试 0，关联命令、产品类型和安全审查也为空 | 配置未齐；不迁移巡检任务选择、反馈等运行记录来冒充配置补齐 |
| 割接配置与清单模板 | 源 5、测试 3 个配置修订；两端均有 CUTOVER_STANDARD_V1 v2 PUBLISHED，以及 2 个 DISABLED 历史修订 | 缺 2 个 DRAFT；关联维度/字段/条件/节次有 ID 重建差异，尚未完成所有嵌套业务内容等价及匹配组合的运行验收 |
| 业务视图注册 | 源 30、测试 17；共有 17 中 13 仅 published_at 不同，4 项无字段差异；缺 13 个视图 | 当前正式模板最新绑定所需视图均存在；缺项含旧 direct-business 入口及 PLN_CONSTRUCTION_PLAN，不能把所有缺项推断为当前入口故障 |
| 交付定义库与引用 | 源定义 619、引用 651；测试均 0 | 独立定义库配置未齐，不能以正式模板运行快照可读取替代定义库功能验收 |
| 交付类型 | 两端均 21；按 type_code 比较 | ID 重建不是丢失证据，详见下面差异摘要；本轮未执行文件生成和交付件提交 |
| 实体表单/扩展字段绑定 | 源绑定 66、扩展 4；测试绑定 8、扩展 1 | 多数绑定是实体运行数据，不能整批当模板配置同步 |
| 业务字段配置 | 源有 plt_business_field_configuration 表及 4 条；测试无该表 | 确认结构差异；当前工作树未检出同名 SQL/Java 消费者，运行影响待关联实际来源实现，不能直接断言当前页面报错 |
| BPM 配置依赖 | 分类 3/2、表单 2/1、流程定义信息 25/8、表达式 1/0 | 需按实际引用的流程 key/版本逐项验收，表中行数不同不证明当前模板审批失败；未发起审批 |
| 通知模板 | 邮件 2/0、短信 15/10，站内信表纳入清单 | 未发送消息；差异不构成修改渠道凭据或订阅的授权 |
| 字典/菜单/角色授权 | 字典项差 75，包含旧模块、示例、PMS 规则枚举；大部分全局菜单差异来自不装配的上游模块 | 模板维护菜单存在，但当前周文成仅显示文档模板配置入口；不能因无角色菜单行直接判超级管理员无权限 |
| 状态机配置 | 测试有 V517 补入的默认状态机；源/测试修订与迁移序列不同 | 未修改状态，未进行状态机全动作回归 |

## 已确认的历史边界

测试旧发布修订 `996610195020091`～`996610195020095` 分别保留 12/12/9/9/9 处 Designer 视图快照缺项；最新 v2 已无这些缺项。历史发布修订保持原件，不作为本轮自动修改对象。

## 验证边界和建议顺序

1. 优先核定采集 3 个发布模板、巡检发布规则、独立交付定义库是否属于本次测试环境应提供的基础配置；再按正式发布/导入机制准备可审阅补齐清单。
2. 业务字段表先查明来源实现与适用迁移；不能因源有表而向测试库盲目建表。
3. 割接关联配置、BPM 流程依赖和字典项需要进一步做自然键/引用重映射及实际消费者验证；本轮不声称这些功能已经通过。
4. 全功能验收仍需拥有相应权限的会话，逐项验证编辑、校验失败、发布、停用、匹配、冻结与历史回读。本轮当前会话未具备所有模板管理入口，未擅自换号或提升权限。

## 迁移状态

- source 最近成功版本：516, 515, 514, 513, 512, 511。版本号大小不证明执行集合相同；测试未见 V515/V516，源最近为 V516。
- target 最近成功版本：519, 518, 517, 514, 513, 512。版本号大小不证明执行集合相同；测试未见 V515/V516，源最近为 V516。

## 全表盘点（不是逐行等价/功能通过结论）

| 表 | 源记录 | 测试记录 |
|---|---:|---:|
| `acc_satisfaction_questionnaire_template` | 4 | 1 |
| `acc_satisfaction_questionnaire_template_revision` | 5 | 1 |
| `bpm_category` | 3 | 2 |
| `bpm_form` | 2 | 1 |
| `bpm_process_definition_info` | 25 | 8 |
| `bpm_process_expression` | 1 | 0 |
| `bpm_process_listener` | 0 | 0 |
| `bpm_user_group` | 0 | 0 |
| `cut_checklist_binding_condition` | 2176 | 1446 |
| `cut_checklist_device_context` | 50 | 0 |
| `cut_checklist_external_mapping` | 0 | 0 |
| `cut_checklist_external_query` | 0 | 0 |
| `cut_checklist_external_query_parameter` | 0 | 0 |
| `cut_checklist_field_definition` | 1428 | 914 |
| `cut_checklist_field_option` | 30 | 20 |
| `cut_checklist_input_dimension` | 163 | 0 |
| `cut_checklist_match_candidate` | 1349 | 0 |
| `cut_checklist_result_file` | 317 | 0 |
| `cut_checklist_result_query_reference` | 0 | 0 |
| `cut_configuration_dictionary_value` | 73 | 50 |
| `cut_configuration_dimension` | 21 | 13 |
| `cut_configuration_plan_section` | 19 | 13 |
| `cut_configuration_plan_section_scope` | 244 | 164 |
| `cut_configuration_validation_issue` | 0 | 0 |
| `cut_cutover_checklist_binding_rule_revision` | 406 | 268 |
| `cut_cutover_checklist_item_definition_revision` | 407 | 269 |
| `cut_cutover_configuration_revision` | 5 | 3 |
| `imp_eng_doc_template` | 7 | 6 |
| `imp_eng_doc_template_version` | 7 | 6 |
| `int_sync_binding` | 5179 | 0 |
| `plt_business_field_configuration` | 4 | 0 |
| `plt_business_view_revision` | 30 | 17 |
| `plt_collection_template` | 22 | 0 |
| `plt_delivery_capability_config` | 0 | 0 |
| `plt_delivery_type` | 21 | 21 |
| `plt_dynamic_form_template` | 17 | 12 |
| `plt_dynamic_form_template_revision` | 38 | 25 |
| `plt_entity_extension_definition` | 4 | 1 |
| `plt_entity_form_binding` | 66 | 8 |
| `plt_form_template` | 10 | 8 |
| `pms_plat_process_definition` | 0 | 0 |
| `proj_delivery_definition_reference` | 651 | 0 |
| `proj_delivery_definition_revision` | 619 | 0 |
| `proj_project_template` | 38 | 6 |
| `proj_project_template_deliverable_definition` | 299 | 0 |
| `proj_project_template_gate_definition` | 215 | 0 |
| `proj_project_template_gate_reference` | 332 | 0 |
| `proj_project_template_milestone_definition` | 77 | 0 |
| `proj_project_template_revision` | 87 | 16 |
| `proj_project_template_stage_definition` | 120 | 0 |
| `proj_project_template_task_definition` | 258 | 0 |
| `proj_stage_suggestion_rule` | 5 | 4 |
| `proj_stage_transition_definition` | 31 | 0 |
| `proj_task_state_machine_revision` | 1 | 2 |
| `proj_task_state_transition` | 8 | 16 |
| `sol_solution_review_policy` | 37 | 1 |
| `srv_inspection_rule` | 13 | 0 |
| `srv_inspection_rule_command_revision` | 4 | 0 |
| `srv_inspection_rule_feedback` | 1 | 0 |
| `srv_inspection_rule_optimization` | 1 | 0 |
| `srv_inspection_rule_product_type_revision` | 9 | 0 |
| `srv_inspection_rule_revision` | 15 | 0 |
| `srv_inspection_rule_security_review` | 3 | 0 |
| `srv_inspection_rule_selection` | 25 | 0 |
| `system_dict_data` | 2130 | 2055 |
| `system_dict_type` | 382 | 407 |
| `system_mail_template` | 2 | 0 |
| `system_menu` | 1909 | 741 |
| `system_notify_template` | 3 | 3 |
| `system_role` | 42 | 33 |
| `system_role_menu` | 1003 | 771 |
| `system_sms_template` | 15 | 10 |

交付类型自然键差异：源独有 []；测试独有 []；共有配置差异字段 []。

## 可复查证据

- `.run/template-full-audit-20261025/audit.py`：只读核查；`audit.json`：结构、逐表差异、引用与活动计划检查。
- `details.json`：最新模板规范化摘要、当前表单摘要、文档版本摘要、菜单及角色关联。
- `source-inventory.json` / `target-inventory.json`：全表配置标识与摘要；不含原始载荷。
- `browser-document-list.txt` / `browser-document-detail.txt`：本轮真实页面证据。
- 当前源码：`ProjectTemplateV2ServiceImpl.java:160` 发布快照读取；`CollectionTemplateService.java:25` 模板查询与授权；`docs/baseline/prd-v1.8.md:253` 历史保护约束。

报告只新增过程证据，不回写 PRD、Feature/Task 权威状态，不修改两库数据。
