# com_sales_order 整表裁决

状态：USER_APPROVED_IMPLEMENTATION_IN_PROGRESS。

用户裁决：“按1，source_erp_system 使用source_system”。撤回5个镜像列，同时撤回source_erp_system，实际ERP系统直接复用既有source_system；不新增erp_system。项目名称建议更名未获批准，继续复用既有source_project_name。

已查原pm_order_data_from_erp全部16列，现表40列＝34个既有字段＋6个本任务新增列。仅本批10条来源用于值核对，不声称全历史已验证。

## 一次确认的整表建议

1. 撤回source_company_code、legacy_record_id、source_order_create_time、source_customer_required_time、original_source_sync_time五列，直接复用正式公司、日期及既有来源绑定。公司按已确认4项显式转换；未知码阻断。
2. 原source直接复用source_system保存SAP/D365/SMS，撤回source_erp_system，无新列。既有接收批次仍使用ERP传输契约；Owner保存及迁移/同步查询统一实际ERP系统语义，兼容当前尚未纠正记录。
3. 原projectName直接复用既有source_project_name；更名建议未获批准，既有字段名保留。
4. 其余34个正式字段逐项复用，原16列逐项处理如下。原自增ID不造业务属性，技术关联由既有迁移/同步来源绑定保留。
5. 本批9条旧01记录均已逻辑删除，10条有效订单为001。保留删除与历史关联事实，无须合并或复活；后续发现公司映射碰撞应阻断并另列裁决。
6. 先核对正式字段承接及全表独立值/关联，再清理真实库重复列；SQL/migrations直接按用户最终裁决整理，不采用先加后删的迁移。

## 34个既有正式字段

| 正式字段 | 类型 | 承接处理 |
|---|---|---|
| id | bigint | 正式订单技术主键；不写入原自增ID，原ID用于既有迁移/同步来源绑定 |
| tenant_id | bigint | 迁移租户；公司、客户及订单行关联必须同租户 |
| source_system | varchar(32) | 用户指定实际ERP系统SAP/D365/SMS承接；ERP传输批次契约保持，Owner保存及查询统一实际ERP语义 |
| source_record_key | varchar(128) | 既有幂等身份DPPMS／ERP系统／转换后公司码／订单类型／订单号，保留ERP维度和完整来源证据 |
| source_version | varchar(64) | 沿既有同步版本机制，由原syncTime形成来源版本；不使用本次运行时间冒充来源版本 |
| company_id | bigint | 精确关联已有正式公司；未知公司码阻断 |
| company_code | varchar(64) | compCode按已裁决01→001、02→002、03→003、04→004转换 |
| company_name | varchar(255) | 由精确关联的正式公司承接；原订单表没有公司名称，不编造 |
| order_type | varchar(32) | 原orderType，沿既有0销售/1退货转换 |
| order_no | varchar(64) | 原orderNumber业务订单号 |
| sales_type | varchar(32) | 原salesType原值 |
| contract_no | varchar(64) | 原contractNo，关联必须由COM Owner按正式合同身份验证 |
| execution_no | varchar(64) | 原orderExecNumber，关联必须由COM Owner按正式执行单身份验证 |
| customer_id | bigint | 按原customerCode及转换后公司精确关联；缺少可核实客户绑定时待关联，不按名字猜配 |
| customer_code | varchar(64) | 原customerCode直接承接 |
| customer_name | varchar(512) | 原customerName直接承接；不以项目最终用户名称替代买方 |
| source_project_name | varchar(512) | 既有正式业务字段承接原projectName；更名未批准，保留既有列名 |
| order_comment | varchar(2048) | 原orderComment完整承接 |
| order_amount | decimal(20,2) | 原订单头没有金额，保持既有正式事实，不从任意订单行猜算 |
| currency_code | varchar(32) | 原订单头没有币种，保持既有正式事实，不填默认币种 |
| authority_status | varchar(32) | 复用既有COM权威接收状态，不据原订单伪造审批事实 |
| source_lifecycle_status | varchar(32) | 复用现有订单/退货语义转换；不生成现平台审批或任务 |
| order_create_time | datetime(3) | 原orderCreateTime原值；既有DATETIME(3)，不再新增镜像日期 |
| customer_required_time | datetime(3) | 原customerRequireTime原值；既有DATETIME(3)，不再新增镜像日期 |
| custom_info | json | 原customInfo完整对象；本批10条均NULL，保留NULL。扩大迁移若不是合法JSON须列明并阻断，不丢弃 |
| source_sync_time | datetime(3) | 既有Owner接收同步时间，保持其正式语义，不和原syncTime混用 |
| source_updated_at | datetime(3) | 原syncTime来源更新时间；既有DATETIME(3)。较旧来源版本不得覆盖较新正式事实，原版本由既有迁移/同步证据保留 |
| status | varchar(32) | 既有商务启停状态由COM Owner维护，不能恢复逻辑删除旧订单 |
| version | int unsigned | 既有乐观锁版本，维持Owner并发保护 |
| creator | varchar(64) | 保持正式Owner审计；原表没有创建人，不能拿客户、销售人员或无依据用户冒充 |
| create_time | datetime(3) | 保持正式记录创建审计；orderCreateTime是业务订单日期，不能冒充原记录审计创建时间 |
| updater | varchar(64) | 保持正式Owner审计；原表没有更新人，不编造历史人员 |
| update_time | datetime(3) | 保持正式Owner更新审计；原syncTime是来源版本时钟，不混成审计时间 |
| deleted | tinyint | 保持既有逻辑删除事实，旧01订单9条已删除，不复活、不重定向不可变历史 |

