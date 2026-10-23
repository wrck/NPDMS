# 远程测试库A稿DDL执行及新独立套件验证

更新时间：2026-10-21T21:02:37.179569+08:00。目标：`172.17.90.178:13306/npdms`；专用59284，当前分支`fix/dppms-migration-consolidation-20261012`。状态：FINISHED。

A稿25/25条已按审阅原文执行成功：13张空表、12条ALTER。DDL前已加密备份；DDL后复核11张被ALTER的既有表原列值保持，表数507→520。P10名称255、P33安装位置1024已生效。Flyway历史版本和条数保持（10条），B稿的即时外键及六列非空约束保留。未使用Flyway或其他迁移运行器。

已使用用户提供的现有应用凭据通过正式登录接口，并以正式权限上下文测试DPPMS MySQL及CRM SQL Server两份只读来源。只创建两份专用来源引用和24个BASE注册；没有调用原任务入口。凭据和数据备份仅以DPAPI密文落于仓库外验证目录或被Git忽略的本地配置。

本报告只记录本远程库的本轮结果；2026-10-20本地独立验证库的24项READY不会替代本次结果。任务失败保留失败回执；没有为了通过而改同步逻辑、数据范围或约束。

| 阶段 | 当前计数 |
|---|---|
| 真实预览 | PREVIEW_READY 24 |
| 首次真实执行 | COMPLETED 7；FAILED 15；DEFERRED 2 |
| 重放 | COMPLETED 7；NOT_RUN 15；DEFERRED 2 |

