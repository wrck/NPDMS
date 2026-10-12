# 许可历史整表方案

状态：WHOLE_TABLE_APPROVED / PILOT_LOCAL_VERIFIED / PRODUCTION_BASELINE_PENDING。2026-10-11用户明确批准16列及上述处理整表。批准时未建表、写入或读取许可码原值；原表全部8列已通过SHOW FULL COLUMNS核实，全量475468条。旧刷新SQL从dptech_v_liccode_info读取7项属性，原页面按合同查询；原id是刷新缓存自增身份，不当作业务编码。

按整表批准由AST持有正式ast_device_license_record，最终16列。许可是设备/产品使用权资料。AST负责AES-256-GCM认证加密封装验证，复用此前审查过的加密实现，未依赖其他模块biz类；PLT通过稳定公开API保存来源绑定、命令回执和保护基线。密文封装包含密钥版本，不提供解密接口，日志与页面不含许可码原值。

## 全部16列整表批准

批准前本表不存在；下列全部属于本轮整表批准的新增物理列，没有其他列。

| 列 | 类型/可空 | 用途及依据 |
|---|---|---|
| id | BIGINT / 非空 | 正式记录ID；原id通过既有迁移绑定承接 |
| tenant_id | BIGINT / 非空 | 现平台租户边界 |
| device_id | BIGINT / 可空 | 由AST按租户及完整SN精确核实，不匹配时不猜配 |
| device_sn | VARCHAR(255) / 可空 | 原sn完整保留，不按当前设备字段长度截断 |
| contract_id | BIGINT / 可空 | COM Owner核实唯一正式合同后关联；原表无公司，不凭合同名称或任取同号合同 |
| contract_no | VARCHAR(32) / 可空 | 原contract完整值 |
| contract_type | VARCHAR(16) / 可空 | 原contractType业务值，保持原分类，不伪造当前合同分类 |
| product_code | VARCHAR(255) / 可空 | 原item；旧页面明确展示为产品编码，与原字段“项目编号”注释有差异，保留核查说明 |
| product_model | VARCHAR(256) / 可空 | 原specModel，许可登记自身型号，不用当前目录值覆盖 |
| license_status | VARCHAR(255) / 可空 | 原status，不据历史值生成当前启用、失效或许可激活动作 |
| license_code_ciphertext | TEXT / 非空 | 原licenseCode受控认证加密；密文封装包含算法及密钥版本，禁止明文落库、快照、页面和日志 |
| creator | VARCHAR(64) / 非空 | 原表无审计，使用实际迁入操作者 |
| create_time | DATETIME(3) / 非空 | 实际迁入时间，不伪造来源历史时间 |
| updater | VARCHAR(64) / 非空 | 与首次创建操作者相同 |
| update_time | DATETIME(3) / 非空 | 与首次创建时间相同 |
| deleted | BIT / 非空 | 现平台既有逻辑删除规则，默认false |

不新增project_id或公司镜像：原表的业务关系是合同和设备，项目查询由正式项目合同/设备关系经Owner获取。无法确认的device_id/contract_id保留NULL并登记待关联，原SN/合同/产品等字段仍由正式实体完整承接，不以来源快照代替。

## 原8列逐项映射

| 原列 | 原类型/可空 | 全表非NULL数 | 正式承接 |
|---|---|---:|---|
| id | INT / 非空 | 475468 | 已有INT/PLT来源绑定，不新增技术镜像 |
| licenseCode | VARCHAR(256) / 非空 | 475468 | license_code_ciphertext；仅安全流式采集、传递、加密，来源证据同样受控加密 |
| sn | VARCHAR(255) / 可空 | 474236 | device_sn及可核实device_id |
| specModel | VARCHAR(256) / 可空 | 451882 | product_model |
| contract | VARCHAR(32) / 可空 | 474209 | contract_no及可核实contract_id |
| contractType | VARCHAR(16) / 可空 | 474066 | contract_type |
| item | VARCHAR(255) / 可空 | 475468 | product_code；按旧业务页面含义，原注释差异列明 |
| status | VARCHAR(255) / 可空 | 475468 | license_status，原值保留，不翻译为当前状态机 |

没有原字段排除。原NULL与空字符串区分保留；空许可码属于无法满足受控密文承接的异常，阻断并提交具体裁决，不用占位秘密。重复原行逐条保存；旧刷新会清空并重建自增ID，持续刷新身份尚未明确，本轮只规划历史迁入，后续刷新须另行裁决，不据原ID随意覆盖记录。

查询与写入通过AST Owner及既有迁移/同步功能；浏览器只提供非秘密许可资料和“许可码已加密保存”状态，继承当前项目/设备/合同授权，不增加新角色或放宽权限。缺失当前对象只登记待关联，不创建虚假设备、产品、合同或公司。

依据：D:/EclipseWorkspace/Parctice/PMS/PMS-struts/config-ibaits/sql-map-refresh-data-license-config.xml:6、27、36；sql-map-project-config.xml:6405；WebContent/sys/module/sub/licenseInfo.jsp:21—27；任务私有license-schema-only-audit-20261011.json。licenseValuesRead=0、writes=0。

