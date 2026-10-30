# Inline Snapshot Content Implementation Plan

**Goal:** 请求快照 JSON 内完整展示授权任务冻结的 script.content，不新增 input 卡或其他区域。

**Architecture:** CollectionTaskPanel 使用集中 helper 复制并投影服务端 evidence。CAPTURED 保留原 snapshot 和 script 其他字段，仅由 AVAILABLE input 补入 body.script.content；省略列表只移除实际补入的精确字段路径。RECONSTRUCTED 使用带 provenance 的历史证据 projection（metadata、script、executionFacts），不构造完整请求 body。未取得、ERROR、RESTRICTED 不回退表单，UNAVAILABLE 不输出 content:null，AVAILABLE 空串保留。受理前本地冻结快照保留正文但继续删除凭据、callbackUrl、extensions、parserConfig；受理后 server winner 权威。

**Tech Stack:** Vue 3、TypeScript、Vitest、Element Plus、Playwright（本机 Chrome）。

## Steps
- 先更新 CollectionTaskPanel 与 transient-credentials 测试，验证 inline 正文、历史 projection、深色样式红测。
- 新增小型 request-snapshot helper 及测试，覆盖 EXTERNAL_DELIVERED / ADHOC_INLINE / LOCAL_MANAGED、空正文、不可用状态、复制不 mutation、精确 omitted 过滤、安全 sentinel。
- 接入 helper，同一快照 JSON 展示；请求区深色终端、紧凑 Element Plus 提示、独立滚动，facts 保持浅色。
- 适配相关旧单测和 e2e 预期，运行全量 pnpm test、ts:check、build 和 Playwright。
- 完成 ready 文件，交付红绿结果，由主代理部署。

## Boundaries
不改 DB、API、权限、后端 secret 白名单；不访问 48181、不连接设备、不 commit、不 root clean，保留已有工作区改动。不进行主 IAB 浏览器操作。
