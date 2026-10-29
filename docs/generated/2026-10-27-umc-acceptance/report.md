# UMC 离线巡检实测记录

测试日期：2026-10-27。目标：`https://10.121.230.208/`。执行范围：需求方明确授权使用测试账号登录、创建离线巡检任务、上传日志和下载报告。

## 结论

已完成真实接口登录、测试任务创建、有效日志上传、结构化结果读取、DOC 下载和正常注销。有效样本识别出 VPN1000-GA-X、SYNTHETIC-SN-001，并形成两项检测结果，其中一项异常。报告文件经 XML 解析确认包含设备及检测项内容。

当前环境实测可下载的是 Word 2003 XML 内容的 `.doc`。创建任务时请求了 `doc=true,pdf=true`，最终所有测试报告仍为 `pdf=false`，PDF 下载失败；XML、DOCX 下载同样返回“文件不存在”。这不能扩大为所有 UMC 版本均不支持 PDF，只能说明本测试环境尚未完成 PDF 生成。

## 测试方式与数据边界

- 使用 Node 24.11.1 内置 HTTPS、crypto、vm 和文件能力执行临时 API 探针，沿用交付脚本的登录协议。原始交付脚本未修改；本次结果不代表原 CLI 原样运行已通过。
- 登录密码、加密表单及 Cookie 仅在进程内存使用，未写入证据文件。对登录加密脚本检查后，在禁止动态字符串编译、无 Node 能力暴露且有时间限制的 VM 上下文执行。
- 浏览器插件连接仍报 EPERM，因此本次是实际 API 验证，未完成真实浏览器 UI 验收。
- 测试地址存在证书名称不匹配；先用无凭据请求核对证书，再仅对该 IP 固定当前证书 SHA-256 指纹并保留证书链验证。未修改系统、Node 或 Java 的全局 TLS 设置。
- 输入来自仓库已脱敏的 `device-ops-platform/parser-releases/device-command-output-1.4.0/input-real-sanitized.json`。SN、提示符、IP 为合成测试标识；没有连接实际设备，没有上传未脱敏原始日志。
- 四个测试任务均为 `type=1` 离线任务，`send=false`，未操作在线连接、规则配置、通知或既有任务。测试任务及报告保留作为追溯证据。

## 登录与退出

| 操作 | 结果 |
|---|---|
| 获取登录挑战与页面参数 | 成功，能解析 c/cn/cp |
| 测试账号登录 | HTTP 200；后续任务列表 HTTP 200、业务 code=200，鉴权成功 |
| `GET /UMC/Logout.action` | HTTP 200 |
| 注销后重新查询任务列表 | HTTP 401，当前测试会话已失效 |

证书指纹及请求轨迹见 `evidence.json`，不含 Cookie 或登录表单。

## 输入格式对比

样本均以同一组脱敏 `show version`、`show run`、`show tech` 输出为基础，所用 IP 仅为测试区分标识。

| 输入 | 上传响应 | 实际解析结果 |
|---|---|---|
| 直接拼接 stdout，LF | HTTP 200 | 型号/SN 为空，0 条规则，info=没有可用的巡检规则 |
| 直接拼接 stdout，CRLF | HTTP 200 | 同上，不能仅靠换行转换解决 |
| 每条命令恢复 `<synthetic-device>命令` 提示符，CRLF | HTTP 200 | 识别型号、SN、软件版本，执行2条规则 |
| 每条命令使用 `synthetic-device#命令`，CRLF | HTTP 200 | 对本样本没有识别出设备，0条规则 |
| NPDMS 的 schemaVersion/commandBlocks JSON | HTTP 200 | 未形成有效设备及检测结果，0条规则 |
| ZIP 内容，使用合法的“名称(IP)”文件名 | HTTP 200 | 未解压为有效日志，型号/SN为空，0条规则 |

有效样本说明：尖括号提示符是从这组输入对比得到的兼容条件，不能推定所有厂商、型号、提示符及编码均已覆盖。新增提示符是测试格式转换，不能作为真实设备执行来源证明。生产转换须使用有来源的设备标识和已脱敏命令输出，保留转换前后摘要及关联。

一次 multipart 中上传四个重复 `files` 字段成功，并形成包含四条设备记录的报告；其中只有尖括号提示符样本得到有效检测。该测试证明批量传输可行，不代表四台不同真实设备的业务验收。

## 文件名限制

| 文件名 | 结果 |
|---|---|
| `NPDMS_VALID(192.0.2.20)` | 接受并形成有效检测 |
| `192.0.2.21` | 接受并形成有效检测 |
| `192.0.2.21.txt` | HTTP 500，文件名不合法 |
| `CaseExtension(192.0.2.22).log` | HTTP 500，文件名不合法 |
| `input-logs.zip` | HTTP 500，文件名不合法 |

平台可以继续允许用户上传自身支持的日志/归档格式；调用 UMC 前应完成既有脱敏及必要拆分，转换为已验证的文本格式，并单独设置 UMC 要求的无扩展名上传文件名。

