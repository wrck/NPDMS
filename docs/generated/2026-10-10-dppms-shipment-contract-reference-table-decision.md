# 发货合同引用整表与保修年数裁决

状态：WHOLE_TABLE_APPROVED / LOCAL_VERIFIED。用户已回复“接受建议”，批准本表29列及保修年数纠正。本任务库实际结构、迁移/同步预览与执行、重放及真实任务配置页面已验收；生产历史SQL/Flyway基线对齐仍待处理，未声称生产上线完成。

已核全部fb_contract原12列/120639行及现有com_shipment_contract_reference全部29列/120638行。当前引用全部来自DPPMS；warranty_flag存有0～10及空白，而它的正式定义是0否/1是。实际原视图以c.warranty×12计算设备保修月数，原年数与现有标志定义不一致。

建议不新增表、物理列或实体扩展：仅将现有warranty_flag更名为warranty_years并由VARCHAR(8)改为INT NULL，业务含义改为合同保修年数；其余28列复用原定义。已有来源0/1也按0/1年处理，不混为布尔标志，不据年数产生当前保修状态或续保审批。若保留原warranty_flag定义，请由用户指定年数的其他正式承接字段；不能把年数继续存入0/1标志。

当前源2条NULL、2条空白；目标1条NULL、2条空白，源目标数量差1需逐原键核查，本报告不推断缺失原因或授权忽略。原空白或NULL没有年数，建议正式年数为NULL，原格式及排除原因在来源证据列明；0～10原整数保持。非整数/负数或目标出现后续修改均阻断待裁决，不由本批整数统计扩大转换规则。

批准后逐键核查：120638条正式引用全部唯一匹配原合同键，年数业务值无差异，没有目标多余键或两侧重复键。来源多出1条：contract_id、contract_code、office_code均为空字符串，其他9列全NULL；缺乏来源身份、合同号及任何合同业务事实，无法生成有效合同引用，原行完整保留在不可变核查证据并逐列列明。该行不归并到任一正式合同，不制造合同编号。证据：approved-contract-years-keyed-prechange-20261010.json，业务写入0。

## 正式29列逐项处理

| 既有字段 | 类型/可空 | 建议处理 |
|---|---|---|
| id | bigint/NO | 原定义及现值保持，按原同义字段承接 |
| tenant_id | bigint/NO | 原定义及现值保持，按原同义字段承接 |
| contract_id | bigint/YES | 原正式字段直接复用，保护现值；跨系统关系沿既有精确关联 |
| contract_no | varchar(64)/YES | 原定义及现值保持，按原同义字段承接 |
| company_id | bigint/YES | 原正式字段直接复用，保护现值；跨系统关系沿既有精确关联 |
| company_code | varchar(64)/YES | 原正式字段直接复用，保护现值；跨系统关系沿既有精确关联 |
| company_name | varchar(255)/YES | 原正式字段直接复用，保护现值；跨系统关系沿既有精确关联 |
| department_id | bigint/YES | 原正式字段直接复用，保护现值；跨系统关系沿既有精确关联 |
| department_code | varchar(64)/YES | 原正式字段直接复用，保护现值；跨系统关系沿既有精确关联 |
| department_name | varchar(128)/YES | 原正式字段直接复用，保护现值；跨系统关系沿既有精确关联 |
| contract_type | varchar(32)/YES | 原定义及现值保持，按原同义字段承接 |
| customer_name | varchar(512)/YES | 原定义及现值保持，按原同义字段承接 |
| project_name | varchar(512)/YES | 原定义及现值保持，按原同义字段承接 |
| market_code | varchar(64)/YES | 原定义及现值保持，按原同义字段承接 |
| market_name | varchar(128)/YES | 原定义及现值保持，按原同义字段承接 |
| system_source_key | varchar(64)/YES | 原定义及现值保持，按原同义字段承接 |
| system_code | varchar(64)/YES | 原正式字段直接复用，保护现值；跨系统关系沿既有精确关联 |
| system_name | varchar(128)/YES | 原定义及现值保持，按原同义字段承接 |
| warranty_flag | varchar(8)/YES | 更名warranty_years，INT NULL，承接原合同年数；不增加第二列 |
| remark | varchar(4096)/YES | 原定义及现值保持，按原同义字段承接 |
| mapping_status | varchar(32)/NO | 原定义及现值保持，按原同义字段承接 |
| source_system | varchar(32)/NO | 原定义及现值保持，按原同义字段承接 |
| source_record_key | varchar(128)/NO | 原定义及现值保持，按原同义字段承接 |
| source_sync_time | datetime(3)/YES | 原定义及现值保持，按原同义字段承接 |
| creator | varchar(64)/NO | 原定义及现值保持，按原同义字段承接 |
| create_time | datetime(3)/NO | 原定义及现值保持，按原同义字段承接 |
| updater | varchar(64)/NO | 原定义及现值保持，按原同义字段承接 |
| update_time | datetime(3)/NO | 原定义及现值保持，按原同义字段承接 |
| deleted | tinyint/NO | 原定义及现值保持，按原同义字段承接 |

