# 项目992203060198 S1入口修复与工期任务版本核对

## S1入口

需求方要求继续排查GRAPH_TARGET_STALE，保留历史和校验。前一轮真实浏览器已复现：联系人获授权返工后S1第2轮运行，S2/S3已完成历史保留；ProjectStageReadinessService使用旧相邻阶段推进查询，ProjectRuntimeGraphResolver拒绝非PENDING目标并抛GRAPH_TARGET_STALE，查询异常直接形成页面弹窗。不能通过重置S2/S3状态修复。

本轮2026-10-12只读核对时，联系人及S1已由现有自动完成链完成，项目version21/currentStage=S4；真实浏览器S1入口显示4/4完成，无原异常。正式readiness API正常返回S4→S5、advanceAllowed=false、STAGE_COMPLETION_UNSATISFIED。本轮没有创建联系人、完成任务、推进阶段或进行数据库业务写入，不能将当前自动完成后的正常页面算作新补丁已部署验收。

代码修复仅在已授权的readiness查询中捕获精确PROJECT_STAGE_ADVANCE_INVALID/GRAPH_TARGET_STALE，返回advanceAllowed=false、nextStage=null和同名guidance；不返回任何流程启动动作。其余图、租户、项目及权限错误继续抛出。实际推进命令与ProjectRuntimeGraphResolver完全未修改，仍拒绝非PENDING目标。前端展示已有目标执行状态不能相邻推进的说明；未评估的门禁不伪称没有门禁。没有覆盖历史、修改冻结规则或创建审批节点。

定向后端49项通过（读取器14、readiness14、推进命令21），前端Z06推进6项通过；包括DONE/ACTIVE/SKIPPED目标只读返回阻塞、原状态不变、无流程动作、其他图错误仍拒绝、原命令失败测试保留。日志.run/s1-readiness-tests.log；前端使用现有vitest.pms-file.config.ts。原readiness“目标ACTIVE抛异常”断言改为“只读返回不可推进”；实际推进和图校验断言保持。

## 授权构建、部署与验收

需求方本轮明确“编译通过即可部署，不需要等并行任务完成”，授权本次59191部署重启。此前getGeneratedAt编译错误已由并行会话修正；两次统一构建先后遇到工程、资产模块在构建期间新增割接API依赖的错误，依赖落盘后重新构建。

scripts/worktree-build.ps1最终在2026-10-12 19:56:54全模块BUILD SUCCESS，日志.run/s1-readiness-build-approved-final.log。源码一致性检查仍检测到并行编辑；按本轮明确授权，在同一工作树独占锁下核对包时间、实际编译包内S1读取修复及历史读取修复，并通过原22项既有修复基线，使用官方worktree-artifact.py一次性冻结成功编译产物。未降低默认构建脚本或修复校验，source明确记为concurrent-source-approved及成功构建时间，不声称匹配后续源码。

冻结清单.run/build/releases/59cbddfc-cd37-438e-8763-548175be3ab7/manifest.json，SHA256 28b208a1fd21a37227a78061b446e8ac527c5d5e0922af7160cf8784b1195e60。使用scripts/worktree-backend.ps1 -Manifest ... -ApprovedRestart部署；原包备份和运行包身份校验保留，local,dac配置，新PID25800，backend healthy。48182没有启停，部署后独立检测dac healthy。

正式API完整比较plans/rounds，25条轮次及计划与部署前完全一致；基线.run/s1-history-before-deploy.json，结果.run/s1-history-after-deploy.json。readiness返回currentStage=S4、advanceAllowed=false、STAGE_COMPLETION_UNSATISFIED，未强行推进。真实浏览器重新打开项目S1入口，显示4/4已完成；刷新门禁结果无GRAPH_TARGET_STALE弹窗，执行历史与规则结果可打开，原轮次保留。

返工S1同时后续阶段已完成的负向场景由49项后端/6项前端定向测试验证，未为浏览器验收返工或重置真实数据。当前S1正常入口的真实浏览器验收不等于已在真实数据重新执行该返工场景。并行割接业务闭环不属本次验收；本次未修改模板或项目计划，没有提交或推送Git。


## 工期与分工被替换的证据

原来源模板993009900014的Designer证据（docs/generated/2026-09-22-visio-delivery-template/designer.json以及2026-10-06-visio-system-record-template/source-template-published-designer.json）明确：

| 项目 | 原来源配置 | 模板993009900016配置 |
| --- | --- | --- |
| PRE_DURATION名称 | 工期与分工界面确认 | 施工计划 |
| 业务组件 | PLN_CONSTRUCTION_PLAN（现有工期面板） | PLN_STAGE_PLAN_APPROVAL（施工计划面板） |
| 业务视图ID | 2101606310708887554 | 2102312815597113345 |
| 完成条件 | DURATION_EFFECTIVE且D_INTERFACE | CONSTRUCTION_PLAN_APPROVED |

当前任务description及规则name仍保留原工期/分工口径，形成名称、绑定、条件与说明冲突。来源说明明确“工期生效不等同施工计划审批”；不能将这项替换解释为联系人调整或纯展示改名。

只读数据库逐版抽取PRE_DURATION完整任务及完成规则：模板V0(993009900035，2026-09-24 14:25:13)、V1(993009900036，14:36:20)、V2(993009900050，2026-09-30)，以及项目V1至V4，抽取内容完全一致，证据.run/s1-duration-version-provenance.json。因此该偏离最晚在993009900016创建草稿时已存在，项目2026-10-07初次实例化即继承；2026-10-10/11的联系人修复未修改PRE_DURATION名称、绑定或规则。

查询该模板及最初修订的system_operate_log未获得记录，不能据此确定具体操作者、会话或批准来源；未找到联系人修改授权覆盖这项偏离的证据。此前验收记录称该任务为施工计划是描述现场配置，不是本次授予的业务变更。

本轮未改写模板、项目计划或历史绑定。恢复应按原业务口径走前向模板/项目修订，并核对当前分工界面条件与对应Owner提供的事实，不能覆盖已完成轮次或简单回填旧快照；当前模板没有原D_INTERFACE交付件，不应臆造替代完成条件。并行源代码和其他未提交修改全部保留，未提交或推送Git。
