# UMC DOC 与结构化结果生产接口实测

日期：2026-10-27。当前交付范围：日志上传到UMC，取得UMC DOC原件及结构化结果。平台三格式生成、规则判定替换、巡检页面与业务归档不计入本次内部协议能力的完成范围。

## 实际实现

- API：`pms-module-integration/pms-module-integration-api/src/main/java/cn/iocoder/yudao/module/pms/integration/api/umc/UmcInspectionApi.java`。
- 提供方：`pms-module-integration/src/main/java/cn/iocoder/yudao/module/pms/integration/umc/NodeUmcInspectionGateway.java`。
- 协议执行器：`pms-module-integration/src/main/resources/umc/inspection-worker.cjs`。
- 配置：`UmcInspectionProperties`，前缀pms.integration.umc，真实密码仅经进程环境/密钥提供方注入；不写入源码、参数、临时文件或运行配置模板。

## 真实调用结果

用当前Java接口和提供方调用实际UMC，输入为已脱敏合成设备的提示符日志，未连接或采集真实设备。

- 外部任务75、报告690；完整检查点及摘要见 `result.json`。
- DOC原件1,300,451字节；SHA-256为 `2b3a63aaba499c3d7dafa99639d2cf0c366a7aae3547f8b73ba1e3c08fb9ecde`。
- 型号VPN1000-GA-X、SN为SYNTHETIC-SN-001，结构化结果包含2项规则及完整指标和值层次。
- 原始JSON及其摘要与类型化DTO一并返回，空值、空列表、布尔结论及外部ID精度保留。
- 同一requestId、作用域、输入及检查点再次调用，复用任务75/报告690，没有再次上传或创建新任务。
- 每次调用收尾尝试正常注销，实测warnings为空。初期探针任务74/报告689保留，未删除或重写远端历史。

这是Java接口/Node执行器的真实对接验证，不是前次手工探针冒充生产接口，也不是NPDMS页面端到端验收。Node为24.11.1，Java使用固定JDK25；没有启动或更新运行中的NPDMS服务。

## 定向验证

- 9项Node协议测试全部通过：长ID、文件名、WordML原件校验、DOC就绪轮询、正常及未知上传恢复、登录失败收尾、跨设备拒绝、假DOC拒绝、超时注销。
- 6项Java测试全部通过：跨租户、缺配置、结构化数据保真、WordML/损坏/XXE拒绝、字节防御复制、子进程不继承其他系统凭据及预加载/TLS绕过变量。
- 定向Java测试沿用 `scripts/worktree-toolchain.ps1` 和同一工作树互斥锁，实际新增源码编译到隔离目录；不写模块target。未宣称全仓Maven构建通过。
- 回归入口：`node --test pms-module-integration/src/test/node/umc-inspection-worker.test.cjs`；隔离Java验证入口为 `.run/umc-adapter/verify.ps1`。

## 交付文件与后续消费

- `umc-report.doc`：UMC出具的DOC原件，实际Word2003 XML。
- `umc-report-detail.json`：UMC结构化原始响应，不重写外部运行行ID。
- `typed-report.json`：系统API反序列化后的设备→规则→指标→值对象。
- `result.json`：当前实测任务、报告、摘要及恢复结论。

业务Owner在现有权限和作用域校验后即可消费该内部接口，应在长事务之外执行，并持久保存检查点、通过PLT登记返回文件。当前没有新增REST入口、巡检页面按钮、业务检查点表或PLT绑定；这部分不能写成已完成。配置不完整时失败关闭，不自动使用测试账号。没有迁移、重启、部署、提交Git或推送。
