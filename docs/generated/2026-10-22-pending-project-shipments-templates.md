# 待指派项目发货对账与五类模板匹配

2026-10-22，目标59191、租户1、npdms_domain_test，来源为已配置DPPMS。用户要求仅处理数据、不修改代码；后续明确批准恢复59191并继续。未修改应用源码、表结构、生命周期或人员指派。

## 当前结果

完整读取6697个项目，筛选ACTIVE且服务经理或项目经理尚未指派，共246个。201个有主档合同号或正式有效合同关系，合并208个合同号；45个缺合同不能据此关联。对应原库发货覆盖177个项目，24个项目的合同在原库无发货记录。

通过来源只读事务按合同号查询fb_contract→fb_shipment→fb_shipment_barcode，再按来源系统DPPMS和来源记录主键逐条核对ast_device_shipment：原库21414条，目标21414条，缺失0、SN或合同号差异0，覆盖183个合同号。未重复插入或改写既有发货事实。仅核对来源主键、SN、合同号，不能将此结果解释为设备归属、订单行或全部物流字段已完成对账。证据source-key-reconciliation.json、source-shipment-keys.json、project-shipment-reconciliation.json。

五类模板以服务端已发布规则预演匹配，限定用户指定五类：普通直签140、工程直签10、普通非直签67、工程非直签2、非直签原厂督导7，共226个；20个无匹配，未猜测或修改业务属性。最新发布修订依次为993009900073至993009900077，版本均v2。结果five-template-matches.json及match-evaluations.json。

22个已有有效计划的项目通过正式计划草稿、模板绑定、预览API办理：17个预览无问题后正式应用成功，逐条数据库回读确认有效计划引用最新五类v2修订；项目生命周期和人员指派状态均与操作前一致。项目992203060216在真实浏览器详情中显示“当前执行：工程类－直签 · 计划 v2”“当前模板：工程类－直签 · v2”，刷新后保持，仍待指派服务经理。列表保留原创建模板元数据，不以列表旧编号否定当前有效计划，也未覆盖初始绑定历史。证据template-binding-results.json、effective-template-readback.json、project-state-after.json、browser-effective-plan.txt、browser-effective-plan-reopened.txt。

5个已有计划项目因已开始节点或交付件办理历史保护而未应用，保留换版草稿与原有效计划：992203060017、992203060016、992203060009、992203060007、992203060003。具体保护问题见template-binding-results.json。

另外204个虽已匹配，但缺冻结模板和初始计划，逐条查询确认计划数量为0。现有换版接口依赖有效计划；初始化服务仅由创建新项目流程调用，未找到可为既有迁移项目安全初始化的公开入口。未通过直接写模板ID伪造运行计划；在“不改代码”约束下这部分未完成。其余20个无匹配项目也未绑定。证据missing-initial-plan.json、summary.json。

## 服务恢复及预演记录

此前发货预演暴露旧配置转换失效、容量限制，以及目标目录引用实库不存在的warranty_flag、item_code列。仅在本次新增停用的一次性同步任务2113127472103014401中调整配置，未改既有任务或代码。

预演2113128082718179330在读取21414条后因后端JVM原生内存不足中断，日志记录Native memory allocation (mmap) failed to map 8388608 bytes及G1 virtual space。用户明确批准后，使用scripts/worktree-backend.ps1，传入.run/build/releases/3a78ad9b-fc80-4888-b42b-6568c0b15146/manifest.json、-ApprovedRestart和-StartIfOffline，以原冻结产物恢复local,dac，PID59524，正式接口和浏览器登录态均恢复。未停止或启动DAC。

系统恢复后将中断预演标记为FAILED，提示运行进程中断且可关联重试。所有发货运行均为preview=true，未执行正式发货同步；逐条对账证明无缺失后未再重跑整批预演。预演CREATED/UPDATED是预演目标动作，不能当成实际新增发货数量。新增任务保持停用、无活动运行。

本次只对17个生效项目之一进行真实浏览器抽查，不宣称其余项目均完成浏览器验收。证据目录均为.run/pending-shipment-20261022/，辅助脚本只用于本次数据操作，不属于应用功能代码。

## 后续指令：只允许本次新增五类正式模板

用户再次明确只选择最近新增的五个正式项目模板，即993009900032～993009900036，而非验收复制模板。通过正式disable API停用其余11个ACTIVE模板，回读验证ACTIVE集合精确为这5个；未删除任何模板、发布修订或历史计划。停用只阻新匹配。

重新对租户1全部ACTIVE且未绑定模板项目匹配：226个中204个MATCHED、22个NO_MATCH；此前待指派范围中的204个匹配项目均包含在内。MATCHED项目缺初始计划的问题仍存在，尚未写入绑定。维持此前不修改代码约束，已请求用户确认是否允许补充既有项目初始化入口后再实际分配。不得将这204个匹配结果当成分配成功。

