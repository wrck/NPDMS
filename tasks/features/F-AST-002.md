# F-AST-002 设备产品类型受控副本与公开查询

> Feature实施状态：`IN_PROGRESS`
> Feature Ready Gate：`READY / GO`（来源裁决有效；master修订011已关闭`Q-GOV-20260901-001`）
> Technical Plan Gate：`PASS / NPDMS-FAST002-TECHPLAN-20260830-01`；身份契约差量`PASS / NPDMS-FAST002-IDENTITY-CONTRACT-DELTA-20260830-FINAL`
> Implementation Done Gate：`NOT_ESTABLISHED`
> 当前阻断：`master真实MySQL复验和独立Done裁决未完成`；历史分支Done只作证据，Requirement投影保持`EQP-01@V1=PARTIAL`
> Requirement ID：`EQP-01（V1/P0）`
> Feature Spec：`specs/features/F-AST-002-device-product-type-copy-and-public-query.md`
> Technical Plan：`docs/superpowers/plans/2026-08-30-f-ast-002-device-product-type-copy-and-public-query.md`
> 锁定规格提交：`a52b22b4`
> 关联消费：`F-INS-001`发布与工程师选择外部Gate
> 适用基线：master `CHG-PRD-2026-09-02-011`；来源AST同号修订只作历史证据

## 当前最小工作单元

- `DU-20260902-ACC-AST-SELECTIVE-INTEGRATION`已将`a52b22b4..68bc56ec`中的F-AST-002能力选择性集成至`master@524a70e7`，排除夹带迁移、F-INS提交和来源工作树脏改动。

## 来源分支已完成候选（只作证据）

- Feature Ready：`GO NPDMS-FAST002-FEATURE-READY-20260830-01`。
- 唯一Technical Plan已覆盖公开API、三表、权限、来源顺序、冲突事务、批量授权范围、测试、真实MySQL和追溯收口。
- 首轮独立复审发现来源顺序、设备复合外键、关联设备范围、测试节奏和冲突事务五项问题；已按正式SDS、Feature Spec和计划顺序整改。
- 整改复审：`GO NPDMS-FAST002-TECHPLAN-20260830-01`；只放行Implementation，不代表Implementation Done或后续发布Gate。
- Task 1独立代码复审发现自由`serviceIdentity`只能做白名单声明、不能证明调用主体；已登记Q-FAST002-001并接受高可信裁决ADR-0036。两轮NO-GO整改闭合专用适配器、不可绕过验证和受控导入主体后，设计差量终审`GO NPDMS-FAST002-IDENTITY-CONTRACT-DELTA-20260830-FINAL`。
- Task 1代码已交付两个公开Query/Result契约、包级栈式调用上下文、不可变主体授权注册表、租户/动作/委托用户守卫和错误码；15项定向测试、模块编译、SDS与追溯检查通过，独立代码复审`GO NPDMS-FAST002-TASK1-REMEDIATION-REVIEW-20260830-01`。Inspection专用适配器、空设备范围查询和受控导入仍分别保留在Task 8、Task 5和Task 4。
- Task 2已交付`ast_product_type`、`ast_product_type_source_mapping`、`ast_device_current_product_type`三表前向迁移和三个Tenant DO；稳定编码/来源键、冲突证据、当前引用、同租户复合引用和状态空值约束已落盘。原迁移V132因固定权威测试库已由F-CUT-001占用，按计划串行前移为V146；V132～V145历史确认、V146 migrate和后续validate均PASS。Schema契约4项、AST Reactor编译、SDS Phase 2、需求追溯均PASS；MySQL 8.4.10创建3表、9个CHECK、4个外键和生成列成功。独立复审`GO NPDMS-FAST002-TASK2-FINAL-REVIEW-20260830-01`；并发和幂等仍保留在Task 7/Task 4。
- Task 3已交付三个场景化Query、三个Mapper、三个XML和授权设备投影；动态集合空范围失败关闭，来源映射按稳定唯一键`FOR UPDATE`，授权设备单次联查当前项目与有效项目关系并保留未解析状态。首轮独立复审NO-GO指出逻辑删除和真实证据缺口，已补齐三类表逻辑删除条件与负向验证。定向测试19项、真实MySQL Mapper 3项、标准Flyway validate和模块编译均PASS；最终独立复审`GO / NPDMS-FAST002-TASK3-FINAL-REVIEW-20260830-01`。
- Task 4已交付唯一受控导入POST入口、Controller与Service双重专用权限、认证上下文租户/操作者、`sourceUpdatedAt`唯一排序水位、平台幂等成功编排、产品类型/来源映射/设备当前引用事务写入、冲突`REQUIRES_NEW`记录、来源失败保留最近成功副本和摘要化操作审计；软删除来源键继续占用并稳定拒绝，冲突审计携带当前来源证据，并发推进保护不会反向覆盖更晚成功事实。V147只登记`pms:asset-product-type:controlled-import`且不绑定角色，固定测试库Flyway validate 143项PASS。Task 4定向测试30项、真实MySQL事务/Mapper 7项、AST Reactor编译和`git diff --check`均PASS；独立复审`GO / NPDMS-FAST002-TASK4-REMEDIATION-REVIEW-20260831-01`，不代表Feature Implementation Done。
- Task 5已交付`AssetProductTypeQueryService`和薄`AssetProductTypeApiImpl`：按编码查询在可信动作校验后一次批量读取并按请求顺序补齐未知、停用和最近成功副本事实；授权设备查询校验可信动作与委托用户，空设备/空项目范围失败关闭，以统一`effectiveAt`复用Task 3联查并映射未解析状态。Task 5定向测试10项、Task 1/3/5组合定向测试21项、AST Reactor编译和`git diff --check`均PASS；独立复审`GO / F-AST-002-TASK5-CURRENT-DIFF-REVIEW-20260831-01`。Inspection不可伪造专用适配器仍保留在Task 8，本结论不代表Feature Implementation Done。
- Task 6已执行Task 1至5跨单元定向回归，并补齐Writer首次导入、幂等重放、陈旧来源、更晚来源更新、既有副本空响应保留和审计详情脱敏证据；回归发现软删除产品类型编码会被同源导入错误更新，已最小修复为`PRODUCT_TYPE_CODE_RESERVED`稳定拒绝且不写产品类型、来源映射或设备事实。15个测试类71项、AST Reactor编译和`git diff --check`均PASS；独立复审`GO`。真实MySQL软删除唯一键、事务和并发证据仍保留在Task 7，本结论不代表Feature Implementation Done。
- Task 7已在固定隔离MySQL中验证三表约束、受控导入、幂等重放、陈旧水位和来源失败不覆盖最近成功事实、停用历史、授权设备范围、跨租户及审计失败回滚；并以真实线程和同步栅栏验证同稳定编码、同来源不同目标、同设备当前引用和同幂等键竞争。空库143项迁移、重复migrate以及V145→V147前向升级均PASS，既有设备数据指纹与`ast_device`结构不变；10项导入集成、3项Mapper、4项并发共17项真实MySQL测试、AST Reactor编译及`git diff --check`均PASS；独立复审`GO`，仅放行Task 7，不代表Feature Implementation Done。

