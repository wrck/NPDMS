# 紧凑排版与字体层级精准优化（2026-09-18）

用户已明确设计指令：保留功能、字体层级、紧凑清晰、交互方便、去大块说明文字、简化次要信息。
已核对真实 48181 设置页/工作台 DOM 并完成源码逐项盘点。

## 边界（必须遵守）

- 不改 input names / API / 枚举 / 证据 `script.content` 逻辑。
- 小字最小 12px，不把 9-11px 缩回来。
- 不隐藏安全 / 证据标注（alert、role="note"、下载行 :845-847 保留）。
- 不动 workbench/standalone 高度、850px 断点、tabs 前置折叠。
- `el-empty` / `el-alert` 保留组件形态。
- 不引入外部字体、不改深色终端 / 请求快照样式、不改 `--el` 主色。
- LF 行尾；不 commit 不 push；不访问 48181/设备/root clean。

## 任务A：文案压缩（短句保留语义与安全警示）

| 文件 | 位置 | 现文案 | 新文案 |
| --- | --- | --- | --- |
| TargetSelector.vue | :209 | 关闭时使用通用采集接口，不创建虚构项目或设备。 | 关闭后不关联项目/设备 |
| ProtocolConnectionForm.vue | :505 | Telnet 会明文传输…（长句） | Telnet 明文传输，仅限受控网络（保留 el-alert） |
| ProtocolConnectionForm.vue | :515 | 临时凭据仅保留在本页；保存连接经验证后加密存储，敏感内容不回显。 | 凭据加密存储，不回显 |
| ProtocolConnectionForm.vue | :558 | 暂无保存的连接。填写下方信息并验证保存即可创建。 | 暂无保存的连接 |
| ProtocolConnectionForm.vue | :561 | 已保存凭据：…（两分支长句） | 凭据已保存 / 状态异常，请重新保存 |
| ProtocolConnectionForm.vue | :564 | 已保存连接有未验证修改；提交采集前必须验证并保存，或切换为临时连接。 | 有未验证修改，请先验证保存 |
| ProtocolConnectionForm.vue | :636 | 留空时不校验设备身份，可能受到中间人攻击；建议仅在可信网络中使用。 | 留空不校验设备身份（保留 role="note"） |
| ProtocolConnectionForm.vue | :648 | 自动模式适用于常见交互式终端；… | 删除（placeholder 已含语义；超时行 :625 保留） |
| ScriptArtifactEditor.vue | :117-119 | MVP 不伪造脚本列表… | 删除整段 security-note |
| ParserSidebar.vue | :111 | 平台未启用自动解析；可指定已发布版本，或仅保留原始输出。 | 未启用自动解析，可选已发布版本（保留 alert） |
| ParserSidebar.vue | :113 | 尚未启用默认解析版本。本次采集仍会完整保留原始输出。 | 未配置默认解析版本（保留 alert） |
| ParserSidebar.vue | :114-120 | 三分支长句 | AUTO→按默认版本解析，不丢原始输出；RELEASE→固定使用所选版本；不解析→仅执行采集，不生成结构化结果（保留） |
| ParserSidebar.vue | :123 | 暂无已发布解析版本。可稍后重试，或仅保留原始输出。 | 暂无已发布解析版本（保留 alert） |
| ParserSidebar.vue | :152 | 兼容解析只生成当前命令块的事实和告警，… | 兼容解析仅作用于当前命令块（保留 alert；:147 动态示例保留） |
| CollectionTaskPanel.vue | :858 | 连接建立后，设备输出会实时保留在此区域。 | 等待设备输出 |
| CollectionTaskPanel.vue | :863 | el-empty 两分支长句 | readonly→请从记录列表选择记录；非只读→尚未下发采集任务（保留 el-empty） |
| CollectionTaskPanel.vue | :894/:910/:931 | 三处截断句不一致 | 统一为「输出已截断」（:841 已是该文案） |
| CollectionTaskPanel.vue | :1024 | 证据查询暂时失败，原始输出不受影响，请重试证据。 | 证据查询失败，请重试 |
| CollectionTaskPanel.vue | :1025 | 证据访问受限，未提供原提交请求。 | 证据访问受限 |
| CollectionTaskPanel.vue | :1026 | 库创建时间（本地）：…（非浏览器提交时间） | 库创建时间：…（本地时区）（去长括注，保留一处短标注） |
| SemanticResultPanel.vue | :75 | 采集完成后自动显示结构化数据 | 暂无解析结果（保留 el-empty） |
| SemanticResultPanel.vue | :100 | 型号冲突警示 | 保留原样 |
| SettingsManagementView.vue | :19/:38 | 长副标题 / 长 alert | 只读身份与能力视图 / 能力开关不代表实际授权（保留 alert） |
| OverviewManagementView.vue | :41 | 仅统计当前身份可访问的采集范围，不包含无权访问的数据。 | 仅统计授权范围内数据 |
| RecordsManagementView.vue | :17 | 授权范围历史检索；未知历史字段不伪造。详情复用… | 授权范围历史检索 |
| TasksManagementView.vue | :15 | 采集、解析与到期通知分开管理；停止查看不会取消设备执行。 | 采集、解析与到期通知分开管理 |
| ScriptsManagementView.vue | :42 | 仅展示授权采集引用的不可变版本。安全读取与载入工作台是两个独立、显式操作。 | 仅展示授权引用的不可变版本 |
| ScriptsManagementView.vue | :137 | 仅在当前会话内存中一次性交接，不保存到浏览器存储，不写入 URL。 | 仅内存一次性交接，不落盘（保留） |
| ParserManagementView.vue | :120 | 草稿 → 样例验证 → 发布 → 显式激活；变更始终保留并发绑定校验。 | 草稿 → 验证 → 发布 → 激活 |
| ParserManagementView.vue | :282 | 输入完整 ReleaseRequest JSON，包含 manifest…上限 32 MiB。 | 输入完整 ReleaseRequest JSON，上限 32 MiB |
| ParserManagementView.vue | :317 | 指定已发布 release…输入示例（stdout 为实际日志）+ 内联 pre | 留空使用当前活动版本，上限 X 字节；JSON 示例移入 el-collapse「输入示例」（默认收起） |

