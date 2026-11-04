# 项目86648详情完整性诊断（2026-11-02）

用户反馈多项目发货数据查询失败，随后明确为项目详情数据不完整，并提供项目86648。只读诊断确认：接口未报错，基础记录存在但页面关联链未闭合；不能用25个同步任务COMPLETED证明项目详情完整。

## 样例事实

- 项目：86648，16314026012203N-0，ACTIVE；正式合同31020261028A01已关联。
- 正式订单接口返回1条；装箱单接口返回0；设备列表按projectId返回0。
- 该合同下正式发货12条，12条均已关联订单行；设备12台。项目有9条已确认订单行分配，分配数量合计12。
- 正式包裹1条，原合同来源键187ca230-6a2d-4c40-af08-92bb6d799bb9可精确命中发货合同引用，但shipment_contract_ref_id为NULL。页面通过该正式引用联查，故查不到。
- 项目当前设备归属0、安装历史0；直接只读源pm_project_shipment按projectId=86648查询也是0条。不能把空安装历史认定为漏迁12条安装记录，也不能仅换成读取原项目登记表解决设备展示。

## 共性范围

- 正式包裹159930条，shipment_contract_ref_id全部NULL。
- 正式设备3506715台，project_id全部NULL。BASE_P30明确只承接设备基础，BASE_P33明确只承接安装历史且不更新设备主档；现行项目序列号查询却按设备当前project_id筛选。
- 包裹引用可按原来源键补齐；项目详情的发货关联展示与当前设备归属属于不同语义，需要确认口径，不直接按合同把设备归给项目。

## 拟修复及边界

包裹增加受来源绑定/旧指纹/租户保护的精确引用补齐；项目只读展示按已确认的订单/合同发货关联取设备，保持当前归属及写操作授权。用户已确认方案1，记录在Q-PROJECT-SHIPMENT-DISPLAY-20261102。代码已实现，当前尚未进行生产数据补写或发布。

证据位于F:/codex-base-sync-route-20261102：result-362-project-86648-read.json、result-364-project-86648.json、result-365-project-chain.json、result-367-project-source.json。原始SQL是只读查询；request-363首条查询引用不存在的source_system列失败，364已移除该列重新核对，未将失败查询计为通过。


## 原任务裁决追溯与修复结果

- [原P15收尾](../migration-task-capabilities-20261015/P15-delivery-completion.md)已定义包裹精确父引用补齐；新BASE_P15遗漏此步。本次添加独立PackageContractReference阶段，读取本地包裹，验证本任务包裹/父合同绑定、租户及旧指纹，只补空引用；已有不同引用保留并报告，更新保持原审计时间。正式执行锁定目标及父记录，不重新读取远端发货。
- [10月23日组织回填报告](../device-organization-backfill-20261023/report.md)明确设备公司/部门补齐与当前项目指派不同，3486084台当前项目仍为空；[P30裁决](../migration-task-capabilities-20261015/P30-device-assignment.md)规定指派为单独显式操作，要求SN、生效时间、安装来源及预览摘要。正常基础同步不自动产生项目归属。本次不恢复旧指派任务、不直接写project_id。
- AST新增项目发货只读API，调用COM当前关联合同API，不跨模块查询COM表。项目完整查看范围及设备查询权限同时生效；空合同集返回空页，SQL精确匹配合同号且限制租户/未删除。保留同SN多次发货，不读取设备当前地址或配置。API不返回来源原文。
- 项目序列号页面新增默认“发货设备”页签，按发货记录计数；原“归属设备”、导出、配置Log继续使用原查询和权限。
- 自审已检查模块边界、空权限、敏感字段、重复SN、历史保护、事务回滚及生产构建参数；未发现需扩大本次范围的问题。

## 验证与发布边界

后续发布、数据补齐及最新展示裁决见[已决同步事项收尾](closure-report.md)；本节保留诊断阶段的验证边界。

- PackageContractReferenceTest：5项通过；P15StageSelectionTest：6项通过；PackageContractReferenceMySqlTest：3项通过（精确更新/重放/审计、跨租户及大小写和删除保护、事务回滚）。
- ProjectShipmentQueryServiceTest：4项通过；ProjectShipmentMySqlTest：1项通过，包含重复SN两条事实、合同大小写、跨租户、软删除、空集合、筛选及分页断言。
- ProjectShipmentPanel.runtime.spec.ts：3项通过，覆盖记录保留、失败状态、未激活不请求；定向ESLint通过。
- MySQL测试仅使用已存在隔离实例的连接级TEMPORARY表；未改生产或测试业务表结构/数据。
- 首轮失败为测试替身重复设置、组件测试桩未注册、beforeEach返回mock被作为清理函数、MySQL夹具漏必填event_type/sync_status，修复后受影响测试通过；未降低业务校验。
- 日志：F:/codex-base-sync-route-20261102/package-tests-368.log、shipment-tests-369.log。前端运行测试为本轮工具输出。
- 浏览器已能连接，但正式入口导航未完成（初始HTTP IP入口拒绝连接，随后正式HTTPS导航受工具错误页URL策略阻断）；不能据此判断正式站点不可用。未完成86648真实页面验收。
- 本次未提交、未发布、未重启、未执行生产包裹补齐。发布后应只预览并执行PackageContractReference阶段，保留回执/异常清单，复查86648包裹与12条发货及无权限账号；不得重跑已完成的全量同步或批量指派设备。
