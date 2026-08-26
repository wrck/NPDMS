# F-PLT-001 统一文件身份与版本管理

> Feature实施状态：`IMPLEMENTING`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / NPDMS-FPLT001-FEATURE-READY-20260826-01-R2`
> Technical Plan Gate：`PASS / NPDMS-FPLT001-TECHPLAN-20260826-01-R3`
> Implementation Done Gate：`PENDING`
> 当前任务：`Task 4 实现上传初始化与内容校验/安全扫描链`
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

- [x] Task 1 建立六表、字典/策略/权限/Job种子、50MB配置和Feature工作单（PASS / `c1e3a46` / 独立裁决GO）
- [x] Task 2 实现批准的INFRA技术存储回执适配（PASS / `38f6a6b` / 独立裁决GO）
- [x] Task 3 定义PLT公共文件契约、Provider注册与持久化原语（PASS / `974feb3` / 独立裁决GO）
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

> 检查点（2026-08-26）：Task 1提交`c1e3a46e768e670a7788b7d933899494963cbcf9`经独立复审GO；V92六表、5条租户复合外键、V93确定性种子、50MB/52MB应用边界、稳定错误码及迁移契约均通过，允许推进Task 2。

> 检查点（2026-08-26）：Task 2提交`38f6a6b0a72ffc0b2285128e12a1a741ff8bf579`经独立复审GO；窄化INFRA存储回执契约、跨配置确定性重放、冻结配置短时访问/补偿删除及真实MySQL验证通过，允许推进Task 3。

> 检查点（2026-08-26）：Task 3提交`974feb3a17c0f15c5ece59c8b2745862c18c8f33`经独立复审GO；公共文件契约、九动作封闭值域、业务Provider唯一解析、六表显式Mapper、精确槽位/稳定游标/锁定读与场景CAS均通过，允许推进Task 4。