## 报告与导出

| 请求 | 有效报告685的结果 |
|---|---|
| `GET /umc-api/dev/inspection/report?id=685` | HTTP 200，直接返回结构化对象，含 devlist/rulelist/targetlist |
| `POST .../report_download`，type=doc | HTTP 200，1,300,463字节，实际为 Word 2003 XML |
| 同接口，type=pdf | HTTP 500，文件不存在；报告已完成且等待超过180秒后复查仍失败 |
| 同接口，type=xml | HTTP 500，文件不存在 |
| 同接口，type=docx | HTTP 500，文件不存在 |

DOC 响应 Content-Type 为 `application/octet-stream;charset=UTF-8`，不能按响应 MIME 推定真实格式。XML 根元素为 `{http://schemas.microsoft.com/office/word/2003/wordml}wordDocument`，不是 OLE DOC、OOXML DOCX，也不是平台结构化检测 XML。

有效报告包含：

- 型号：VPN1000-GA-X；SN：SYNTHETIC-SN-001；软件版本：B311CM005D025P02PATCH75。
- 规则1“软硬件检测”：support=true、error为空、result=true；页面契约将 result=true 呈现为异常，原因包含 AV/APP/IPS 特征库检查。
- 规则85“接口延迟up配置检查”：support=true、error为空、result=false。
- 仅覆盖本样本匹配的2项，不代表 UMC 的110条规则或 NPDMS 的完整冻结规则集合均已执行。

下载文件：`report-685-requested-doc.doc`；SHA-256：`27c83d7948ab48375daf4bf3afc190c6bad92231c41faa54e7213a88306cc6d9`。

## 远端记录

任务名前缀：`NPDMS_UMC_20261027_mvqodcsb_`。

| 任务ID | 后缀 | 报告ID | 用途 |
|---|---|---|---|
| 69 | text_doc_pdf | 683 | 无命令提示符的直接拼接输入 |
| 70 | input_formats | 684 | 四种输入的批量对比 |
| 71 | valid_prompt | 685 | 独立有效样本及导出验证 |
| 72 | filename_probe | 686、687 | 合法/非法文件名及ZIP内容对比 |

任务最后均回到 status=1，UMC 页面定义为“待启动”。没有删除测试任务或报告，没有改动既有业务记录。

## 对集成方案的直接影响

1. **上传成功不等于检测有效。** 0条规则且 info 说明无可用规则的报告也可生成 DOC，原脚本仍可能输出 ok=true。平台必须检查设备身份、规则覆盖、support/error及预期检测项完整性，不能据此认定巡检通过。
2. **增加结构化详情读取。** 原脚本没有调用 `GET .../report?id=...`；实际服务提供该能力。结果应按平台任务、执行尝试及设备映射归档，UMC 的 deviceid/ruleid 不能直接当成 NPDMS ID。
3. **轮询文件就绪。** 报告685首次出现时 doc=false、pdf=false，后续 doc 才变为true。不能仅以报告记录存在就立即下载并判定成功。
4. **保留标准格式边界。** UMC WordML 原件作为外部文件证据保存；当前平台 PDF/DOCX/XML 生成要求仍须满足。是否受控转换或组合生成需形成明确方案，不绕过 DOC 内容校验。
5. **保留平台时间。** HTTP Date 与报告时间均为2026-04-28，而客户端执行日期为2026-10-27；原下载名称也使用4月28日。外部时间保留为来源值，平台操作时间单独记录，唯一性和任务关联不依赖远端日期。
6. **收尾释放会话。** 已验证正常注销入口与注销后的401，适配器应在适当生命周期释放自身会话，并协调同账号并发。
7. **按接口处理响应。** 创建/上传成功返回空体，列表使用 code/data，报告详情直接返回对象，部分参数校验错误返回HTTP 500纯文本。不能用统一假定的Envelope解析所有UMC接口。

尚待确认：更多型号/日志样本、完整命令清单与规则映射、UMC PDF生成条件及失败原因、正式多设备报告组合和安全格式转换策略。未进行 NPDMS 功能实施、迁移、部署或浏览器端到端验收。

## 证据文件

- `evidence.json`：任务、上传、下载、最终状态、时间、证书及无凭据请求轨迹。
- `report-details.json`：本次五份测试报告的结构化详情，全部来自本次脱敏合成样本。
- `accepted-prompt-log.txt`：得到有效检测的提示符文本，上传时使用合法的无扩展名文件名。
- `rejected-semantic-command-blocks.json`：上传被接收但未得到有效检测的命令块样本。
- `report-683-requested-doc.doc`：0规则输入仍生成的文档，用于证明文件生成不等同检测成功。
- `report-685-requested-doc.doc`：实际包含设备识别和两项检测结果的报告。

集成方案：`docs/reference/2026-10-27-umc-inspection-integration-analysis.md`。
