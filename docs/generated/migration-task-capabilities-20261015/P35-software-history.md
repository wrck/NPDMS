# P35 批次及版本历史离线适配

2026-10-16。DPPMS_SOFTWARE_HISTORY以一份父批次+原logId子版本组合查询承接原9列和28列，保留两类独立原主键、正式表和血缘。无独立P36回源任务，不读pm_project。根项目范围由P16完整发布前置筛选，读取器补齐的原类型/完整编码随本组传入父子；父子原projectId必须相同，INT再次向P16和Project Owner核验并锁定正式项目，不能依赖已经不在运行定义中的临时publishedProjects列表。

AST组合Owner共用原字段及历史规则，正式阶段先写/核对父批次，再映射原logId给子版本；原审计、当前设备独立历史关联、全部正式字段和不可变八属性扩展保护保留。新增独立只读Preview回执，临时负ID只在本轮关联，不传入正式ExternalTargetMapping或持久化绑定。预览不插入、写扩展或发布定义，不用回滚真实写入冒充预览。原软件历史及批准纠正入口沿原正数正式回执工作。不会改变设备当前软件版本或发送升级完成事件。

验证 targeted-024530.log：原历史Owner含新增预览16项、原批准纠正8项、扩展预览3项、INT入口4项，合计31项通过。覆盖父子来源/绑定、缺父和跨项目、虚构临时父拒绝、原字段变化、扩展定义/值、空审计和旧路径回归。首轮发现正式目标DTO不允许临时负ID，已改用独立预览结果并完成回归。MySQL组合JSON查询、实际平台定义及端到端故障回滚尚未做真实环境验收。

只读核对 npdms_reverify_20261014：SoftwareVersionRecord已发布扩展定义为0；证据在外部 p35-extension-definitions-readonly.json。候选extensionDefinitionId保持空，实际运行须明确阻断，不能填测试ID，也未发布定义。候选 current-definitions/P35.json、candidate-commands/P35.json 已由正式工厂导出，保持未保存和停用。

代码及离线适配累计23/24；定义前置未补齐不等于可运行配置。本次没有保存配置、业务预览、真实同步、DDL、服务操作、浏览器验收、提交或推送。
