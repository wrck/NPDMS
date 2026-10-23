# 设备日志拆分、关联与最新文件增量

2026-10-06；对应[授权范围](../../../specs/features/F-IMP-004-configuration-log-collection.md#6-已授权的设备日志文件增量2026-10-06)和[当前任务](../../../tasks/features/F-IMP-004.md)。代码已实现，运行验收未完成；本目录是生成证据，不是新的业务基线或Feature Done证明。

## 实现范围

DAC新增`POST /api/v1/npdms/log-splits`，按终端命令会话及唯一整机SN拆分show run/show tech/display等输出，同设备重复会话合并，保留完整命令、stdout和stderr；含糊或缺失SN整批拒绝。复用原增强脱敏器，并处理Token/API key及SNMP认证/加密口令；只返回脱敏正文，不回显失败输入，不执行设备命令。

NPDMS的AST提供独立归档基础能力：当前租户与项目EDIT范围校验，SN精确关联当前项目设备，稳定顺序锁定设备，设备事务计数器分配版本。手工上传与配置采集回传共用版本序列，最新为当前项目内同一设备的最高可用版本；新版本不可用时回退到已有可用版本。批次、脱敏来源文件、逐设备文件一起提交，旧版本保留，来源键不能替换内容、操作人、文件名或项目。

PLT复用已有内容校验、扫描、存储回执、不可变引用、材料注册与下载票据；Infra新公共内部接口只读取已登记receipt并核对长度和摘要，不暴露HTTP或直接跨模块访问Service/表。IMP通过新增代理接入原配置结果接收器：先验证原Owner与不可变重放，成功采集才附加归档，保持原状态机及失败/联调路径。该代理随既有DAC接入开关启用，采用原配置CONSUME权限与原发起人；不伪造登录用户，也不以日志归档推进配置完成。

新增独立归档页面及复制增强的“配置调试与设备日志”页面，原配置页面不修改。新上传采用`application/octet-stream`原始字节流，最多20MiB，避免multipart将敏感原文写入临时文件；操作审计保留，正文不记录。上传同时要求归档import和`pms:file:upload`，读取要求query与项目VIEW，下载同时要求download和`pms:file:download`。V409初始化表、材料分类和菜单，不自动授予业务角色权限。

旧URL日志与原采集历史保留，未做旧数据迁移。本次最新查询覆盖新增手工上传和成功配置采集归档；模板日志确认规则、其他五项Owner完成事实及新模板发布仍是独立未完成事项。

## 已执行验证

[verification-summary.json](verification-summary.json)从本次实际Surefire报告提取7组共25项测试，全部通过，没有复制包含环境属性的原始XML。覆盖DAC完整内容/歧义/脱敏、AST范围与幂等/历史/版本回退、原文入口读取和超限拒绝、IMP真实接收器代理与事务触发、PLT任务及Owner来源、Infra已登记内容读取。IMP事务单测使用事务管理器替身，实际数据库回滚须由后续MySQL验收证明。

主要执行命令（根目录执行，DAC组在`device-ops-platform`目录）：

```powershell
$env:MAVEN_OPTS = '-Xms64m -Xmx512m -XX:+UseSerialGC'
mvn.cmd -pl pms-module-asset,pms-module-engineering,pms-module-platform,yudao-module-infra -am test '-Dtest=DeviceLogArchiveServiceTest,DeviceLogArchiveControllerTest,DeviceConfigurationLogArchiveBridgeTest,DeviceLogFileServiceTest,FileStorageContentServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xms32m -Xmx256m -XX:+UseSerialGC -Dnet.bytebuddy.experimental=true' -q
mvn.cmd -pl device-ops-parser-semantic,device-ops-adapter-web-spring -am test '-Dtest=DeviceLogSplitterTest,NpdmsLogSplitControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xms32m -Xmx256m -XX:+UseSerialGC -Dnet.bytebuddy.experimental=true' -q
```

执行按改动拆为若干定向批次，上述命令表达对应验证集合，不声称一次全量运行。相关模块已编译，3个Mapper XML通过解析；这些检查不能证明SQL迁移已经应用。

[dac-http-verification.json](dac-http-verification.json)记录宿主机原生DAC服务48181的实际HTTP验证：2设备拆分200，缺少execute scope、namespace不符、project不符均403，缺失SN为422。使用原JWT/JWKS校验与临时内存密钥，未绕过认证；只启动自身验证服务，验证后已停止自身DAC与issuer辅助进程。stderr完整保留后续以对应DAC定向测试验证。

新增3个前端文件ESLint和Prettier通过。vue-tsc按当前严格配置检查新增入口及真实依赖，使用实际全局声明和Vite类型文件；新增文件无诊断，但10处既有依赖错误导致整体失败，详见JSON。没有放宽类型检查或修改无关组件。

## 尚未执行与恢复条件

指定测试环境仍为Compose `npdms-50eb-test`、`npdms_test`、MySQL23316、Redis26379。现有容器配置和本地配置的数据库凭据均返回1045。已向需求方请求有效凭据配置文件路径；不重置库、不修改密码，不改用第二套数据库替代指定验收。

- V409 Flyway迁移及实际菜单/材料种子尚未应用。
- `DeviceLogArchiveMySqlTest`的4项测试已编译但未执行：同设备并发形成不同版本、同来源并发幂等保留完整文件身份、不同项目设备首次导入、子文件存储失败时版本计数器/批次/文件一起回滚。测试只清理自身虚拟项目及明确设备ID，不清空既有测试库。
- NPDMS真实浏览器上传、重新打开、查看设备历史、下载完整来源与设备文件，以及原配置采集实际回传尚未执行。DAC接口200与25项单测不能替代这些闭环。
- `yudao-server`整体重打包曾被已有59391进程占用的目标jar阻挡，未停用该进程。各相关模块编译不等于新NPDMS运行服务已启动。

恢复需要这套指定测试库的有效配置文件路径，然后按既定Compose入口迁移，完成上述MySQL与宿主机应用/真实浏览器验收。新模板仍保持原草稿，未修改任何已冻结项目快照。2026-10-07需求方授权本地提交与合入`codex/domain-migration`，Git操作不代表上述运行验收完成。

合入编号：主库`npdms_domain_test`的Flyway历史已确认V388～V390分别为设备合同投影索引、同步绑定分页索引和设备日志解析任务，不更改其文件或执行记录。通用文件框架原V388～V397在合入分支使用V399～V408，保持原先执行顺序；本日志归档原未执行V398改为V409。原检查点及验证快照按其采集时编号保留，不改写历史证据。
