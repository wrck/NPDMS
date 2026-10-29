# TF-060 客户保障完整性错误分类修复

2026-10-29；来源：[问题跟进 TF-060](../2026-10-26-test-feedback-triage/问题跟进.md)。本次仅修复可由当前代码确认的高亮漏接，不将原反馈“已填仍报未填”的保存问题认定为已解决。

## 定位与修改

`CutoverPlanContactPolicy.validate()`在提交/生成时发现没有CUSTOMER保障行，或手填客户缺少姓名/电话，原来抛出`IllegalArgumentException`。`CutoverPlanController`因此响应400、`REQUEST_SCHEMA_INVALID`；当前页面只对`SUPPORT_ARRANGEMENT_INCOMPLETE`等方案完整性错误高亮并聚焦保障安排，故这两类客户完整性错误没有进入该提示。

最小修复仅将上述两处抛错改为已有`CutoverPlanApplicationException(SUPPORT_ARRANGEMENT_INCOMPLETE)`，由既有控制器响应422、`BUSINESS_INCOMPLETE`、`FIX_REQUEST`，接通已有页面高亮。原校验条件、中文提示及草稿允许暂存不完整信息的规则保持。客户引用版本/身份核验、格式错误、授权、批准后联系人补丁与不可变历史没有调整。

修改路径：

- `pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanContactPolicy.java`
- `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/service/plan/CutoverPlanContactPolicyTest.java`
- `pms-module-cutover/src/test/java/cn/iocoder/yudao/module/pms/cutover/controller/business/plan/CutoverPlanControllerContractTest.java`
- `yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/workbench/cutoverPlanComponents.spec.ts`（仅追加本项用例，保留既有并行改动）

## 验证

- 失败测试先行：原实现13项中2项准确失败，均为期望领域完整性异常、实际得到`IllegalArgumentException`；其余11项通过。证据：`.run/feedback-20261029-tf060/red.log`。
- 修复后：联系人规则9项、保障人员规则4项、既有控制器合约4项共17项通过。证据：`green.log`。
- 追加HTTP合约负例后，控制器合约5项通过，明确断言422、原中文提示、`BUSINESS_INCOMPLETE`、`SUPPORT_ARRANGEMENT_INCOMPLETE`和`FIX_REQUEST`。证据：`controller-final.log`。
- 前端组件及保障提示2组68项通过，含服务端拒绝缺CUSTOMER行后显示具体缺失角色、调用聚焦、不额外保存或下载的用例。证据：`frontend.log`。
- 精确差异`git diff --check`通过；独立审核未发现阻断问题。

## 部署前产物

固定入口`scripts/worktree-build.ps1 -Target backend -MavenArguments @('-DskipTests')`完整构建成功，构建前后源码指纹一致，22项既有修复保护检查通过；定向测试如上单独执行，不将此处跳过测试的打包算作测试通过。

- 冻结清单：[fa20ca69 manifest.json](../../../.run/build/releases/fa20ca69-4b35-40c3-8d8f-ea5d0efd9e07/manifest.json)
- JAR SHA-256：`5c607cb451ae75f0a4e98cc110626551bd85323c428a2cbb35595afa9875f161`
- 源码指纹：`c6112e8fd187d100b5250183acabb8b7403cbb03337e7287cea4d4dbab332b83`
- 构建日志：`.run/feedback-20261029-tf060/backend-build.log`

随后固定部署入口仅执行`-ValidateOnly`，冻结产物、`local,dac`、受保护178测试库配置及自动迁移关闭预检通过；未改变服务。日志：`.run/feedback-20261029-tf060/backend-precheck.log`。

该冻结产物包含构建开始时整个工作树已有修改，不是仅TF-060的独立补丁包。运行中的JAR在冻结/预检时仍为`0b27d7f50909bb2394fffabcfbbb7b19df14874924dcd50769277bc1285032be`，不包含本次新修复。

## 随后获批部署（2026-10-29 15:19）

需求方明确批准按`fa20ca69-4b35-40c3-8d8f-ea5d0efd9e07`清单重启59191。再次固定入口`-ValidateOnly`核对源码、冻结产物和运行包基线匹配后，使用同一入口`-ApprovedRestart`部署成功。PID44368、`local,dac`、health UP；实际运行JAR SHA-256与清单一致，22项既有修复保护通过。观察到11条至受保护178测试库的连接；其他服务未操作。证据：`.run/feedback-20261029-tf060/approved-deployment.log`、`approved-runtime.json`。Actuator配置值被掩码，回执明确区分固定启动器期望值与已逐值验证内容。

仅SELECT的只读事务查找TEST_ONLY任务及当前有效DRAFT方案，没有符合条件的草稿。现有两条测试任务分别为`2115348993804271616`（项目79557，P5/APPROVING）与`2115348581932007424`（项目79557，P3/SURVEYING）；没有撤回审批、创建新方案或改状态。证据：`test-draft-candidates.json`。

本次已部署，但尚未完成项目详情→阶段→任务办理的真实浏览器复验；原反馈的taskId、方案版本、编辑与保存载荷仍需现场样本核验。TF-060仍不标记已处理。没有执行迁移、DDL或业务数据写入。