## 任务B：字体层级（显式声明、稳定对比）

token（main.css :root 新增，不引外部字体）：

- `--ops-fs-h1: 1.25rem` / `--ops-weight-heading: 600`
- `--ops-fs-h2: 1rem`（面板 h2 15-16px 区间取 16px，workflow-grid 紧凑位 0.9375rem）
- `--ops-fs-h3: 0.875rem`、正文 14px/400、辅助 12-13px/400 slate

落点：

- AppShell.vue :326-330 h1 显式 `1.25rem/600`；subtitle 显式 `0.75rem/400`（12px muted，与 label 区分）。
- main.css :775 `.workflow-grid .panel-header h2` 0.875rem → `0.9375rem/600`；:131-135 `.section-heading h2` 显式 600。
- main.css :330-336 / :798-802 表单 label 显式 600；helper 保持 400；输入值 14px 不变。
- management.css :14-17 卡片头 strong 保留 16px/600；:71 h3 改 `0.875rem/600`，与卡片头不再同字号。
- PanelHeader.vue 默认 h2 补 `font-weight: 600`（1rem 面板标题）。

## TDD

1. 红：`src/management/presentation.spec.ts` 新增「长句禁用 + 短文案」契约断言；更新既有凭据断言为新短句。
2. 红：新增 `src/styles/typography.spec.ts`，读取真实 CSS 并以 computed style 断言关键值（jsdom 不做样式表级联，采用「解析规则→内联应用到探针元素→getComputedStyle 断言」方式，断言走 computed style 而非整段字符串匹配）。
3. 绿：实施任务A/任务B。
4. e2e 7 项（collection 1 + output-tabs 3 视口 + management 3）全绿；文案断言如有受影响同步更新。
5. `pnpm test` / `ts:check` / `lint`（0 errors）/ `build` 全绿。
