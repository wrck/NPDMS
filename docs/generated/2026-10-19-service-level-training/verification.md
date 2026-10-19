# 割接服务等级与培训、配置办理验证

日期：2026-10-19。本文为本次有界修复的结果记录，不替代业务规格。依据需求方确认的“未维护手选、已维护只读”，并按随后明确的“保存后不跟随客户服务等级变化”收敛。

## 实现边界

- CUT 创建时从现有 CUS 维护记录读取唯一当前有效等级。未维护时允许显式选择现有 `pms_service_level` 字典，不设置固定默认值；已维护等级只读。
- 保存后沿用评估中的来源和手选值，不增加 CUS 变化重验或刷新重存要求；旧 `UNAVAILABLE` 草稿可首次补齐。独立割接原有的客户缺失/参考提供方不可用边界保留，不借本次新增全局准入限制。
- 手选值、操作者、时间及来源记录 ID 使用明确列，来源记录不冒充不可变修订。P5 从所引用的不可变评估读取有效等级；旧 JSON 原件和已提交历史不改写。
- 新可选字段为空时不改变旧保存意图的序列化摘要；真实 Spring mapper 回归验证旧完成意图可重放、手选值变更仍冲突。
- 培训保留原有默认客户确认模板及内置打印版式；配置、培训人员查询失败时可显式选择当前登录人或保留已有人员。未扩大角色或模板查询权限。

## 真实浏览器结果

隔离环境：前端 `18199`、后端 `59299`、数据库 `npdms_fullcycle_v4_3d5c63bd453d`。合成客户 `UIREG-20261019`，项目 3。没有将测试结果表述为 `59191` 部署结果。

| 场景 | 实际动作和结果 | 证据 |
|---|---|---|
| 未维护手选 | 任务 `2112028037836861440` 手选银牌保存；随后 CUS 初始化金牌有效样例，原任务仍通过原生“下一步”进入 P4，评估保留银牌及选择人 3 | `manual-create-save-ui-events.json`、`manual-after-cus-gold-current.png`、`cut-service-level-final-readonly.tsv` |
| 已维护只读及保存后稳定 | 任务 `2112029139470807040` 带入金牌，无手选控件；保存后通过原 CUS update API 将测试记录改为铜牌，任务提交 HTTP/code=200/0，进入 P4，评估仍为金牌，来源 ID 精确为字符串 `970000000000501001` | `maintained-gold-saved-then-cus-bronze-submit.json`、`maintained-gold-readonly-stable-verdict.json`、`maintained-gold-saved-p2-stable.png` |
| 培训办理 | 培训 2 保存并重开，使用本人、默认确认模板及内置打印版式；生成本地模拟客户链接，模拟客户确认后后台显示已确认 | `training-native-create.json`、`training-native-issue-safe.json`、`training-native-customer-confirm.json`、`training-admin-confirmed.png` |
| 配置办理 | 配置 4 查询其他人员受限时显式选择本人，保存、重开、开始、完成均成功；既有配置 2、3 未改写 | `configuration-native-create.json`、`configuration-native-start-complete.json`、`configuration-completed.png`、`business-final-readonly.tsv` |

上述浏览器证据根目录：[本地验收产物](../../../.run/build/source-v4-fullcycle-20261018/service-level-training-20261019/)。首次手选提交的响应监听路径写错而未捕获当次响应；以实际原生操作后的 P4 页面和持久化终态证明成功，不虚称该次响应被捕获。已维护创建的早期截图仍在加载，不作为通过证据；使用稳定后重开 P2 截图。

客户等级旧维护接口没有生效动作。本轮不扩展其生命周期：已维护消费分支使用独立标记的隔离初始化夹具，仅向精确匹配的合成客户新插入一条记录，无旧行状态更新；之后等级值变化走原正式 update API。该夹具不代表客户维护业务已具备“生效”闭环。

两台验收设备通过既有正式同步配置各预览一行后应用，再经真实业务归属/安装；没有通过改旧割接任务或 SQL 伪造设备发货状态获得测试条件。客户确认仅为本地模拟，没有发送外部消息。

## 验证与运行产物

