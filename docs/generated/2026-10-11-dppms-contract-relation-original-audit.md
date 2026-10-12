# 原合同关系与现有业务实体只读核查

状态：SOURCE_AND_EXISTING_STRUCTURE_VERIFIED / WHOLE_TABLE_PLAN_PENDING。本文件仅记录原表、旧写链和现有业务表，未批准或执行新增表列、关系写入、审计纠正或去重。许可写入仍独立等待密钥配置答复。

## 原表作用及来源

`pm_project_contract`共86780条、7列，通过`projectGroupCode`关联`pm_project_group_relationship`的项目编码，不直接包含projectId。旧首次建项目写入组及合同关系；合并合同向该组新增合同；拆分项目仍加入原组，因此会共享该组合同。旧查询按组展开项目合同关系。不能凭字段名称把它当作项目父子树或现有项目组合。

已逐表读取实际完整结构：pm_project_contract 7列、pm_project_group_relationship 9列、pm_project_group 8列。本批10项目各有1条组成员、组及合同关系；正式com_project_contract_relation也有10条，本次未修改。

| 原合同关系全部字段 | 现有承接候选或核查边界 |
|---|---|
| id | 原关系身份，适用INT/PLT来源绑定；不直接用作现平台主键 |
| contractNo | 精确关联正式合同及其contract_no；原表没有公司，不任选同号合同 |
| projectGroupCode | 原关系展开键及拆分/合并共享关系；尚未确定正式组关系承接点，不写备注或假设为父子树 |
| createTime | 正式create_time具备同义字段；本批现值为2026迁入时间，不能直接宣称已承接原历史时间 |
| createBy | 正式creator具备同义字段；应依已迁用户绑定核实，现值为管理员1，未纠正 |
| updateTime | 正式update_time具备同义字段，但原77703条NULL、正式非空，转换须整表裁决 |
| updateBy | 正式updater具备同义字段，但原86057条NULL、正式非空，处理须整表裁决 |

原createTime全非NULL，createBy有40044条NULL。不能沿其他表的限定审计纠正授权改本表。

## 既有正式关系表全部17列

| 字段 | 实际类型/可空 |
|---|---|
| id | BIGINT / 非空 |
| tenant_id | BIGINT / 非空 |
| project_id | BIGINT / 非空 |
| contract_id | BIGINT / 非空 |
| relation_role | VARCHAR(32) / 非空 |
| source_system | VARCHAR(32) / 非空 |
| source_table | VARCHAR(64) / 可空 |
| source_record_key | VARCHAR(128) / 可空 |
| effective_from | DATETIME(3) / 可空 |
| effective_to | DATETIME(3) / 可空 |
| status | VARCHAR(32) / 非空 |
| version | INT UNSIGNED / 非空 |
| creator | VARCHAR(64) / 非空 |
| create_time | DATETIME(3) / 非空 |
| updater | VARCHAR(64) / 非空 |
| update_time | DATETIME(3) / 非空 |
| deleted | TINYINT / 非空 |

正常ContractRelationCommandService按项目与合同创建RELATED关系，校验项目管理权和合同公司范围，使用当前操作者、执行时间、ACTIVE、版本0。本批10条来源标记PMS/COM_PROJECT_CONTRACT_RELATION、创建时间为2026-10-07迁入操作；是已存在业务关系，不能以存在关系替代完整原7列承接，也不能绕过Owner覆盖现存审计。

## 全历史例外和模型真实性

按合同号＋组编码有34组重复、额外47行，总81行。其中18组除原id外全部值相同，16组原审计值不同；没有去重，也没有任选一条。8条合同关系缺原组，9条缺原组成员，这两组可能重合，不能直接相加为17个异常。缺少可核实项目时不挂靠任意项目。

仓库存在未跟踪的ProjectContractHistoryDO，但指定任务库不存在com_project_contract_history，SQL/migrations也没有其定义。它不能作为已具备的权威业务承接能力。正式proj_project_portfolio和成员表实际存在，组合用途为战略/客户/区域/计划/专项；未发现据此把旧合同共享组直接转换为这些用途的批准。本次不先建表再请用户确认。

下一步须把原共享组业务、重复审计行和既有关系保护纳入完整整表方案，明确既有字段复用及确有缺项，然后提交用户裁决。上述7列没有被排除或丢弃；原关系与组全字段资料保留，来源材料不称为正式业务承接。

证据：任务私有contract-relation-original-readonly-20261011.json；旧sql-map-project-config.xml的insert_project_contract（1262）、insert_merge_contract（3360）、insert_branch_group_relationship（3430）；现有ContractRelationCommandService、ProjectContractRelationDO及真实SHOW FULL COLUMNS。schemaWrites=0，businessWrites=0。
