# 采集输入与请求证据

## 输出界面

连接工作台和历史记录复用同一输出组件，提供标准输出、错误输出、解析事实、请求快照四个页签。记录只读，不提供设备执行操作；连接工作台保留提交入口。解析结果与按目标的兼容事实均在解析事实页签展示，侧栏仅保留解析配置。

## 请求证据不等于含秘密的原始报文

### 新提交：CAPTURED_SUBMISSION

服务端保存实际提交的白名单安全投影。结构为：

```json
{
  "schemaVersion": 1,
  "request": {
    "method": "POST",
    "path": "/api/v1/collections",
    "idempotencyKey": "request-idempotency-key"
  },
  "body": {}
}
```

body保留允许展示的原始请求选择，不用连接解析后的默认值替换。连接密码、私钥、口令、脚本正文及未准许的自由字段不进入快照；omittedFields列明省略路径。脚本正文通过独立input返回，不在快照重复保存。首次受理快照与任务同事务保存；幂等重放不得覆盖原快照。

为限制请求快照捕获的内存占用，单设备与项目批量采集 POST 请求体上限为 **4 MiB**，超过返回 413；这是采集请求上限，不修改离线解析 API 的独立输入上限。捕获器流式跳过凭据值，自持有重放缓冲区在请求结束清零；不声称 JVM/Jackson 内部所有临时内存均可被主动擦除。

### 历史任务：RECONSTRUCTED_FACTS

旧库没有完整原始请求。接口只返回已保存的脚本身份、目标端点和最终解析选择等执行事实，snapshot为null。缺失的原始超时、保存连接引用、请求路由及用户最初解析选择不反推、不填默认值。数据库createdAt不是浏览器提交时间，旧null保持未知。

## 独立查询

- `GET /api/v1/collections/{collectionId}/evidence?namespace=...`
- `GET /api/v1/projects/{projectKey}/collections/{collectionId}/evidence?namespace=...`

需要`device-ops:collections:read`并满足任务namespace/project授权；响应`Cache-Control: no-store`。普通任务详情不返回脚本正文，不因证据下载放宽脚本目录权限。

响应按四类分组：

- `metadata`：collectionId、namespace、projectKey、externalRequestId、activityType、createdAt。
- `input`：任务自身的source/key/version/policy/parserType/sha256、contentStatus和content。
- `submission`：provenance、snapshot和omittedFields。
- `executionFacts`：目标事实与已解析的semanticParsing坐标。

输入从任务自身冻结的script_content读取，不读取当前脚本版本来替代历史。支持有权限的LOCAL_MANAGED、EXTERNAL和ADHOC任务，不限于登记策略。内容可能包含敏感运维命令，仅获授权调用者可读，不读取或解密保存凭据。

## 下载与失败处理

下载输入记录仍为UTF-8 BOM纯脚本文本。AVAILABLE与UNAVAILABLE明确区分，空字符串不自动等同于未取得。权限拒绝或证据读取失败时不伪造下载内容，提供明确状态/重试；标准输出查询继续可用。前端不把内容写入localStorage或URL。

## 迁移与验真

V20用于可空的版本化提交快照，历史不回填伪快照。H2/MySQL均需迁移兼容测试。分支本地验真使用48181原持久H2、原DPAPI密钥；升级前备份并校验，不使用48182合成数据替代，不执行设备命令或重解析历史任务。
