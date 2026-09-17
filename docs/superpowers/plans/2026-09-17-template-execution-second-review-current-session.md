# 项目模板执行解耦：第二轮复审结论与先行修复

> 日期：2026-09-17
> 仓库/分支：`wrck/NPDMS` / `codex/liteflow-remediation`
> 第二轮复审及本次提交基准：`d13798671f1ce0ca44f5d17a88c92fe579f92eb9`
> 对应范围：[仅当前会话实施计划](2026-09-17-template-execution-rules-and-controlled-operations-current-session.md)的 S1～S6 主链及直接消费者。
> 用户授权：已有结论先提交，再修复未修复部分；修复使用统一方式兼容其他业务实体。
> 本次交付：固化三项复审发现，提交 R2-02/R2-03 的代码和测试源码；R2-01 仍阻断，后续单独修复。不修改既有阶段历史，不宣称真实验收通过。

## 1. 结论

第二轮确认 2 项 P1、1 项 P2。独立阶段提交齐全不等于全链路闭合，当前仍为源码层面的 NO-GO：需求分析新受控调用与精确作用域守卫不兼容，不能把它记成仅剩真实环境验证。

本记录只固化当前会话范围的结论，不扩展为全库审计、业务迁移、指定试点或其他会话任务。

## 2. R2-01 / P1：需求分析受控调用与精确守卫不兼容

状态：**本提交未修复，后续独立处理。**

相关 Java 路径以 `pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/` 和 `pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/` 为根；公开操作 API 在 `pms-module-project/pms-module-project-api/src/main/java/cn/iocoder/yudao/module/pms/project/api/workbinding/operation/`。

实际调用链：

```text
ProjectControlledOperationExecutor 建立精确 Frame
  → RequirementAnalysisOperationCommandAdapter
  → RequirementAnalysisEntityCommands.create/save/complete/copy
  → RequirementAnalysisAccess.lock / lockExecution
  → RequirementAnalysisExecutionAccess.lockCurrent
  → 旧四参数 ProjectBusinessExecutionApi.WriteRequest
  → ProjectOperationAwareExecutionGuard 精确匹配失败
  → CONTROLLED_OPERATION_SCOPE_MISMATCH
```

`ProjectVerifiedOperationScope.matches` 要求操作编码、操作版本、对象及完整执行选择一致；四参数请求没有操作和对象证据。同 Owner/对象类型已存在 Frame 时，守卫禁止降级旧路径。因此业务权限、状态、绑定和版本即使合法，需求分析四个受控操作仍可能在此确定性拒绝。无受控 Frame 的独立旧入口不受这一新增分支影响。结论来自源码调用链，没有冒充实际 HTTP 复现。

后续修复必须提供可复用的业务命令上下文：由真实 Owner 入口声明稳定操作身份和源对象，统一处理版本 Provider、表单和文件回调，以及 Owner 新生成对象的明确登记。COPY 的源修订与新草稿、CREATE 的无源对象与新对象必须区分。不能让旧四参数请求通配通过，不能失败后回退旧守卫，也不能直接复制外层已验证 Frame 的全部字段冒充独立核对。

待覆盖：四操作分别在任务/阶段调用；版本服务再次回调；COPY/CREATE 的新对象与表单/附件回调；不同修订、操作、租户、主体及轮次不得借用校验；异常清理与同事务回滚。

## 3. R2-02 / P1：任务办理误用 UPDATE 权限

状态：**代码和测试源码随本提交落库，框架验证待执行。**

`ProjectOperationContextResolver` 的 TASK 分支原调用 `TaskBusinessAccess.writable`，该方法要求 `pms:project-task:update`。同一新解析器的 STAGE 分支和既有任务 START 动作使用 `pms:project-task:execute`。这混淆维护资格与执行资格：有 UPDATE 无 EXECUTE 的用户可能通过项目侧判断，而只有 EXECUTE 的办理人被拒绝。Owner 权限仍独立校验，不将此问题描述成已获得所有业务权限。

修复：

- `writable` 保留 UPDATE 语义，不改变旧关联维护入口。
- 新增 `executable`，使用 EXECUTE 并复用原状态、租户、角色/分派及范围判断。
- 新操作上下文改用 `executable`，能力查询和提交重验保持一致。
- 新增 `TaskBusinessOperationPermissionTest` 四项 JUnit/Mockito 测试源码：两类权限不互授，范围、终态和租户保护保留。

## 4. R2-03 / P2：宿主未消费最新视图状态

状态：**代码和测试源码随本提交落库，纯函数测试通过，Vue/浏览器验证待执行。**

后端能力查询已经输出 AVAILABLE/READ_ONLY/UNAVAILABLE；原 `operationHost.allowedActions` 未消费此字段，`BusinessViewHost` 仍按旧注册状态传递只读。页面打开后视图停用而业务/执行资格仍满足时，可能继续显示写按钮并传递 readonly=false。

修复：增加 `operationPresentation` 纯函数；新受控视图 READ_ONLY/UNAVAILABLE/缺失状态只保留已有查询动作，向组件传递只读及原因。不改变 Owner 权限、业务 API 合法性和项目推进规则，不销毁编辑缓冲、执行客户端或不确定请求恢复入口。独立和旧契约路径保留原呈现规则。

新增 11 项 Node 纯函数测试和 3 项 Vue/Vitest 宿主测试源码。纯函数通过不等于业务组件实际遵守 readonly 的浏览器证明。

## 5. 全阶段复审覆盖

| 阶段 | 第二轮重点 | 结论 |
|---|---|---|
| S1 | 三类入口、五时点、可信身份和旧契约 | R2-01 是实现未满足合同，不需重设总体方案。 |
| S2 | 目录、规则闭包、哈希和发布能力检查 | 未确认新增编译缺陷；运行适配已安装不能证明内部调用可用。 |
| S3 | 可见性、Owner 权限、项目办理资格 | R2-02；权限交集不能混用维护与执行。 |
| S4 | Executor、Advisor、Scope、Guard、Owner/版本回调 | R2-01；不得撤销跨操作/对象失败关闭。 |
| S5 | 源接收、分目标投递、有效计划/轮次、正式完成 | 未确认新增确定性错误推进；早到/乱序/并发仍须实测，唤醒不等于履约证据。 |
| S6 | 能力、呈现状态、编辑身份、原请求恢复 | R2-03；保留上一轮未保存和重试保护。 |

## 6. 本次验证与提交边界

- 第二轮交付包八个候选文件及四个修改前原文均按 Git Blob SHA 核对。本次未改变候选语义。
- 本次提交前重跑 `operationPresentation.node.test.mjs`：11 项通过、0 失败；独立 TypeScript 严格检查通过。没有把上一轮测试冒充本轮重跑。
- 已补四项 JUnit/Mockito、三项 Vue/Vitest 测试源码，但尚未运行。
- 完整 JDK25/Maven、Spring 代理/事务、JUnit、Vue/Vitest、MySQL、API、模板保存重开和浏览器联调待执行。
- 本次无数据库迁移、部署、服务重启或 Feature 状态晋级。只对目标分支作非强制快进，保留已有 S1～S6 和首轮自审历史。

R2-02/R2-03 的源码修复落库不代表全链路 GO；必须继续闭合 R2-01。后续通用修复及验证在独立记录中说明，不改写本报告的基准发现。
