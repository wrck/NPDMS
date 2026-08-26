# F-PLT-001 统一文件身份与版本管理

> Feature实施状态：`IMPLEMENTING`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / NPDMS-FPLT001-FEATURE-READY-20260826-01-R2`
> Technical Plan Gate：`PASS / NPDMS-FPLT001-TECHPLAN-20260826-01-R3`
> Implementation Done Gate：`PENDING`
> 当前任务：`Task 1 建立统一文件物理基础`
> Requirement ID：`PLT-02（V1/P0，FR-PLT-008）`
> Feature Spec：`specs/features/F-PLT-001-unified-file-identity-and-version-management.md`
> Feature物理契约：`specs/features/F-PLT-001-physical-contract.json`
> Technical Plan：`docs/superpowers/plans/2026-08-26-f-plt-001-unified-file-identity-and-version-management.md`
> 锁定规格提交：`2efd8c476430d77ce2003c6e9fe300a335eac6a7`

## 实施边界

- 本Feature只实现PLT-02统一文件公共能力，不合并PLT-01、INT-11、INT-12或各业务域审批状态。
- PLATFORM持有FileArtifact、FileVersion、FileReference业务真值；INFRA只提供技术存储回执和短时访问能力。
- 首期仅处理50MB以内文件，复用Spring Multipart和Yudao既有文件存储链，不修改基础框架。
- 首个消费者为SOL客户延期依据；PLT只冻结文件事实，不推进PRE-01审批状态。
- 本Feature不包含历史附件迁移、Deployment、SIT、UAT或Release。

## 任务跟踪

- [ ] Task 1 建立六表、字典/策略/权限/Job种子、50MB配置和Feature工作单
- [ ] Task 2 实现批准的INFRA技术存储回执适配
- [ ] Task 3 定义PLT公共文件契约、Provider注册与持久化原语
- [ ] Task 4 实现上传初始化、用途授权与策略选择
- [ ] Task 5 实现50MB正向上传、版本提交、引用绑定与文件事件投递
- [ ] Task 6 实现文件查询与精确引用锁定重验
- [ ] Task 7 实现短时下载/预览访问
- [ ] Task 8 实现解绑、草稿删除、失效与归档
- [ ] Task 9 建设响应式统一文件界面并接入SOL客户延期依据
- [ ] Task 10 完成真实MySQL、Quartz、浏览器与独立Feature Done复审

## 上下游解除条件

- F-SOL-001 Task 6材料分支：Task 5～6提供真实上传、固定版本引用及锁定重验后解除。
- F-SOL-001 Task 9材料场景：Task 9接入SOL用途并完成真实MySQL链路后解除。
- F-SOL-001 Task 10：上述材料主线闭合后返回其正式浏览器验收。