## 6个本任务新增列逐项纠正

| 当前新增列 | 建议 | 原因 |
|---|---|---|
| source_company_code | 撤回 | 复用company_id/company_code及已确认转换，原码差异列明 |
| source_erp_system | 撤回 | 用户指定直接复用source_system承接实际ERP系统，无新增列 |
| legacy_record_id | 撤回 | 技术ID由既有来源绑定关联，不作为新增业务列 |
| source_order_create_time | 撤回 | 正式order_create_time已有DATETIME(3) |
| source_customer_required_time | 撤回 | 正式customer_required_time已有DATETIME(3) |
| original_source_sync_time | 撤回 | 正式source_updated_at已有DATETIME(3)；原版本完整留在既有来源证据 |

## 原表全部16列

| 原字段 | 正式承接点 | 处理或排除原因 |
|---|---|---|
| id | 既有迁移/同步来源绑定 | 技术自增ID没有产品/订单业务含义；不新建legacy_record_id实体镜像 |
| orderNumber | order_no | 保留原订单号 |
| contractNo | contract_no | 保留原合同号并核实正式关联 |
| orderExecNumber | execution_no | 保留原执行单号并核实正式关联 |
| orderCreateTime | order_create_time | 原值、毫秒精度，不保存重复日期 |
| customerRequireTime | customer_required_time | 原值、毫秒精度，不保存重复日期 |
| customerCode | customer_code / customer_id | 原编码正式承接；ID仅精确匹配，不猜配 |
| customerName | customer_name | 原买方名称，不替代最终用户 |
| projectName | source_project_name | 复用既有正式字段，更名未批准 |
| orderComment | order_comment | 完整原值 |
| orderType | order_type | 沿既有0销售/1退货转换 |
| compCode | company_code / company_id | 按已确认4项显式映射；原码差异在既有来源绑定/证据完整列明 |
| salesType | sales_type | 完整原值 |
| source | source_system + 既有source_record_key | 用户指定source_system直接保存SAP/D365/SMS；source_record_key继续原幂等关联 |
| customInfo | custom_info | 完整对象，不用它包裹已经有正式字段的数据 |
| syncTime | source_updated_at / source_version | 原来源版本时钟。不得把本次运行时间写成原值 |

## 真实值与关系核查

10个来源订单均唯一对应当前有效正式订单；orderCreateTime、customerRequireTime、syncTime分别与正式order_create_time、customer_required_time、source_updated_at一致。正式日期列均已为DATETIME(3)。本批customInfo全部NULL。

按原订单号查到19条正式记录：9条旧01记录deleted=1，10条有效001记录deleted=0。有效订单合计105个订单行引用；旧01记录没有订单行引用；本批合同关系order_id引用为0。此核查未改写任何订单、订单行或迁移证据。

## 当前边界

本表已按用户裁决完成代码及当前SQL定义纠正，真实库仍待正式Owner补承接和受保护的列清理。


## 纠正实施边界

6个实体镜像字段及当前SQL新增定义已撤回；直接复用正式日期、公司和source_system。正在验证Owner接收、同步和查询，未纠正真实库或重新部署。

## 最新定向验证

订单及订单行当前代码共108项定向测试通过，0失败、0错误、0跳过。覆盖原空字符串和描述空白保留、公司转换、旧来源键及ID复用、数量精度、回放和冲突保护。该定向结果不等于实际迁移或浏览器验收；实际迁移及浏览器验收尚未完成。真实库镜像列未删除，服务未重启。

## 实际MySQL Mapper验证

3项实际MySQL/XML验证及47项受影响定向回归通过。覆盖实际ERP来源类别与旧ERP类别兼容、跨租户/删除/错来源键排除、空筛选集合返回空、同步识别已删除身份以阻止重建，以及执行单原空字符串、正式组织关系、乐观锁和审计更新。测试数据只存在于单连接临时表，关闭连接后消失；未改写真实业务行或表结构。服务、迁移和页面验收仍待后续执行。


## 2026-10-10 正式承接及重复列清理完成

已通过既有迁移功能的定向纠正命令、COM Owner及单独的ORDER_FIELDS_V1回执补承接10个订单、105条订单行。实际来源D365直接承接到source_system；10个原空字符串订单备注、2个原空字符串组合码均原样补入，正常乐观版本各加1。原ID、来源键、审计人员和历史时间以及其他业务字段逐项核对不变；未复活旧01逻辑删除订单，不触发分配或验收事件。重复执行10个项目均REPLAY_COMPLETED；列清理后再次回放仍无业务写入。

6个订单及4个订单行镜像列已从任务库清理；当前SQL/migrations早已按裁决撤回新增定义，未增加先加后删的迁移。逐行流式备份、比较两表全部101262/50790行，剩余字段值一致；20个正式资料查询结果一致，项目及其他项目资料、Flyway原历史未变。原表完整16列及原NULL逐项核对，实际来源数据没有写入。10个项目的旧迁移回执保留，新字段纠正回执独立记录。

84项后端针对性检查、宿主机完整打包和8项页面组件检查通过。真实浏览器打开正式订单详情，可见D365、001、原客户/项目名称及毫秒日期；发现客户要求时间显示数字后已修正，可见2023-03-20 00:00:00.000。证据保存在本任务受控目录：verified-order-fields-formal-carry-20261010.json、verified-order-fields-after-mirror-cleanup-repeat-20261010.json、verified-order-mirror-finalization-20261010.json、verified-order-original-tables-current-20261010.json、verified-order-formal-fields-browser-20261010.txt。以上为本表实际边界，整体Flyway基线对齐和实施范围迁入仍在推进，不宣称生产上线完成。
