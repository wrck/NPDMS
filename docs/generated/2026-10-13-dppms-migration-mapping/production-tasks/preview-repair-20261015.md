# 本轮修复预览结果

业务写入0；PREVIEW_READY不代表生产就绪，正式前置及Owner缺口仍保留。旧报告及原失败Run不覆盖。phase4 P14因token401尚未POST，phase5仅续P14，不重跑P01/P15，401历史保留。

| 任务 | 当前配置/结果版本 | 状态 | 读数 | 完整summary | 技术读完 | summary问题动作 |
| --- | --- | --- | --- | --- | --- | --- |
| P01 公司、部门与主管关联 | 1/1 | PREVIEW_READY | 180 | {"CREATED": 180} | True | {} |
| P04 账号与任职资料 | 0/0 | FAILED | 1566 | {"FAILED": 1566} | False | {"FAILED": 1566} |
| P05 角色定义及来源角色绑定 | 0/0 | FAILED | 31 | {"FAILED": 31} | False | {"FAILED": 31} |
| P06 用户角色关系 | 0/0 | FAILED | 0 | {"FAILED": 0} | False | {"FAILED": 0} |
| P07 用户公司与部门范围 | 0/0 | FAILED | 0 | {"FAILED": 0} | False | {"FAILED": 0} |
| P08 基础字典类型与字典项 | 0/0 | FAILED | 612 | {"FAILED": 612} | False | {"FAILED": 612} |
| P10 CRM客户主档 | 0/0 | PREVIEW_READY | 59601 | {"ISSUE": 83, "CREATED": 59518} | True | {"ISSUE": 83} |
| P11 CRM正式产品目录 | 0/0 | PREVIEW_READY | 8441 | {"CREATED": 8441} | True | {} |
| P12 DPPMS缺失产品补齐与编码关联 | 1/1 | PREVIEW_READY | 34976 | {"INSERT": 34976} | True | {} |
| P13 CRM执行单 | 0/0 | PREVIEW_READY | 99895 | {"ISSUE": 99895} | True | {"ISSUE": 99895} |
| P14 销售订单与订单行 | 5/5 | PREVIEW_READY | 467190 | {"ISSUE": 2138, "CREATED": 465052} | True | {"ISSUE": 2138} |
| P15 正式合同、发货合同引用与发货事实 | 6/6 | PREVIEW_READY | 3848169 | {"ISSUE": 814, "CREATED": 3847355} | True | {"ISSUE": 814} |
| P16 有效项目主档、来源身份与基础最终客户资料 | 1/1 | FAILED | 0 | {} | False | {} |
| P18 项目合同关系及正式商务关联 | 3/3 | PREVIEW_READY | 82676 | {"ISSUE": 82676} | True | {"ISSUE": 82676} |
| P19 项目相关方与发货/执行状态资料 | 1/1 | PREVIEW_READY | 265547 | {"ISSUE": 265547} | True | {"ISSUE": 265547} |
| P20 项目真实产品 | 0/0 | PREVIEW_READY | 7818 | {"ISSUE": 7818} | True | {"ISSUE": 7818} |
| P21 项目租赁产品与产品配置 | 0/0 | PREVIEW_READY | 1294 | {"ISSUE": 1294} | True | {"ISSUE": 1294} |
| P22 项目产品实施范围 | 0/0 | PREVIEW_READY | 343858 | {"ISSUE": 343858} | True | {"ISSUE": 343858} |
| P23 合同回款计划 | 1/1 | FAILED | 0 | {} | False | {} |
| P24 合同实际回款信息 | 1/1 | PREVIEW_READY | 79182 | {"CREATED": 79182} | True | {} |
| P25 设备逐次续保记录 | 0/0 | PREVIEW_READY | 453443 | {"CREATED": 453443} | True | {} |
| P30 设备主档及项目归属衔接 | None/None | NOT_PREVIEWED | None | null | False | {} |
| P31 项目成员历史、历史联系人与获准授权 | None/None | NOT_PREVIEWED | None | null | False | {} |
| P33 普通安装历史及获准当前位置 | None/None | NOT_PREVIEWED | None | null | False | {} |
| P34 出厂软件版本记录 | None/None | NOT_PREVIEWED | None | null | False | {} |
| P35 软件变更批次与设备版本历史 | None/None | NOT_PREVIEWED | None | null | False | {} |
| P37 设备许可历史 | None/None | NOT_PREVIEWED | None | null | False | {} |

本轮修复：P01不复用旧ID，同批组织preview上下文用于6个拟建父码校验；8主管待P04正式账号后回补同P01。P12移除revision/标签扩展，CRM同编码正式字段保持，仅补缺。P15 B延后大JSON＋C主键有序读取键仅性能路径调整，sourceKey/原字段及合同客户冲突保护不变。P14首次迁入误带旧库fieldCarryProof映射，hasFieldCarry仅判断字段存在使空证明触发FIELD_CARRY_PROOF_MISSING；本轮移除3个ORDER恢复专用映射及空reviewed/unreviewed参数，走既有正式首次导入Owner，旧证明/接管保护及历史原件保留，没有放宽Owner校验。

65回归通过，额外父alias红测/9项绿测独立记录；真实UI临时模板生成通过；浏览器验收未保存任务或启动运行，独立API预览另计。完整终态/样本归因/基线证据见JSON；保留明细上限按任务实际记录：P15为10000，P14为2000；P15样本814 ISSUE覆盖完整814问题，CREATED中的公司未命中仅警告，不代表公司依赖就绪，前置类型见[矩阵](prerequisite-dependencies.md)。不同任务和重跑不累加readCount称业务迁移总量。