- 后端 55 项有效定向用例：CUS 当前事实 12；CUT 控制器合同 3、请求解析 3、审批快照 5、应用服务 16、原独立接入规则 11、查询 5。不重复计算重跑。
- 前端 50 项有效定向用例：CUT 创建 24、详情 7、等级面板 4、既有共享交互 8；配置/培训回退 7。
- 完整后端构建通过，22 条既有修复产物基线通过。最后一次代码变化只为新增空字段的幂等摘要兼容，受影响应用服务 16 项已重跑通过。
- V501 经既有 Compose/Flyway 入口迁移、validate、再次 migrate 均成功；重复执行无额外迁移。新增列和两项历史保护触发器存在。
- 对本次新 SUBMITTED 评估的四个新增字段执行事务内负向更新，SQLSTATE 45000 拒绝；回滚并读回确认完全一致。
- 初次功能验收运行包为 `0ca23143-feb0-4efe-8601-d32805b9129b`；经需求方本次明确批准，最终幂等兼容包 `a78139b9-d1cb-49cd-8d07-2313876fcef6` 已部署至 59299，PID `47756`。真实浏览器重新加载并从列表复开两条 P2 历史，银牌/金牌保持、无手选控件且已提交只读；当前 CUS 铜牌未影响保存快照。运行和两次实际 GET code=0 的证据见 `owned-backend-service-level-idempotency-restart-evidence.json`、`final-runtime-two-p2-browser-verdict.json`，截图为 `final-runtime-manual-silver-p2.png` 与 `final-runtime-maintained-gold-p2.png`。
- 两次批准重启均保留数据库、凭据、Redis；59191、48182、18199 进程创建时间及 PID 均未变化。未提交或推送 Git。

后端日志位于上述验收产物根目录：`backend-verified-build.log`、`backend-digest-compatible-build.log`。前端日志实际位于 [W 目录](../../../.run/build/source-v4-fullcycle-20261018/W/service-level-training-20261019/)，使用 `vitest-cutover-unavailable-preserve-intake.log`、`vitest-cutover-creation-header-level.log`、`vitest-cutover-service-level.log` 的未受后续修改影响用例以及 `vitest-personnel-template-fallback.log`。

## 后续部署至 59191

需求方随后明确要求“将这次修复部署到59191，保留现有数据和配置，完成后用真实浏览器复核”。已按该次授权通过 `scripts/worktree-backend.ps1` 部署上述最终冻结包，运行 PID 为 `44236`，配置仍为 `local,dac`。实际运行 JAR 与冻结清单一致，本机及 `10.210.0.11` 的健康状态均为 UP。

- 保留原 `npdms_domain_test`、Redis 24379、数据库凭据、DAC 参数及 Java 参数；`.env`、DAC 环境文件和 Compose 文件未修改。48182、59299、18199 原进程及创建时间未改变。
- 原数据库停在 V497。本次通过 Compose/Flyway 仅挂载和应用 V501，validate 和重复 migrate 成功；未带入 V498–V500 的菜单、角色或字典变更。旧迁移在此限定挂载中标记 Missing 并忽略，不宣称完成了全部历史迁移文件的校验。
- 受影响的两张割接表已备份为当前 Windows 用户可解密的 DPAPI 加密文件，未落盘明文备份。迁移及部署后，22 条割接任务、27 条评估的原有列以及角色、用户角色、角色菜单、菜单、配置、字典逐行摘要均与变更前一致；两项新增历史保护触发器存在。
- 部署入口已退出，外围 Python 输出捕获因子进程继承管道未返回；在独立验证服务就绪后仅结束该外围进程，未再次停止后端。运行包、设置与数据的后验核对均通过。

部署证据：[59191 部署记录](../../../.run/build/deploy-59191-service-level-20261019/deployment-evidence.json)。加密备份已在内存中解密并核对摘要，确认可读，未生成明文文件。

真实浏览器通过 `http://10.210.0.11:19191` 原生登录，刷新加载本次 CUT、培训、配置前端代码，实际请求均发往 59191：

- 复开真实项目 `992203060197`，培训和配置表单正常打开，读取接口 HTTP 200/code 0；培训沿用原有客户确认模板修订 2、打印表单修订 2。表单均取消，未提交业务写入。
- 从原生割接列表复开既有任务 `2111084153208291328` 的 P2 历史，GET HTTP 200/code 0；保持只读，无手选控件，原历史“未获取”保留，不回填或覆盖。
- 最后一次页面重新加载及历史 P2 导航中，捕获的 console.error/pageerror 为零。不会把此前未加监听的点击表述为全部控制台零错误。
- 复核范围为部署后的真实页面与原数据读取，不重复向原环境新建和提交业务。完整业务写入闭环的证据仍为上文隔离环境验收。
- 记录展示限制：培训表单默认工程师显示保留的用户 ID `1`，该次用户分页结果未解析出姓名；未将其误报为保存失败，也未改用户或角色数据。

浏览器证据：[59191 复核记录](../../../.run/build/source-v4-fullcycle-20261018/deployment-59191-readonly-20261019/deployed-final-browser-verdict.json)、[培训表单](../../../.run/build/source-v4-fullcycle-20261018/deployment-59191-readonly-20261019/deployed-training-readonly-form.png)。本轮未提交或推送 Git。

保护验证见 `submitted-assessment-trigger-negative.json`、`v501-migration-result.json`。最终部署后的 `final-runtime-secret-scan.json` 检查 1562 个文本产物，实际运行凭据及 Bearer/JWT/bcrypt 模式均零命中。旧请求幂等重放以真实 Spring mapper 定向回归为验证边界，未伪造历史完成事实或冒称浏览器重放了无法恢复原键的旧请求。
