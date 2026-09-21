# NPDMS 接入

本目录独立构建、独立进程运行、使用独立数据库。来源见 [UPSTREAM.md](UPSTREAM.md)。
NPDMS 网关基于 `codex/liteflow-remediation` 的 `c90daf62e` 增量扩展，未合入该分支的其他业务改动。

## 运行配置

1. 在本目录执行 `build.ps1`，或分别构建 `device-ops-web` 和本目录 Maven reactor。
2. DAC 启用 `npdms` profile；默认端口 `48182`。NPDMS 在原有 profile 集合中追加 `dac`。
3. 两个宿主机进程通过受控环境注入相同的 `NPDMS_DAC_REQUEST_SIGNING_KEY`、
   `NPDMS_DAC_CALLBACK_SIGNING_KEY`，分别用于任务执行授权和结果验签，均至少 32 个 UTF-8 字节。
   配置文件不保存密钥。
4. `NPDMS_DEVICE_OPS_BASE_URL` 指向 DAC；`NPDMS_DAC_CALLBACK_URL` 指向 NPDMS
   `/api/v1/pms/integration/device-ops/callbacks`，默认开发后端为 `127.0.0.1:58080`。
   DAC 的 `NPDMS_DAC_CALLBACK_HOSTS` 只允许登记的回调主机。非本机 HTTP 端点拒绝启用。
5. DAC 保留原 OIDC 验证。NPDMS 使用专用服务身份的 `NPDMS_DEVICE_OPS_BEARER_TOKEN`；
   该身份需要 `device-ops:collections:read`、`device-ops:collections:execute`，以及对应项目和
   `npdms-<tenantId>` 命名空间范围。身份令牌由部署侧注入及更新，不使用用户/管理员会话。
6. NPDMS 通过自己的 Flyway 流程应用 `V325__fint012_device_ops_gateway_dispatch.sql`。
   DAC 自己执行本目录内的 Flyway，不能指向 NPDMS 数据库。

默认不启用 NPDMS 的 DAC profile。原有 DAC 独立前端、原生接口和解析能力保留。

## 请求和回传

- `POST /api/v1/npdms/collections`：复用原生集合请求体，增加 `Idempotency-Key`、
  `X-DAC-Grant-Time` 和 `X-DAC-Grant`。任务授权 60 秒有效，绑定租户命名空间、平台任务、项目、
  设备、连接目标、用户名、协议、冻结脚本及回调目的地。服务身份授权仍由 DAC 验证。
  平台任务号同时是 DAC 的幂等键；相同任务不会再次执行。
- `GET /api/v1/npdms/collections/{platformTaskId}?namespace=...`：精确查找任务，
  用于下发结果不确定时恢复外部任务号，不依赖有限页数的管理列表扫描。
- `POST /api/v1/npdms/collections/{platformTaskId}/cancellations?namespace=...`：请求停止。
  排队任务清理秘密后取消；执行中的任务中断退出后记录实际终态。已经终止的记录不重写。
  当前必须命中执行所属的 DAC 实例；其他实例返回 409，不伪造跨实例取消成功。
- `POST /api/v1/npdms/collections/{platformTaskId}/result-redeliveries?namespace=...`：
  将死信中的同一终态事件重新投递，不重新采集、不重建日志。对账任务使用此入口恢复回传。
- DAC 从不可变 Outbox 载荷生成日志，发送 `metadata` 和 `log` 两个 multipart 部分。
  请求包含 `tenant-id`、`X-DAC-Timestamp`、`X-DAC-Nonce`、`X-DAC-Signature`。
  验签内容为 `timestamp + LF + callbackId + LF + SHA256(metadata原字节) + LF + SHA256(log)`。
  时间窗 300 秒；同一 callbackId 的相同证据幂等，不同证据拒绝。
- 只有返回匹配 callbackId、正数 receiptId 和 `ACKNOWLEDGED` 才表示接收完成，HTTP 200 本身不够。

INT 流式验证并转交日志，文件平台统一执行大小、类型、摘要及配置的扫描策略。
DAC 的 JSON 日志原文按文本日志（`.txt` / `text/plain`）归档，字节及签名摘要不变，复用现有内容嗅探规则。
扫描拒绝时保存隔离证据，不生成可下载的 FileVersion；正常日志保存为不可变文件版本和受控引用。
存储回执以租户、回调及内容摘要生成稳定操作号，业务事务失败后可复用原存储回执。
日志明确截断时回传失败，不将其解释为完整成功证据。

PLT 原有完成模式保持不变：`BUSINESS_CONSUMPTION` 必须由匹配的业务对象确认消费，
DAC 技术成功不会直接完成配置调试记录。

## 当前边界

2026-09-21 用户明确授权先以手工命令完成业务接入，不以命令模板发布为前置。
在配置调试列表打开“手工命令 / 日志”，填写设备连接地址、临时用户名/密码和逐行命令。
复用配置调试的查询/修改权限及项目范围；已完成记录仅查看历史，不能继续下发。
每次执行保存平台任务与配置记录的不可变关联、操作者和命令摘要。按用户后续要求，V327 起另外保存
实际下发的逐行命令快照供执行历史展示；不保存连接密码，命令栏不得填写密码或密钥。
旧记录的命令快照保持空值，可在归档日志的 commandBlocks 中查看实际命令，不反推或回填历史。
同一个请求键不会再次执行；改变命令、目标或记录版本时不能重用请求键。