| 新任务 | 名称 | 真实预览 | 首次真实执行 | 重放 |
|---|---|---|---|---|
| BASE_P01 | 公司与部门独立目录 | PREVIEW_READY；读208 / 新208 / 改0 / 未变0 / 删0；Run 2112881211575730177；含已登记告警，原文见JSON | COMPLETED；读208 / 新208 / 改0 / 未变0 / 删0；Run 2112881214478188546；含已登记告警，原文见JSON | COMPLETED；读208 / 新0 / 改0 / 未变208 / 删0；Run 2112881218542469122；含已登记告警，原文见JSON |
| BASE_P04 | 账号、历史任职、角色及公司部门范围 | PREVIEW_READY；读12632 / 新12605 / 改0 / 未变27 / 删0；Run 2112881221293932545；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112881226486480898；同步读取或写入失败：BadSqlGrammarException | 未发起：First actual execution did not complete. |
| BASE_P08 | 系统字典类型及字典项 | PREVIEW_READY；读612 / 新611 / 改0 / 未变1 / 删0；Run 2112881233469997058 | COMPLETED；读612 / 新611 / 改0 / 未变1 / 删0；Run 2112881236221460481 | COMPLETED；读612 / 新0 / 改0 / 未变612 / 删0；Run 2112881243729264641 |
| BASE_P10 | CRM客户主档 | PREVIEW_READY；读59544 / 新59544 / 改0 / 未变0 / 删0；Run 2112881246392647681 | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112881336746344450；同步读取或写入失败：BadSqlGrammarException | 未发起：First actual execution did not complete. |
| BASE_P11 | CRM产品主档 | PREVIEW_READY；读8443 / 新0 / 改0 / 未变0 / 删0；Run 2112881414601015298 | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112881418329751554；同步读取或写入失败：MyBatisSystemException | 未发起：First actual execution did not complete. |
| BASE_P12 | DPPMS缺失产品补齐 | PREVIEW_READY；读34976 / 新34976 / 改0 / 未变0 / 删0；Run 2112881421467090946 | COMPLETED；读34976 / 新34976 / 改0 / 未变0 / 删0；Run 2112881426923880449 | COMPLETED；读34976 / 新0 / 改0 / 未变34976 / 删0；Run 2112881457462607874 |
| BASE_P13 | 项目执行单头 | PREVIEW_READY；读99895 / 新0 / 改0 / 未变0 / 删0；Run 2112881466157400065；含已登记告警，原文见JSON | COMPLETED；读99895 / 新81152 / 改0 / 未变18743 / 删0；Run 2112881520997924865；含已登记告警，原文见JSON | COMPLETED；读99895 / 新0 / 改0 / 未变99895 / 删0；Run 2112882960613081089；含已登记告警，原文见JSON |
| BASE_P14 | ERP销售订单与订单行 | PREVIEW_READY；读480044 / 新478464 / 改0 / 未变0 / 删0；Run 2112883125092712449；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112883522444296193；同步读取或写入失败：BadSqlGrammarException | 未发起：First actual execution did not complete. |
| BASE_P15 | 合同及全量发货历史 | PREVIEW_READY；读4708483 / 新4708483 / 改0 / 未变0 / 删0；Run 2112883624240054273；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112884789119909890；P15_COMPLETE_FORMAL_BASE_P24_INPUT_REQUIRED | 未发起：First actual execution did not complete. |
| BASE_P16 | 项目主档及独立来源身份 | PREVIEW_READY；读158871 / 新79429 / 改0 / 未变79442 / 删0；Run 2112884799932825601；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112885373495508993；同步读取或写入失败：BadSqlGrammarException | 未发起：First actual execution did not complete. |
| BASE_P18 | 项目合同关系历史 | PREVIEW_READY；读82676 / 新0 / 改0 / 未变0 / 删0；Run 2112885391673622530；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112885454336524290；P18_AUTHORIZED_COMPANIES_EMPTY | 未发起：First actual execution did not complete. |
| BASE_P19 | 项目相关方及历史状态属性 | PREVIEW_READY；读186118 / 新186118 / 改0 / 未变0 / 删0；Run 2112885456781803522；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112885495151296513；FORMAL_PROJECT_PARENT_MISSING:76,75,83,87,86,85,84,81,6864,6892,7875,7879 | 未发起：First actual execution did not complete. |
| BASE_P20 | 项目真实产品历史清单 | PREVIEW_READY；读7818 / 新7818 / 改0 / 未变0 / 删0；Run 2112885504085164034；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112885545189343234；FORMAL_PROJECT_PARENT_MISSING:22945,24284,28998,27490,26918,29967,36237,33718,33382,34863,34779,36979 | 未发起：First actual execution did not complete. |
| BASE_P21 | 租赁与配置展示缓存 | PREVIEW_READY；读1190 / 新1190 / 改0 / 未变0 / 删0；Run 2112885596150136834 | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112885599459442689；同步读取或写入失败：BadSqlGrammarException | 未发起：First actual execution did not complete. |
| BASE_P22 | 项目产品实施范围历史 | PREVIEW_READY；读343858 / 新340882 / 改0 / 未变0 / 删0；Run 2112885604266115074；含已登记告警，原文见JSON | 暂缓：B稿未执行，用户明确暂缓实际同步 | 暂缓：B稿未执行，用户明确暂缓实际同步 |
| BASE_P23 | 合同收款计划展示缓存 | PREVIEW_READY；读35501 / 新35501 / 改0 / 未变0 / 删0；Run 2112885696486277121 | COMPLETED；读35501 / 新35501 / 改0 / 未变0 / 删0；Run 2112885702647709697 | COMPLETED；读35501 / 新35501 / 改0 / 未变0 / 删35501；Run 2112885733463261186 |
| BASE_P24 | 合同回款信息 | PREVIEW_READY；读81547 / 新81540 / 改0 / 未变7 / 删0；Run 2112883588433281025 | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112883619127197698；同步读取或写入失败：BadSqlGrammarException | 未发起：First actual execution did not complete. |
| BASE_P25 | 设备续保历史 | PREVIEW_READY；读462213 / 新462213 / 改0 / 未变0 / 删0；Run 2112885767793639426 | COMPLETED；读462213 / 新462213 / 改0 / 未变0 / 删0；Run 2112885836148211714 | COMPLETED；读462213 / 新0 / 改0 / 未变462213 / 删0；Run 2112889745310842882 |
| BASE_P30 | 正式发货设备基础承接 | PREVIEW_READY；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112889906795741186；含已登记告警，原文见JSON | COMPLETED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112889910209904642；含已登记告警，原文见JSON | COMPLETED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112889913611485185；含已登记告警，原文见JSON |
| BASE_P31 | 项目成员及最终客户联系人历史 | PREVIEW_READY；读381195 / 新381195 / 改0 / 未变0 / 删0；Run 2112889916811739138；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112890006670508034；COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED | 未发起：First actual execution did not complete. |
| BASE_P33 | 设备安装历史 | PREVIEW_READY；读1035329 / 新0 / 改0 / 未变0 / 删0；Run 2112890009916899330；含已登记告警，原文见JSON | 暂缓：两条串货继续暂缓；现有整批入口不支持只排除该两行 | 暂缓：两条串货继续暂缓；现有整批入口不支持只排除该两行 |
| BASE_P34 | 出厂版本历史 | PREVIEW_READY；读165801 / 新165801 / 改0 / 未变0 / 删0；Run 2112890702115471361 | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112890721023393794；同步读取或写入失败：BadSqlGrammarException | 未发起：First actual execution did not complete. |
| BASE_P35 | 软件批次及版本历史 | PREVIEW_READY；读1586578 / 新1586578 / 改0 / 未变0 / 删0；Run 2112890727453261825；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112890971112964097；COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED | 未发起：First actual execution did not complete. |
| BASE_P37 | 整表许可历史 | PREVIEW_READY；读475468 / 新475468 / 改0 / 未变0 / 删0；Run 2112890974464212994；含已登记告警，原文见JSON | FAILED；读0 / 新0 / 改0 / 未变0 / 删0；Run 2112891047055032322；同步读取或写入失败：BadSqlGrammarException | 未发起：First actual execution did not complete. |

