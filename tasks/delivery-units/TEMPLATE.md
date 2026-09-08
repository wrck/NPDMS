# DU-YYYYMMDD-NNN 简要名称

> DU状态：`PLANNED`
> DU类型：`FEATURE`
> Feature协调：`F-XXX-001=FEATURE_EXCLUSIVE`
> Task范围：`本次负责的业务义务/API及对应Task，不只写文件路径`
> Owner：`明确人员或会话`
> 分支：`codex/feature-branch`
> Worktree：`实施来源Worktree的绝对路径`
> 认领基线：`NONE`
> 认领提交：`SELF`
> 修改边界：`path/a/**;path/b/file.ext`
> 串行资源：`NONE`
> 旧功能范围：`NONE`
> 验证：`适用命令或Gate`
> 集成记录：`NONE`

## 协调入口

- 协调Worktree：填写负责编辑并提交DU到master的工作树绝对路径；其未提交文件不是生效认领，读取方式见[工程链6.2](../../docs/engineering/00-engineering-chain.md#62-delivery-unit认领实施与交接)。
- 协调者：本轮并行启动或已交接的协调任务；由其串行核对职责/路径并提交认领，可一次提交多个DU。
- 来源Task：填写上述实施Worktree内的`tasks/features/F-XXX-001.md`及本次Task章节。

参与方用`git show master:tasks/delivery-units/DU-YYYYMMDD-NNN.md`读取目标DU，再定位来源Task/代码；无需切换、合并master或包含认领提交。`PLANNED`仅预约，首次可以直接以`CLAIMED`提交到master，提交后才开工。`SELF`沿用元数据标记，生效依据是master中的已提交记录，不要求额外解析激活历史；认领基线按需补实际Git引用。

Owner、业务职责、排他写边界或交接后的来源位置改变，先确认并提交DU变更，再按新范围写入；未提交编辑不扩权或释放原认领。普通进度、日志及测试记录不要求逐次提交。提交不是写锁，也不替代实际交接；提交仍须用户授权并加载git-commit技能，不自动推送。

## 目标与边界

说明本Delivery Unit交付的可验证闭环、Requirement、提供的接口/正式契约位置及明确排除项。协调者核对活动DU中的业务职责是否重复，不能仅凭路径无交叉认领。一个DU可以覆盖多个Feature或Task，但Feature仍是唯一Implementation Done单元。

## 依赖与消费交接

每个直接依赖记录一条：提供方Feature/DU → 需要消费的业务义务/API → 提供方正式契约与来源Task章节 → 可用增量和验证引用（尚未交付则如实说明）。Owner、Worktree和进展引用提供方记录，不复制第二套状态；无依赖可省略本段。

消费者不能因为本分支没有代码另写提供方实现。只在契约变更、可用增量、阻塞或Owner交接时通知相关方并更新原记录，不逐提交同步。提供方完成所需独立可用增量后按授权优先合入master，消费者同步并真实联调，不等待整个提供方Feature Done。

## 交接

- 最后提交：`NONE`
- 已完成：无
- 可供直接消费者使用的增量及验证引用：尚未交付
- 剩余：全部
- 测试：未开始
- 已知失败：无

## 集成回执

由master协调者记录选中的提交范围、最终Flyway/公共契约处理、验证结果和`INTEGRATED_PARTIAL|INTEGRATED_COMPLETE`结论。分支自报不能填写master集成结论。
