# F-PLT-001 统一文件身份与版本管理

> Feature实施状态：`IMPLEMENTING`
> 总体工程阶段：`IMPLEMENTATION`
> Feature Ready Gate：`PASS / NPDMS-FPLT001-FEATURE-READY-20260826-01-R2`
> Technical Plan Gate：`PASS / NPDMS-FPLT001-TECHPLAN-20260826-01-R3`
> Implementation Done Gate：`PENDING`
> 当前任务：`Task 7 实现短时下载/预览访问`
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
- [x] Task 4 实现上传初始化、用途授权与策略选择（PASS / `0a2869a` + `48fe53c` / 独立裁决GO）
- [x] Task 5 实现50MB正向上传、版本提交、引用绑定与文件事件投递（PASS / `81e1c0f` + `9b4a9d4` / 独立裁决GO）
- [x] Task 6 接入首个SOL消费者并解除材料主线阻断（PASS / `34ce4df` + `eb42aa3` / 独立裁决GO）
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

> 检查点（2026-08-26）：Task 4提交`0a2869a`及整改提交`48fe53c`经独立复审GO；受信上传初始化、50MB有界内容校验、ClamAV规范响应失败关闭、完整成功/拒绝审计及真实扫描验证通过，允许推进Task 5。

> 检查点（2026-08-26）：Task 5提交`81e1c0f`及整改提交`9b4a9d443c95ff1f5bd606e9a1c97c23eb295e99`经独立复审GO；首次上传与ADD_VERSION、精确引用、四类文件事件投递、Quartz自动注册/退避重领及最终存储补偿闭环通过，允许推进Task 6。

> 检查点（2026-08-27）：Task 6实现提交`34ce4dff8f380192e96aba679656cf4728527feb`及范围锁整改`eb42aa3ae4d1f5a4510e73c824e3cdc4d866b3ef`经独立复审GO；SOL客户依据上传、固定版本冻结、BPM三终态重验及PROJ根树范围版本锁闭环通过，真实MySQL/Flowable/PROJ/PLT/SOL验证确认版本变化时无成功终态或审计，允许推进Task 7。