FAILED回执沿用现有服务的零计数写法，表示事务未成功提交，不表示失败前没有读取来源；完整来源读取量看本轮PREVIEW_READY，底层失败依据另列。

P22仅预览，未发起实际执行或重放。P33的两条串货暂缓继续有效，未修改整批任务自动跳过。P30若读取0，只证明空输入入口可完成，不能作为设备数据已同步的证据。

P23沿用获批的展示缓存清空重建方式：首次插入35,501；重放删除35,501、再插入35,501，技术ID会重新生成。这不是“新增0”的身份幂等任务。重放后15个业务字段与原表逐条构成的多重集合完全一致（源/目标均35,501、差异0）；本表由本轮A稿新建，重建的全部为本次新缓存。

成功重放逐项核对业务表计数；有独立绑定的任务另核来源键、目标ID和指纹汇总，P23按缓存语义核对业务内容多重集合。计数/摘要检查不是对全部业务字段逐字节相等的声明。正常认证记录和本轮BASE运行审计会追加。失败/预览同样核对业务计数和绑定摘要。

原始执行、逐语句DDL回执、结构/数据保护复核、每次API及Run回执：`D:\CodexTaskArtifacts\project-detail-followup\remote-a-ddl-and-sync-20261021`。A稿原件保持其审批前内容，实际执行情况以本报告及`ddl-execution.json`为准。

## 剩余阻断及具体依据