## 原12列逐项映射

| 原字段 | 原类型/可空 | 正式承接/转换与原因 |
|---|---|---|
| contract_id | varchar(64)/YES | 既有source_record_key与INT绑定，保留原合同技术键，不当作正式contract_id |
| contract_code | varchar(25)/YES | contract_no |
| office_code | varchar(15)/YES | department_code，并沿既有精确部门关系补ID/名称 |
| contract_type | int(11)/YES | contract_type，沿既有STRING转换 |
| customer_name | varchar(512)/YES | customer_name |
| project_name | varchar(512)/YES | project_name |
| warranty | varchar(2)/YES | 候选warranty_years，单位年；原空白/NULL→NULL，整数原值保持 |
| marketCode | varchar(10)/YES | market_code |
| marketName | varchar(15)/YES | market_name |
| systemId | int(11)/YES | system_source_key，原系统ID不冒充system_code |
| systemName | varchar(15)/YES | system_name |
| remark | varchar(4096)/YES | remark，原合同备注自身事实，不承接其他字段 |

## 实施与保护边界

只有整表裁决后才实施：通过现有迁移/同步功能登记版本与转换规则，COM提供正式年数读写；同步模板和已配置任务的映射一并受控切换，不能只改列名让旧任务继续往已撤回字段写入。先逐原键确认120638目标、当前来源、旧成功回执及全部业务/审计值，有后续变化阻断；实际改名/类型转换只改变获批准的同义值，不重新写审计时间，不删除任何正式引用，不合并不同公司合同。

公司继续01→001、02→002、03→003、04→004，未知非空码阻断。公司身份必须沿已执行同步的实际转换及唯一订单公司规则，不从部门名称猜配。本表contract_id是正式合同主档ID，source_record_key是fb_contract原技术ID，不能互相替代。

当前SQL/migrations按用户要求整理为最终定义，不追加“先加后删”字段；原执行文本、Flyway历史及不可变来源证据保留。其他端口、当前设备维保投影、已有维保/续保事件不据本次结构变化改动。

原始证据：shipment-contract-whole-table-readonly-20261010.json、warranty-contract-reference-readonly-20261010.json，writes=0；V258原warranty_flag定义、原SHOW FULL COLUMNS及原generic-templates的STRING映射。原36列保修材料仍保留，不替换来源。

## 已执行结果及可复用转换

实际warranty_years为INT NULL，表仍为29列。120638条逐键与原12列核对；只将2条空白年数通过限定COM Owner及迁移/同步纠正为NULL，其余28列及审计值全部保持。原年数0至10均原样保存，原NULL仍NULL。原库额外1条contract_id/contract_code/office_code均为空、其余9列全NULL，没有可形成合同引用的业务身份；完整记录和原因留证，不伪造合同、不声称获授权丢弃。

转换DPPMS_WARRANTY_YEARS只适用于本表warranty_years：NULL/空白→NULL，非负整数→整数；负数、小数、科学计数法、非数字及溢出阻断。此字段的NULL是已批准业务值，不能受一般“保留目标已有值”策略吞掉；其他字段沿原空值保护。6个现有任务受控更新定义及版本，仍保持暂停；2个只做引用查找的任务定义保持。

| 运行 | ID | 真实结果 |
|---|---|---|
| 修复后预览 | 2108801389591171074 | PREVIEW_READY，读取2，UPDATED=2 |
| 执行 | 2108801440837177346 | SUCCESS，UPDATED=2 |
| 重放 | 2108801493274365953 | SUCCESS，UNCHANGED=2 |
| 最终INT类型重放 | 2108801809084485634 | SUCCESS，UNCHANGED=2 |

早先预览2108799117113057282因真实租户SQL解析器不能解析字符集CAST而失败，没有执行业务纠正，失败回执未覆盖。改用保持同一完整行指纹的SQL后，真实MySQL＋租户拦截器测试3项通过；相关42项转换、Owner、适配器、配置和通用同步测试通过，完整打包通过。真实浏览器在既有“合同、装箱单与发货记录完整同步”的配置中确认warranty→warranty_years及“DPPMS合同保修年数（空值保留为NULL）”可见，未保存或启用调度。

实施证据：verified-approved-contract-years-final-20261010.json、approved-contract-years-sync-state-20261010.json、verified-contract-years-approved-browser-20261010.txt。正式索引、其他28字段、审计、设备/项目/安装/位置/实施范围保护基线及不可变来源材料、Flyway执行历史均保持。当前SQL/migrations已按用户要求整理最终定义；未执行repair或改写旧校验值，生产部署仍须单独解决基线对齐。
