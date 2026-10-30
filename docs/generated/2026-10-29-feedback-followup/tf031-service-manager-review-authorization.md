# TF-031服务经理发起方案审批权限修复

2026-10-29。需求方明确实施方案发起权限服务经理与项目经理相同，服务经理本人发起也允许本人通过正常BPM任务审批；当前区域/项目范围、功能权限、实时撤权、阶段前置和历史保护保持。

## 原样本与根因

独立项目84578已经正常完成S0/S1/S2。z01010可保存S3方案，但POST `/api/v1/pms/solution-reviews`返回业务错误“无权访问该项目树范围”。只读核对：[项目成员事实](tf031-project84578-service-manager-readonly.json)、[区域范围事实](tf031-project84578-regional-scope-readonly.json)。z01010为本项目已确认PRIMARY SERVICE_MANAGER；y06496为PM，不能用z01010的全局PM角色冒充本项目PM。其有效SYSTEM SERVICE_MANAGER组织范围与本项目公司、部门精确命中。

`SolutionTieredReviewService.authorize(write=true)`在功能权限与PROJECT_MANAGE范围通过后，仍只查询PROJECT_MANAGER参与事实；无匹配时PROJ抛出上述错误，尚未到方案状态/BPM提交。页面可保存而不能提交，是这条二次硬PM守卫造成。

## 修改边界

后续用户要求统一项目管理资格：下述成员/区域来源选择现已收归PROJ的`ProjectManagementQualificationApi`，`SolutionTieredReviewService`只做功能与FULL范围检查后调用该API，并把返回的原Fact传入同源锁。工程不再直接组合参与者与区域API；统一后的191项及最终回执见[全局审查索引](global-service-manager-permission-review.md)。以下两条来源说明保留为根因与统一Owner的行为边界，前轮红绿日志保留其对应时点。

同一处发起、刷新、修订命令的管理资格统一为两条真实Owner来源：

1. 使用`inspectAuthorization`确认当前MANAGEMENT_CODES成员（PM、服务经理及既有L1/L2别称），按实际观察到的管理角色调用`lockAndRevalidateAuthorization`，保留ACTIVE与项目版本重验。
2. 只有前者确实无资格（null或现有精确拒绝码）才使用已有`ProjectRegionalManagerFactApi.inspect/lockAndRevalidate`，由PROJ/SYSTEM确认当前区域、账号、角色、公司部门范围及其锁重验。区域资格不写成PM成员事实。

缺功能权限或空FULL管理范围先拒绝；未确认关系且没有独立区域资格不能提交。已选择的成员/区域来源在锁内失效或版本改变时直接失败，不切换到其他来源掩盖撤权。非“无资格”业务异常直接传播。原查询型读取范围、方案版本、必填、前置任务、审批规则和旧审批历史未改变。

PROJ既有错误码`1_014_024_033`发布为`ProjectParticipantFactApi.SCOPE_DENIED_ERROR_CODE`，原`ErrorCodeConstants`引用同一值及原文案；不从其他模块依赖PROJ业务实现，也不吞掉版本冲突。

## 验证

- 失败测试：补充旧端口回归夹具后，47项中5项准确失败；27项方案必填保护及原状态规则仍通过。证据`.run/feedback-20261029-tf031-review-authorization/red-qualified.log`。
- 修复后方案权限20项、必填27项，共47项通过；覆盖确认PM、确认项目SM、没有项目成员的区域SM、无功能权限、空范围、未确认且无其他来源、成员/区域撤权、其他Owner业务失败不fallback。证据`green.log`。
- 随需求方本人审批裁决补充：真实隔离H2 Flowable以服务经理7发起、冻结候选7、保留RUNNING人工待办，再由本人实际完成审批；断言历史任务受理人/结束时间、人工APPROVE局部变量及流程结束。未向流程写伪造终态值，未改生产BPM。该独立引擎没有平台终态投影监听器，不将其人工任务验证表述为真实项目终态回写验收。
- 最终方案20、必填27、BPM守卫6、真实Flowable7共60项通过。证据`green-self-approval-final.log`；精确`git diff --check`通过。
- 独立审核已复核确认成员与区域两条授权链、精确错误分类、锁重验和失败保护，无阻断发现。

本修复尚未部署或真实浏览器复验。主线程随后以实际PM提交/复制的方案50及新方案51是独立业务过程，不修改其历史，也不能代替服务经理新包复验。未执行数据库迁移、DDL、重启或任何直接数据修补。