- Task 8已新增仅含两个查询方法的`InspectionAssetProductTypeApi`，由AST适配器固定建立Inspection调用上下文后委托通用API；Service模块仅依赖`pms-module-asset-api`，未依赖AST业务模块或访问其DO、Mapper、Service及业务表。5项定向测试、相关Reactor编译、依赖树和`git diff --check`均PASS；独立复审`GO`。本结论不代表Inspection规则、发布或工程师选择闭环完成。

- Task 9已完成产品类型定向回归90项、全仓Unit分类、`yudao-server`装配、全Reactor `verify -DskipTests`、需求追溯、迁移契约、Phase 2契约图及SDS校验；F-AST-002适用测试均PASS。全局Contract与Integration仅保留未由本Feature引入的Platform文件迁移契约及动态表单夹具既有失败，独立裁决允许如实登记后收口。Implementation Done Gate=`PASS / NPDMS-FAST002-IMPLEMENTATION-DONE-20260831-01`；Requirement投影保持`EQP-01@V1=PARTIAL`。

> 检查点（2026-08-31）：基线`c967d667`；当前Gate=F-AST-002收口提交；证据=90项定向、Unit、装配、verify、追溯校验及独立GO；阻塞=无；下一步=提交收口后选择下一个前置已满足的巡检Feature Gate。

## Task 10：master选择性集成与复验

- [x] 仅集成产品类型受控副本、公开查询、受控导入和Inspection只读适配器，Flyway重编号为V164～V165。
- [x] 在当前master复核权限、来源水位、冲突事务、授权范围、后端构建和聚焦测试。
- [ ] 在当前master完成真实MySQL复验并申请独立Implementation Done裁决。
- [x] 更新Requirement矩阵和DU回执，保持`IN_PROGRESS / NOT_ESTABLISHED`。

> master迁移裁决：来源分支的V146/V147只作为历史证据；进入master时按当前最大版本之后前移为V164/V165，不接收来源区间夹带的V132～V145。

## 实施范围

- 产品类型受控副本及来源映射。
- 设备当前产品类型引用与解析状态。
- `pms-module-asset-api`公开查询契约和DTO。
- 按编码批量查询及按授权设备查询。
- 租户、设备和数据范围守卫。
- 停用历史解释、来源降级、并发与审计。
- 前向Flyway、后端测试、真实MySQL和契约消费验证。

## 明确排除

- CRM/MES网络连接、认证、调度、游标、重试、补偿和对账。
- EQP-04连接器Implementation Done。
- 产品类型自由维护、猜测映射或示例值种子。
- Inspection业务表、规则发布、任务选择或设备连接采集实现。

## 完成条件

- 全部实现直接追溯`EQP-01@V1`合法子闭环和`CHG-PRD-2026-08-30-010`。
- `AssetProductTypeApi`契约、权限负向、未知/停用、空范围、来源降级和并发测试通过。
- 真实MySQL前向迁移与约束验证通过。
- F-INS-001消费契约可在不直读AST表、不依赖连接器的情况下验证。
- 更新Feature索引与Requirement追溯后，才可记录唯一Implementation Done Gate。

> master检查点（2026-09-02）：代码回执=`524a70e7`；当前Gate=`IN_PROGRESS / NOT_ESTABLISHED`；已通过=27模块依赖构建、Service 8项与Asset 192项适用测试（27项MySQL跳过）；阻塞=当前master真实MySQL与独立Done裁决未完成；下一步=补齐运行证据并申请独立裁决。