## 本轮实施与验证边界

20项定向Java测试、4项真实MySQL连接内临时表测试和4项Python加密采集测试通过。覆盖完整16列、原NULL/Unicode/空格、认证密文、跨项目共享原许可、幂等保护、后续业务及审计变化阻断、租户隔离、空集合不扩大查询及插入后失败回滚。MySQL测试使用真实AST Mapper和租户/审计拦截器，PLT回执是测试替身，不能据此宣称真实跨模块回执回滚已验收。完整后端打包通过；运行包包含AST许可Owner与PROJ迁移应用，此前未接入的重复PROJ许可模型已撤回，未建其业务表。

指定任务库已有其他业务V426—V432回执，原拟V426未执行，许可文件已改用经核实未占用的V433。Compose/Flyway成功应用`V433__approved_device_license_record.sql`，实际16列，0条记录；受保护的项目、设备、范围、发货引用、软件、保修及全部既有Flyway回执保持。仅验证本次新表及指定环境，完整生产迁移基线和历史校验对齐仍未完成。

10个项目最新来源材料共153条独立原许可，7个非秘密原字段已与原表逐值核对。本轮尚未读取真实许可码或执行许可迁入。任务后端59282已切换本轮测试过的运行包，仅开放已授权查询；没有配置许可密钥，非空许可迁入在写入前阻断。此前私有DPAPI密钥文件通过Python与.NET均无法在当前上下文解封，保留不覆盖；已向用户询问首次承接是否生成独立受控密钥或指定原密钥可用配置。采集、真实Owner迁入、重放及带数据页面验收等待此答复。

证据在任务私有license-test-20261011.log、license-mysql-20261011.log、license-runtime-package-20261011.log、license-ddl-result-20261011.json、license-v433-flyway-20261011.log及license-pilot-state-20261011.json。失败的测试替身重设日志保留；修正后20项通过，未放宽业务校验。

后续查询复核发现当前来源头会随资料刷新变化，只追最新快照会隐藏已迁入许可。新增失败测试实际复现1条正式许可变为0条；现已改为查询该项目各来源的全部历次许可绑定，按正式目标ID归集，再由AST返回正式实体值。最新21项定向测试通过；真实源材料查询覆盖499条历次采集，包含本批153条，其他租户返回0，没有业务写入或新增结构。SQL仅查询PROJ自身来源记录及快照，类型化场景Query和ID游标分页，不跨模块读取AST表。来源证据用于关联，不作为业务显示值。运行包因此更新，原失败日志保留。证据license-historical-source-query-20261011.json；真实许可迁入及页面验收仍等待密钥回复。

查询版本真实页面已核实：项目992203060189进入DPPMS关联资料，选择系统项目资料的许可历史，显示完整非秘密表头、空列表及共0条，接口返回total=0；当前正式表仍未写入许可。此检查只证明查询入口和空列表可用，不代表153条迁入或密文验收完成。浏览器证据为任务私有license-query-only-project189-browser-20261011.txt；运行配置标识licenseQueryOnly=true。

## 密钥裁决后真实承接结果

2026-10-11用户明确批准“生成独立受控密钥，保留旧文件”。独立AES-256密钥已生成并以Windows DPAPI CurrentUser保存，文件ACL仅允许实际当前用户及SYSTEM；旧受保护文件未覆盖。运行时解封至进程内配置，不将明文密钥写入脚本、配置模板、证据或聊天。59282已在既有授权下加载密钥，59191、48182、19082及受保护业务数据保持。旧query-only及0行记录为实施前证据。

通过既有迁移功能及AST Owner对同一10项目执行10个NEW，实际落库153条×16列；逐条核对原8列、非秘密字段的NULL/空字符串、真实迁入人及时间，内存解密逐字节比对原许可码并核验认证MAC。没有原字段排除或明文许可码证据。10次REPLAY_COMPLETED通过，记录ID、16列全值及其他受保护业务/原来源材料均不变。

设备关联缺失0条，合同关联缺失0条；按已批准规则保持NULL并登记，完整原SN、合同及产品属性仍在正式业务表。具体清单仅包含非秘密属性，见任务私有license-pilot-final-proof-20261011.json，不创建虚假对象或激活许可。

真实浏览器在项目992203060189的系统项目资料/许可历史读取40条，两页各20条，完成第二页及详情点击；页面仅显示非秘密正式字段和“已加密保存”，不含许可码、密文、MAC或密钥标识。三份浏览器证据为license-project189-first-page-browser-20261011.txt、license-project189-second-page-browser-20261011.txt、license-project189-detail-browser-20261011.txt。所有10项目查询数量与迁入分区一致。

本次完成的是指定任务环境153条许可承接及查询验收，未迁475468条全历史。旧缓存持续刷新身份尚未批准，仍为一次性迁入；完整Flyway基线只读validate已有真实失败，尚未对齐。DPAPI文件绑定本机当前Windows身份，生产部署须由部署身份配置同一受控密钥并建立受控保管/恢复方式，不能靠复制受保护文件认定可恢复。本次不宣称生产可上线或全任务完成。
