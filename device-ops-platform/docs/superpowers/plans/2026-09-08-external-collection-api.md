# External Collection API Implementation Plan

**Goal:** 为外部系统提供设备采集的提交、状态、输出流及后续解析结果接口，而非要求先上传离线日志。

**Architecture:** 保留现有 `/api/v1/collections` 与 `/api/v1/projects/{projectKey}/collections` 和 JSON 请求/响应格式。共享采集执行引擎；HTTP 层验证 namespace/project/subject 并在读取秘密前进行幂等重放；core/JDBC 持久化无秘密请求指纹、处理并发冲突。使用既有 OAuth2，不新增 API Key 或无鉴权入口。

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Security JWT, JDBC/Flyway, springdoc, JUnit, Apache MINA SSHD.

## 设计约定

- 优先完善已有异步采集 API；不新增重复同步执行 API，也不额外建设网关/客户端密钥管理。
- 设备采集需要 `device-ops:collections:execute`；查询需要 `device-ops:collections:read`。
- 请求仍携带 namespace，必须与受信 JWT 的 `device_ops_namespaces` 授权数组匹配；没有该数组时匹配 `client_namespace`，两者都没有时仅允许 `sub` 同名 namespace。subject 必需。只有受信授权数组显式 `*` 允许跨 namespace；local 调试身份显式带此授权，生产没有缺省放行。
- 涉及项目时额外验证 `device_ops_projects`，包括通用状态/输出/结果接口读取持久化 projectKey。旧 Web 客户端无需改变请求 JSON，但 IdP 必须授予它实际使用的 namespace（包括 standalone）。
- 无秘密、规范化 JSON 请求指纹包含提交者 subject、入口/项目、目标顺序、脚本/解析配置、连接选择与非秘密参数、超时、callback 与请求的语义解析选择。排除密码、私钥及口令；认证材料变化不能使重试重新执行。同 namespace 不同 subject 的 key 重用返回 409。
- 授权后先比较指纹；相同请求重放不需要重新提供临时凭据或重新解密/加载已保存连接。仅新任务加载秘密并派发。数据库竞争胜者也必须比较指纹。
- 对没有新指纹的历史任务，新 HTTP 请求重用旧 key 返回 409，而不是猜测是否同请求；历史查询保持可用。内部旧调用保留原有方法重载。
- 429 可能发生在保存任务/部分派发后，重试必须使用同一 key，不能承诺没有设备命令执行。

## 执行任务

- [x] **core/JDBC：**为 `CollectionTask` 与提交命令增加可选 `submissionFingerprint`；服务新增带指纹 `findExistingId`，旧重载保留；`CollectionJdbcRepository` 早期查找/并发胜者/外部请求 ID 冲突统一校验。新增 H2/MySQL V18 迁移。先写幂等差异、并发、legacy 和精确 namespace 隔离测试，运行失败后实现。
- [x] **HTTP 安全：**扩展 `ProjectClaimAuthorizer` 的 subject/namespace 校验，覆盖 generic/project 提交、详情、SSE、语义结果；完善路径参数显式命名与 namespace 长度。先写权限回归测试。
- [x] **HTTP 重放：**新增专用无秘密请求指纹组件；授权和快照校验后在映射凭据之前查询；指纹贯穿 coordinator 与 core。保留清零 finally 和错误路径。
- [x] **错误/文档：**采集专用错误 advice 返回稳定 code/message/traceId；添加 `external-collection` OpenAPI 分组，保留此前 parser 分组；说明执行/读取 scope、namespace/project claims、完整单设备与批量请求、SHA256、轮询和 SSE。
- [x] **真实验收：**测试专用 RSA JWT + HTTP server + H2 + 隔离 MINA SSH fixture；生产远端地址策略不放松。验证单设备/批量/输出、重复请求执行一次、不同请求409、权限拒绝、无秘密重放与 OpenAPI。
- [x] **全量验证：**`mvn verify` 和 `git diff --check`；不部署、不提交、不改动其他任务文件或前端。

## 验证命令

```powershell
mvn -pl device-ops-core,device-ops-adapter-persistence-jdbc -am test
mvn -pl device-ops-server -am test '-Dtest=ExternalCollectionHttpTest,ExternalCollectionOpenApiConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false
mvn verify
```