证据：.run/five-formal-template-only-20261022/templates-before.json、disabled.json、templates-after.json、unbound-project-match-results.json。

## 通用既有项目初始化入口（已授权实现，待本次部署重启批准）

用户明确允许补充入口并完成204个项目分配，并追加“不能硬编码”。已新增 `POST /api/v1/pms/projects/{projectId}/plan/initialize`，不包含模板/项目ID、名称、数量白名单；根据启用模板配置和发布规则动态唯一匹配，客户端修订须与重新求值结果一致。沿用计划管理权限及项目编辑范围，项目行锁、预期版本、历史实例保护和平台命令幂等/审计；复制增强既有实例化顺序，复用TemplateInstantiator、执行契约工厂、PlanInitialization及正常阶段准入服务，旧创建路径原样保留。API契约已补入docs/design/10-api-design.md。

定向测试37项通过（新增入口14项，既有计划/快照23项），覆盖范围拒绝、跨租户、已关闭、已有计划/模板/孤立运行历史、版本过期、无匹配、过期修订、停用模板、幂等重放、成功初始化、CAS失败和运行失败传播。MySQL回滚检查：错误租户0、过期版本0、首次绑定1、重复0，回滚后主档完全恢复。204个目标项目的阶段/任务/里程碑/门禁/旧交付件实例计数均为0。

当前工作树统一构建入口完成后端构建，冻结产物`.run/build/releases/efe08ed1-b7d3-4b08-83c2-768b95d8843f/manifest.json`；22项已部署修复保护及ValidateOnly预检通过。未提交或推送。尚未部署新入口，未执行204个项目的实际初始化，等待本次59191部署重启批准。

证据：.run/five-formal-template-only-20261022/tests.log、mapper-rollback-check.json、build.log、preflight.log；批量客户端assign-matched.py仅调用正式认证接口并逐项回读有效计划，遇失败停止。

## 部署与204个项目实际初始化完成

用户明确批准本次部署重启。通过统一入口部署冻结包efe08ed1-b7d3-4b08-83c2-768b95d8843f，59191/local,dac健康，PID64308。204个MATCHED项目均已通过正式认证初始化接口分配成功，0失败；分布普通直签129、普通非直签67、督导7、工程非直签1。只分配现有启用且唯一匹配的发布修订，代码不含模板/项目白名单。

逐项核对204份有效计划与模板引用、1421个阶段及阶段契约、3032个任务及任务契约、1020个门禁、4453个执行轮次、1611项PLT模板冻结交付要求，全部与各项目冻结快照数量一致；快照里程碑数量均为0，未补造。项目生命周期和人员指派状态保持。首项目相同幂等键重放结果相同、实例数量不增加；不同键重复初始化和另一项目不匹配模板请求均拒绝，后者主档保持未绑定直至正式分配。浏览器抽查普通非直签与督导两种项目，当前绑定均为v2、初始计划为v1，刷新后保持且仍待指派。其余22个未绑定ACTIVE项目未命中这5类规则，未强配。原5个受历史保护项目仍保留原有效计划。

证据：deploy.log、initialization-results.json、assignment.log、assignment-verification.json、live-initialization-checks.json、browser-initialized-project-reopened.txt、browser-supervision-initialized.txt，均在.run/five-formal-template-only-20261022/。

## 销售订单行与发货匹配缺口诊断

用户追问模板分配后为何销售订单行与发货仍匹配不到。只读核对本次204项目：180个有关联合同及订单，只有71个能通过合同找到目标订单行；有效交付范围仅5行、属于1个项目；ast_device中归属这204项目的设备为0。按这些项目正式关联合同筛出的21401条发货记录，order_line_id全部为空。

明确反例：项目2109733108716273665、合同31320240109A05、订单SO077129，目标订单头存在而订单行为0；原库pm_order_line_from_erp中同D365/01/lineType=0的该订单有10个不同业务行，各仅1条来源记录。故至少该例属于真实订单行未承接，不是原库没有数据。

页面loadProjectEquipmentList按项目设备及项目交付范围归集，再按已分配订单行补数量；发货项目查询也要求ast_device.project_id和shipment_time。模板分配只初始化PROJ运行计划，不创建商务订单行、交付范围或设备归属。原“21414条发货已齐”只证明最初246个项目合同筛选下的来源键/SN/合同号记录存在，不证明订单行与项目关联闭环；不能据此认定用户可见发货完整。

本轮仅诊断，未为商务或资产模块另做写入。后续修复须沿原库项目、订单业务键和SN映射承接订单行、交付范围、发货订单行引用及设备项目关系，不以模板绑定替代它们。诊断证据commerce-diagnosis.json、missing-order-line-example.json、missing-order-line-source-evidence.json。
