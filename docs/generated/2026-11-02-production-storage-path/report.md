# 四机正式环境 MinIO 上传路径修复

2026-11-02，需求方确认四机正式环境，并批准修复、部署及一次后端重启。新上传已经使用`sdp`桶；实际浏览器上传、读取和下载字节校验通过。

## 原因和修改

- 正式默认配置`id=1`的桶名仍为`npdms`，旧运行包`deployment-adapter.jar`也固定使用该桶。改变访问域名不能改变S3签名请求使用的桶名。
- 四机适配器改为读取`npdms.server-deployment.minio-bucket`；桶名、访问域名后缀及已有配置校验统一使用同一值。`site.json`的`minio_bucket`同时驱动后端配置和三机对象代理路由，缺省为`sdp`，拒绝路径注入。
- 通过现有`FileConfigService`新增配置`id=2 / sdp-server-minio-172.17.73.104 / bucket=sdp`，读写探针成功后设为默认；旧配置`id=1`仅取消默认标记，没有修改其名称、存储类型、配置JSON或备注。旧桶和正式已有`/npdms/`只读代理路由保留。
- 旧配置受保护字段SHA-256切换前后均为`12e19b4fdb0dd0f7d51b3c4deb4134f502b377c8e043813bc08a6b20a45efecc`（字段为name、storage、config、remark，不含预期改变的master及更新时间）。
- 未执行Flyway、DDL、数据搬迁、业务应用代码更新、DAC发布或权限扩大。独立诊断对象的短暂写入与删除属于已批准读写验证。

## 产物与启动情况

- 前版本：`SDP20261102_DEVICE_PROJECT_SYNC`，保留原目录。
- 当前版本：`SDP20261102_STORAGE_PATH`。
- `yudao-server.jar`原样复用，SHA-256：`9a1bd421c0092187f67aca0dad5c65c615763fb31975d25b4c74ae8d241f898a`。
- 新`deployment-adapter.jar`：`6c308e54fda46b0e75f905e77b6e7357b41e43b914b8fedd7bf180e70d918453`。仅替换4个旧MinIO适配器类为新存储适配器；非存储类和其他原JAR条目逐项保持原字节，包括DAC身份适配器。
- 工作树锁内使用固定JDK编译，针对与正式运行包摘要一致的应用JAR验证。`storage-adapter-source.json`记录单独编译的存储类来源；最终合并适配器摘要以`release.json`为准，旧`adapter-source.json`保留为来源证据。
- 配置备份：BE的`/etc/sdp/backend/application-prod.yaml.before-storage-20261102`。现网新增`minio-bucket: sdp`及`SERVER_IP: "${NPDMS_SERVER_IP}"`映射，其他配置保持。
- 发出一次明确的`operate.sh be activate backend ... --approved`。首次切换漏检了旧配置键`NPDMS_SERVER_IP`，新适配器要求`SERVER_IP`，启动失败并被systemd自动重试5次。补映射后由既有自动重试恢复，没有再次发出手动重启命令。该启动异常属于本次部署失误，不能将此过程报告为一次平稳重启。
- 23:37:19存储初始化就绪；最终后端PID为`102051`、readiness=`UP`、自动重试计数稳定在5。DAC PID始终为`17159`、自动重启计数0。

## 验证证据

- Java隔离探针通过：非默认桶名与域名一致、新默认项选择、重复启动不改配置、已有桶名漂移拒绝、读回失败时保留旧默认并删除仅本次创建的配置、非法/缺失桶名拒绝。
- Python部署工具23项测试中，最终有效结果为22项通过、1项既有失败。新增测试最初受Windows默认GBK读取影响，已显式指定UTF-8并重跑通过。保留的既有失败是`test_data_paths_and_mount_protection`要求所有service模板具有`ConditionPathIsMountPoint=/data`，而既有`sdp-storage-proxy.service.in`没有该断言；本次未扩大修改该服务模板，未将整套测试记为通过。
- 重启前，使用正式应用存储凭据对员工域名和客户域名分别执行`sdp`签名PUT、后端读取、签名GET和未签名GET：内容一致，未签名GET均为403。探针对象均在finally中删除。HTTPS使用JDK公共CA验证，未禁用证书校验。初次探针使用内部专用信任库产生TLS失败；改用JDK公共信任库后验证。初次诊断键使用点目录，被现有隐藏路径规则拒绝404；改用普通诊断目录后通过，未降低代理限制。
- 重启后，真实浏览器从`https://sdp.dptech.com/infra/file/file`上传诊断图片，显示新增成功。
- 文件记录：`id=2 / config_id=2 / size=3678 / image/png`；实际路径为`https://sdp-customer.dptech.com/sdp/20261102/storage-verification-20261102.png`（此处省略签名查询串）。
- 浏览器打开实际签名链接，图片自然尺寸`480×100`；通过浏览器下载的文件与上传原件SHA-256均为`27f7620e2639ebe3239ef6293d6c14e620a7ce59cd5d913fd87a0378ea835e26`。
- 该诊断图片保留在文件列表，未绑定项目、审批、交付件等业务对象。不能将本次通用存储验收扩展为全部业务附件流程验收。

页面证据：[默认配置](default-storage.png)、[上传成功列表](upload-result.png)、[下载预览](download-preview.png)。

复现与详细回执位于本工作树`.run/storage-path-20261102/`、`.run/certificate-session-20261102/ssh/request-110-storage-be.json`至`request-144-storage-be.json`及对应结果；构建产物位于`M:/AICoding/CodexData/artifacts/storage-path-20261102/`。回执不包含明文凭据或签名查询串。
