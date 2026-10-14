# 项目992203060198工前准备历史任务视图修复

## 故障与边界

需求方要求在当前对话继续修复EXECUTION_CONTEXT_STALE和VIEW_NOT_BOUND，保留历史与校验。正式API复现PRE_SURVEY、PRE_REQUIREMENT、PRE_DURATION三个已完成任务均返回上述错误；真实浏览器在工勘任务也显示两条错误。

项目当前有效计划2104870198458802229；上述三个当前标记的已完成轮次保留计划2104870198458802202，这是计划修订时保留已结束历史的合法状态。ProjectOperationContextResolver却要求所有轮次都属于最新计划，在解析原冻结绑定之前返回STALE，导致能力查询收到空绑定并连带报VIEW_NOT_BOUND。当前联系人轮次使用最新计划，未发生同样的过期问题；联系人PAGE本身没有BusinessView不等于三个业务任务的绑定丢失。

## 修复

只修改执行上下文读取器：当前节点终态与轮次DONE/TERMINATED、endedAt共同成立时，允许读取其原计划的冻结业务绑定及展示配置；原计划必须存在、同租户、同项目且为EFFECTIVE/SUPERSEDED。原授权、唯一当前轮次、节点标识和冻结节点匹配继续校验。历史路径不返回可写执行上下文，permitted=false；携带expected版本向量的请求返回EXECUTION_VERSION_CONFLICT。运行轮次与未结束轮次仍须属于最新计划，不放宽写命令或Owner校验。

未迁移轮次planVersionId、未覆盖历史、未修改模板/项目计划、未重新绑定业务对象或直接写状态；VIEW_NOT_BOUND通过恢复读取原冻结绑定解决，不隐藏错误或伪造视图。

## 验证

沿用固定JDK25/Maven、工作树依赖库和独占锁，33项定向测试通过：新读取器6项、能力查询4项、受控执行器12项、操作执行守卫1项、计划生效10项。覆盖历史只读与原证据不变、运行/未结束轮次仍过期、跨租户/跨项目/草稿计划拒绝、expected旧写上下文拒绝、缺失原计划或冻结节点不回退最新计划、授权失败先于历史读取。日志.run/history-view-tests.log。

部署前通过正式历史API保存25条轮次及计划，部署后完整比较plans/rounds完全相同；基线与结果分别为.run/project-198-history-view-before.json、.run/project-198-history-view-after.json。三个历史任务能力接口均恢复原注册，presentation.status=AVAILABLE、execution=null、actions=[]；不再返回EXECUTION_CONTEXT_STALE或VIEW_NOT_BOUND。原BusinessView ID分别为2101604731582791681、2101606301384949762、2102312815597113345。

真实浏览器验收进一步发现需求分析适配器将只读任务没有可写执行上下文误判为禁止装载。registry.ts仅在明确readonly=true且taskId有效时允许缺少taskExecution；可写路径、外项目执行上下文、双执行上下文及注册Owner/实体/冻结修订校验继续拒绝。已有BusinessView.runtime.spec.ts增加历史只读回归，整套42项通过。前端19191为现有开发服务，本次修改通过其热更新生效，未重启前端。

浏览器实际打开工勘、需求分析、施工计划并切换/刷新：工勘原两条记录保留且只有详情；需求分析显示有效完成V1、冻结正文和附件，输入框及多选框禁用，无创建/保存/完成操作；施工计划显示已有计划，新建调整版本禁用。三任务页面已消除本次两项错误。阶段S1入口仍出现独立GRAPH_TARGET_STALE提示，不列为本次已修复；联系人PAGE无BusinessView属于另一接入路径，本次未改动联系人状态或绑定。

## 构建与部署

多次官方构建先遇到并行割接编译错误，修复后最新scripts/worktree-build.ps1在2026-10-12 18:52:26完成全模块BUILD SUCCESS，但冻结前源码一致性检查检测到并行修改而拒绝发布，日志.run/history-view-backend-build-release.log。需求方随后明确授权“编译通过即可部署，不需要等并行任务完成”。据此只对本次完成的编译产物作一次性冻结：持有同一工作树独占锁，核对target包时间与成功构建时间一致，确认编译包内历史读取修复存在，并通过原22项已部署修复基线校验；使用官方worktree-artifact.py冻结，未修改严格默认构建脚本或修复校验。source字段明确记录并行源码授权及成功构建时间，不声称产物与后续工作树源码一致。

冻结清单.run/build/releases/45bdbd19-82a6-4875-a4db-c65523034752/manifest.json，SHA256 dec41173cb65703bdb6091eec70dc93b86d4b293018feb437f5d0a3b3a1c40dc。使用scripts/worktree-backend.ps1 -Manifest ... -ApprovedRestart执行用户已授权部署，保留旧包备份、运行包身份校验与local,dac配置；新PID35740，59191 backend healthy。48182未启停，部署后独立检测dac healthy。

本次修复与指定三个任务查看路径已验证；并行割接业务闭环未在本次验收。未提交或推送Git，保留全部并行修改。
