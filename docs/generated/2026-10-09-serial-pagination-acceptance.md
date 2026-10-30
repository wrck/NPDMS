# 序列号详情分页修复验收

用户反馈序列号详情分页不起作用。真实Chromium复现项目1002（11台），切换每页10条后点击下一页，页码从2返回1；网络先请求pageNo=2，随后又请求pageNo=1。

原因：ProjectSerialPanel每次load先将total清零，ElPagination据此裁回第一页并再次触发加载。最小修复为翻页加载时保留已知总数，接口返回更新总数；项目切换仍清空总数和查询条件。保留分页接口、项目权限、当前页安装地址和维保补齐。

运行回归3项及ESLint通过。真实浏览器修复后：每页10条、第一页10台，下一页保持页码2且仅1台，网络只请求第二页；上一页恢复10台。未写业务数据，无后端部署。证据在`.run/dac-host/serial-pagination-acceptance/`，前后日志`.run/serial-pagination-browser-before.log`与`.run/serial-pagination-browser-after.log`；复现脚本`.run/dac-host/accept-serial-pagination.py`。

## 分页“全部”选项

通用Pagination新增allowAll能力（默认关闭，消费者需支持-1语义才开启），已在序列号详情与设备日志归档/配置Log启用。全部状态只显示一页，切回固定条数重置第一页。其他已有分页消费者保持现有接口语义，未把-1直接传给不支持的后端。

序列号详情按当前项目及查询条件逐页200条读取并合并，保留授权API与当前地址、维保补齐；取消旧请求时停止后续分页，空截断响应显式失败。配置Log在已上传设备结果内直接展示全部，仍排除无日志设备。

运行回归4项通过，含201台跨页及截断失败。真实浏览器项目1002全部11台显示、切回10条及下一/上一页通过；配置Log真实2台已上传设备保留，浏览器网络替身25条已上传设备验证全部显示与切回20条分页，不写业务数据。复现脚本`.run/dac-host/accept-serial-pagination-all.py`和`.run/dac-host/accept-config-log-all.py`。