日志回传后可以下载并“关联日志”。关联调用既有业务消费契约，事务保存结果版本，重复关联幂等；
不会修改配置记录的调试状态、手工上传日志或已完成历史。下载仍需文件下载权限和当前项目范围。
页面支持直接查看设备输出及错误输出，关闭弹窗时清空预览内容。HTTP 网卡 IP 页面使用兼容 UUID，
下发结束清空密码并恢复按钮，运行中每 2 秒刷新历史；终态或关闭弹窗停止轮询。
数据库文件存储使用短时随机下载票据，Redis 只保存票据摘要及冻结的文件绑定，失效或绑定变更后返回 404；
原公开文件路径不能读取业务存储回执对象。S3 保留原签名 URL，不切换全局存储。
SHELL 模式下，命令回显之后的首个有效响应明确为 `% Unknown command.` 时，新任务按
`COMMAND_REJECTED` 失败回传，即使设备技术退出码为 0；原始输出和历史任务不改写。
认证失败、连接失败等受控原因码一并回传，页面显示具体原因；失败任务只能查看日志，不能关联为成功结果。
取消只请求停止，最终状态以 DAC 退出与回调为准。查询失败或结果不确定时由对账任务恢复，不能自动重用密码重发。

业务 API 为 `/api/v1/pms/implementation/configurations/{configurationId}/collections`，
与仓库其他管理业务 API 一致，在管理端装配 `/admin-api` 前缀。POST 下发、GET 分页；
`/{id}/consume`、`/{id}/cancel`、`/{id}/download` 均为 POST。
NPDMS 另需应用 `V326__imp_configuration_manual_collection.sql`。

当前使用每次输入的临时凭证；已保存凭证取密、命令模板管理、割接及巡检不属于这次手工下发闭环。
独立 DAC 前端保持原有入口。运行验收结果及真实设备边界见专项接入记录。

## 长命令与连接超时恢复

DAC 原生提交为异步执行，HTTP 202 表示接收；SSE 每次连接约 30 秒，原生页面按最后 sequence
重连并同时轮询。SSE 断开不取消执行。真正的 EXECUTION_TIMEOUT 则结束设备执行并保留已收到输出，
不能通过重新查询将该历史任务改为成功。实现分别位于 GenericCollectionController、
CollectionSubmissionCoordinator、CollectionOutputStreamService 和原生页面 CollectionTaskPanel.vue。

NPDMS 配置调试手工执行使用独立的整组命令总预算，配置项
`pms.integration.device-ops.manual-execution-timeout-seconds`（环境变量
`NPDMS_DAC_MANUAL_EXECUTION_TIMEOUT_SECONDS`，默认 600 秒）。这与 HTTP 读超时、SSE 连接时限分别配置；
其他消费者保留原命令时限规则，来源由 PLT 已保存任务提供，不由浏览器指定。

NPDMS 到 DAC 的同步请求出现连接/响应超时时，先按同一 platformTaskId 重连查询一次。
确认存在则恢复外部任务映射；重连仍失败或尚未查到时进入 RECONCILING，由既有 Quartz 对账继续查询
并请求原终态事件重新投递。恢复过程不再次 POST 设备命令、不保存密码供重发。
浏览器下发响应丢失时使用 `GET .../collections/by-request-key?requestKey=...` 精确找回本次任务；
保持配置查询权限及当前项目范围校验，查询暂时失败仍继续轮询，终态停止，关闭页面停止前端查询，
后台对账不受页面关闭影响。

## 取消与页面刷新

运行中的取消通过 ExecutionConnectionContext 的取消标志传到 SSH/Telnet 执行循环，
等待执行器退出后，由调度器持久化 CANCELLED 并触发原有签名结果回调。调用方取消不再中断
同时承担 JDBC 写入的工作线程，避免文件 H2 的通道在提交输出时被中断关闭；已收到的安全输出保留。
等待队列取消仍按原调度规则处理，终态不会因晚到的取消请求而改写。

页面后台轮询不显示整表加载遮罩，终态及关闭弹窗时停止。存在待确认任务时可暂停或继续页面自动刷新，
暂停不停止服务端对账，手动刷新也不会隐式恢复自动刷新。DAC 未找到的未知请求保留结果待确认状态，
不会因此伪造取消结果或重新下发设备命令。

对于未找到但仍处于下发结果待确认的请求，可再次请求取消。DAC 的 V21 迁移增加不可变提交保护记录，
提交与取消共用 `(namespace, idempotency_key)` 唯一约束；取消先落库则迟到提交被拒绝，
提交先落库则转回原执行器取消流程。既有任务迁移时初始化为已接收，历史数据不重写。
不存在的请求没有项目归属，建立取消记录只允许该命名空间具有全项目访问权的服务身份；
业务用户仍须通过 NPDMS 的配置编辑权限、租户和当前项目范围校验。
精确查询返回 HTTP 410 与 `X-DAC-Cancellation: BEFORE_DISPATCH` 才证明未下发取消；
普通 404 继续表示未知。NPDMS 经状态机和并发条件校验后显示“已取消，命令未下发”，
取消记录可供后台对账恢复，页面随后停止查询，不会生成虚假的执行日志。