600秒有界时限保持。首批约157秒是采样观察上界，不是精确首字节延时；C4/4、父alias9和65回归分轮且有重叠，不相加称独立测试数。P12真实UI模板生成/空重复阻断及无保存已验证。151表count/maxUpdate聚合监测不是全字段校验，登录审计单列。旧P16 parser偶发首败后未改代码单类通过，原因未证明，原收据保留。

旧中断P15按恢复归档实际值在interruptedPreviewHistory列出，均非完整预览：2110482204651790337 read118000,2110486367326531586 read892000。

总状态：14 PREVIEW_READY / 7 FAILED / 6 NOT_PREVIEWED。

分对象终态计数、运行耗时及覆盖：

P01：Run 2110493084286287874，耗时 1.008秒；样本覆盖 {"count": 180, "messages": {"NO_MESSAGE": 171, "OTHER_MESSAGE_NOT_EXPORTED": 1, "保持待关联": 8}, "actions": {"CREATED": 180}, "issueCoverage": "NO_ISSUE_ACTIONS_IN_RUN_SUMMARY", "coverage": "FULL_RESULT_ROWS"}。

P12：Run 2110481910438141954，耗时 58.589秒；样本覆盖 {"count": 2000, "messages": {"OTHER_MESSAGE_NOT_EXPORTED": 2000}, "actions": {"INSERT": 2000}, "issueCoverage": "NO_ISSUE_ACTIONS_IN_RUN_SUMMARY", "coverage": "SAMPLE_ONLY"}。

P14：Run 2110507977953607682，耗时 146.235秒；样本覆盖 {"count": 2000, "actions": {"ISSUE": 2000}, "messages": {"PARENT_ORDER_MISSING: 未找到同ERP来源、公司、类型和订单号的父订单": 1590, "DUPLICATE_BUSINESS_KEY_CONFLICT": 410}, "pageChildCount": 0, "coverage": "SAMPLE_ONLY", "issueCoverage": "NOT_PROVEN_FULL_ISSUE_COVERAGE", "evidence": "D:\\CodexTaskArtifacts\\project-detail-followup\\preview-repair-20261015\\phase5\\classification-P14.json"}。

P15：Run 2110493133556776962，耗时 3398.387秒；样本覆盖 {"count": 10000, "messages": {"OTHER_MESSAGE_NOT_EXPORTED": 9186, "CONTRACT_CUSTOMER_CONFLICT": 814}, "actions": {"CREATED": 9186, "ISSUE": 814}, "issueCoverage": "FULL_ISSUE_ACTIONS_PRESENT_IN_SAMPLE", "coverage": "SAMPLE_ONLY"}。

P01即使summary无ISSUE仍有8主管待正式账号来源绑定后在同一P01回补，不表示全部业务待办完成。

终态Run 2110493133556776962：read_count 3848169，已知批次source_count合计 3848169；NULL计数批次 0；对账相等 True。
CONTRACT / COMPLETED：7697批，source_count=80517，expected=80517。
DEVICE_RELATION / COMPLETED：7697批，source_count=64712，expected=64712。
DEVICE_SN / COMPLETED：7697批，source_count=3518110，expected=3518110。
PACKAGE / COMPLETED：7697批，source_count=106635，expected=106635。
SHIPMENT_CONTRACT / COMPLETED：7697批，source_count=78195，expected=78195。
完整CONTRACT来源聚合：[{"reasonCode": "NO_SOURCE_MIGRATION_ISSUE", "count": 79703}, {"reasonCode": "CONTRACT_CUSTOMER_CONFLICT", "count": 814}]；此处是来源migration_issue分类。

终态Run 2110507977953607682：read_count 467190，已知批次source_count合计 467190；NULL计数批次 0；对账相等 True。
ORDER / COMPLETED：91批，source_count=90466，expected=90466。
LINE / COMPLETED：377批，source_count=376724，expected=376724。
完整ORDER来源预检：[{"reasonCode": "NO_SOURCE_PRECHECK_ISSUE", "count": 90056}, {"reasonCode": "DUPLICATE_BUSINESS_KEY_CONFLICT", "count": 410}]。
完整LINE来源预检：[{"reasonCode": "NO_SOURCE_PRECHECK_ISSUE", "count": 374996}, {"reasonCode": "PARENT_ORDER_SOURCE_PRECHECK_FALSE", "count": 1728}]。
来源预检问题合计2138，与summary ISSUE数量相等；Owner运行问题明细仅2000条，来源预检聚合不冒称完整Owner原因明细。

性能观察证据：
D:\CodexTaskArtifacts\project-detail-followup\preview-repair-20261015\phase4-performance-observation.json
D:\CodexTaskArtifacts\project-detail-followup\preview-repair-20261015\phase4-performance-observation.md
D:\CodexTaskArtifacts\project-detail-followup\preview-repair-20261015\p15-v3-device-stage-observations.json
D:\CodexTaskArtifacts\project-detail-followup\preview-repair-20261015\phase4\P15-first-positive-read-observation.json
D:\CodexTaskArtifacts\project-detail-followup\preview-repair-20261015\phase4\P15-device-30second-throughput.json
不宣称C吞吐提升；首批157秒只是采样观察上界，不是精确首字节延时。