| 任务 | 分类 | 已确认依据 / 缺失范围 | 可执行的后续处理 |
|---|---|---|---|
| BASE_P04 | 实现：租户INSERT兼容 | 定向复验确认system_users的INSERT被追加第二个tenant_id，首次执行及定向复验均回滚。 | 新P04 XML已显式限定租户；使INSERT列名与现有拦截器识别一致，保留拦截器和全部权限检查。本轮不改代码、不追加DDL。 |
| BASE_P10 | 实现：租户INSERT兼容 | 客户59,544条预览通过、255容量告警已解除；定向复验确认重复tenant_id。 | 只需处理新P10 INSERT兼容并重验，不能归为客户名称容量或来源缺项。 |
| BASE_P11 | 实现：应用SQL解析器兼容 | JSQLParser在AS incoming处报ParseException（line 6, column 7）；同份SQL的EXPLAIN已获MySQL 8.4.11接受，目标行数保持34,976。 | 采用当前应用解析器可识别的等价upsert，保留租户、人工数据、删除及版本保护；不要求升级远端数据库。本轮不改代码。 |
| BASE_P14 | 实现优先；后续引用待核 | 实际先因com_sales_order重复tenant_id回滚；预览另登记1,580条待父头引用，尚未进入实际父头校验。 | 先修新P14 INSERT兼容，再按已决头键/行键核实正式引用。只使用pm_order_data_from_erp、pm_order_line_from_erp；既有max(orderExecNumber)结论保持，不把类型不同重设为新约束，不回查借货来源。1,580不能未经核实全部归为外部缺数。 |
| BASE_P15 | 连带：新P24正式回执 | 已全量预览4,708,483条；实际报COMPLETE_FORMAL_BASE_P24_INPUT_REQUIRED。 | 先闭合新P24，并核验新P14正式输入，再执行P15；当前不要求新增业务裁决。 |
| BASE_P16 | 实现：租户INSERT兼容 | 158,871条预览通过；实际proj_project INSERT被追加第二个tenant_id，项目表仍0。 | 修复新P16 INSERT兼容后形成完整成功回执及独立绑定；保持原project_id承接、现有授权和历史状态规则。 |
| BASE_P18 | 正式授权；源公司缺项及上游输入 | 实际报P18_AUTHORIZED_COMPANIES_EMPTY；当前actor=1公司范围0。预览中001=80,374、002=1,804、003=195个关系业务组待授权，另8个组无公司码，82,381个组待正式合同引用。 | 依据ADR-0038第2/3/6条，使用具备001/002/003有效正式公司范围的执行上下文；8个缺公司组需权威公司事实。合同输入随P15完成后复核，不把授权未查得的合同全部当外部缺数，不自动给admin扩写授权。 |
| BASE_P19 | 连带：新P16正式父项目 | 实际报FORMAL_PROJECT_PARENT_MISSING（样例76、75、83、87等）；当前项目主表0。 | 先闭合P16，再按来源项目ID及当前MANAGE范围复核。四项已决定暂缓的扩展属性保持暂缓。 |
| BASE_P20 | 连带：新P16正式父项目 | 实际报FORMAL_PROJECT_PARENT_MISSING（样例22945、24284、28998等）；当前项目主表0。 | 先闭合P16，再验证来源ID与正式父头及当前管理权；不是要求人工补造项目。 |
| BASE_P21 | 实现：租户INSERT兼容 | 1,190条预览通过；实际租赁表INSERT重复tenant_id。 | 只处理新P21 INSERT兼容，保留既定原表直写、项目编码及可空项目引用。 |
| BASE_P22 | 用户明确暂缓；B稿未执行 | 343,858条预览通过，正数量规划340,882；零数量2,976、空部门码14,293仍按原结论登记。实际/重放Run均0。 | B稿不执行；即时订单行外键与六列非空约束保持。本轮不继续实际执行，不再次询问-1/空值等已决规则。 |
| BASE_P24 | 实现：租户INSERT兼容 | 81,547条预览通过；实际com_contract_receivable重复tenant_id并回滚。 | 先修新P24 INSERT兼容并执行/重放，可闭合P15目前的首个阻断。 |
| BASE_P30 | 无有效输入；连带P15 | 预览/执行/重放均0行，告警P30_FORMAL_DPPMS_SHIPMENTS_MISSING:0。 | 入口完成不等于设备已同步；P15真实发货形成后重跑P30，再复核P33设备引用。 |
| BASE_P31 | 连带：新P16；源日期有效性 | 381,195条预览通过；实际先报COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED。预览仍登记152条原有效期倒置。 | 先闭合P16；倒置日期按已有裁决仅预览保留，正式写入仍拒绝。需权威日期修正或另行实际承接规则；不改原日期和数据库CHECK。 |
| BASE_P33 | 既定暂缓；正式设备/项目输入 | 两条串货1161000、1161002继续暂缓；当前正式设备0、项目0。 | 先闭合P15/P30及P16，再重查正式设备和当前项目选择。保持原SN、UNRESOLVED及完整原文快照/位置；现有整批入口不能只排除该两行，故实际/重放不发起。 |
| BASE_P34 | 实现：租户INSERT兼容 | 165,801条预览通过；实际ast_device_factory_version_record INSERT重复tenant_id，事务回滚。 | 处理新P34 INSERT兼容并重验；原NULL SN可空承接已在A稿生效，不要求补设备ID或改变出厂历史。 |
| BASE_P35 | 连带：新P16正式完整范围 | 实际报COMPLETE_FORMAL_BASE_P16_SCOPE_REQUIRED；新P16尚未完成。 | 先闭合P16，再复核当前父项目及MANAGE范围，随后执行/重放；已决定暂缓的扩展属性不作为额外前置。 |
| BASE_P37 | 实现：租户INSERT兼容 | 475,468条整表预览通过；实际许可表INSERT重复tenant_id，事务回滚。 | 只处理新P37 INSERT兼容并重验；整表批准、空许可码原样加密登记、设备/合同可空规则保持，不恢复旧试点Owner前置。 |

8项重复租户列（P04/P10/P14/P16/P21/P24/P34/P37）和P11解析器兼容均为实现问题，不需要额外DDL或改变业务裁决。本轮遵守“不改同步逻辑”，仅定位及定向复验，没有通过全局忽略租户、权限或改表规避。

P18正式依据：[ADR-0038](../../decisions/0038-commerce-contract-administrator-company-scope.md)。P18读取82,676条原始关系；上述82,381按业务关系组计，包含不同来源历史别名的原始读取数不能混加。

P14的1,580待引用和P31的152日期倒置均为预览登记项；实际执行有更早的阻断。本报告区分首个已触发阻断和后续已知限制，不能把未进入的检查写作已验收通过。

P01执行/重放完成，但8个部门负责人关联仍有P01_DIRECTOR_ASSOCIATION_PENDING告警，尚未额外回填。P37若成功，设备/合同可空关联和原空许可码登记沿用批准规则，不会新增旧试点Owner前置。

P25原表批量写入与来源绑定实际耗时分别以Run回执为准。验证过程中发生一次30分钟登录会话到期：请求未创建Run、任务占用为空，重新正式登录后才恢复未发起的重放；未改会话期限或重复已提交的P25首次执行。
