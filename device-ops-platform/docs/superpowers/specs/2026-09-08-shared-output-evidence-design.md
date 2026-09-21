# 共用采集输出与证据恢复

状态：本会话方案已批准，包含备份后本地48181的V20升级；不包含设备命令、连接测试、历史重解析、活动发布切换、Git提交或推送。

## 用户目标

记录复用连接工作台输出界面并去除执行按钮；请求快照对应真实请求的安全投影；恢复输入记录下载；标准输出命令正文无白底；解析结果直接在解析事实中显示；连接左侧字段和操作完整可达，内容集中。沿用Element Plus与现有AppShell，不重造输出界面。

## 组件职责

CollectionTaskPanel保留标准输出、错误输出、解析事实、请求快照四tab。Records只读，连接仍可提交；SemanticResultPanel只在facts内部出现一次，传当前全部目标结果和details，兼容事实按目标分组。首次展示默认展开单目标或首目标，轮询不改变用户选择，不抢切tab。ParserSidebar仅配置，Project与Records移除外部重复结果。

## 证据契约

独立evidence API按任务namespace/project和read scope授权，Cache-Control:no-store，普通details不携带脚本正文。历史读取collection自身冻结script_content、SHA及已存context/endpoint/resolved parser事实，来源标RECONSTRUCTED_FACTS；没有完整原请求，不编造timeout/savedConnection引用/用户原selection。输入AVAILABLE、UNAVAILABLE、RESTRICTED明确区分，下载保留UTF-8 BOM脚本文本。

新请求在连接/解析resolution之前按白名单捕获安全投影，route/idempotency元数据与body分开，凭据不序列化，自由字段/回调敏感部分省略并明示。新V20 nullable快照与任务同事务写入，幂等winner不可被重放覆盖。历史保持null不回填假快照，script正文不重复存储。前端同一冻结POST body生成即时显示，受理后以服务端证据为准，异步失败重试和任务切换隔离，不持久化内容到浏览器存储。

## 布局与语义

终端pre明确深底浅字，management通用pre样式限制范围；外层tab样式不侵入语义内部tabs。事实和request区域使用Element Plus浅色内容主题；状态步骤对比度可读，PARTIAL_SUCCESS中性，legacy合并输出不把合成成功标签当命令证据。共享输出有独立高度和单一主要滚动区。

桌面连接卡header固定/body单scroll，避免卡片与表单双裁切；小屏自然流页面scroll。解析配置紧凑，脚本元数据中宽度可换行，保留浮层可见。

## 验证与环境

子任务先红后绿，覆盖安全快照、历史恢复/下载、并发幂等、H2/MySQL迁移、权限/大小写/fallback星号、四tabs/只读/多目标/异步竞态。完整测试、类型、lint、build与JAR资源一致性后，再备份原H2及DPAPI，停止已验证本工作区48181进程，原key/原cwd/原库重启应用V20。真实旧任务4758371f-292f-45c1-b02d-b91e3249d7b0只读验真，不重新采集或解析。截图和真实操作验证，工具失败明确记录。
