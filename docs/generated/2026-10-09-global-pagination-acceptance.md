# 全局分页“全部”能力验收

用户明确要求公共分页全局支持全部，不逐页启用或维护专门适配。本次取代前一轮allowAll/-1方案。

## 实现

- 公共Pagination默认始终提供全部选项，无allowAll属性。选择全部时重置第一页并向消费者传实际正数总条数；总条数变化时更新，切回10/20/30/50/100恢复分页。既有本地数组slice无需新语义。
- 统一request.get入口对pageNo/pageSize超过200的分页读取按200条拆分、合并list，保留其他查询条件、headers及请求选项，不修改调用方参数；非分页及不超过200的GET完全沿用原请求。服务端PageParam及授权保持原样，不自动重复写请求。
- 普通大分页保留原offset/size语义；无匹配返回空列表，结构不匹配、后续空截断或接口拒绝均失败，不交付部分结果。
- 移除序列号详情专有全部循环、配置Log -1分支和两个页面allow-all属性；配置Log已上传过滤仍保持。

## 验证

- 公共组件、公共请求层及两个消费者共4测试文件8项运行回归通过；相关ESLint及diff检查通过。
- 真实Chromium项目1002序列号详情11台选择全部并切回10条、上下翻页通过；配置Log真实11台仅2台已上传，分页网络替身25台支持全部/20/5，保留无日志排除。
- 未适配页面 `/customer-asset/devices` 使用真实浏览器和401条网络替身，默认10条，选择全部后经公共请求层发3次200条合法请求合并401行，再切回10条。未创建401条业务数据，业务写入0。没有让真实百万设备库全量渲染作为测试。
- 复现脚本 `.run/dac-host/accept-global-pagination.py`、`accept-serial-pagination-all.py`、`accept-config-log-all.py`；公共入口结果 `.run/dac-host/global-pagination-acceptance/acceptance.json`；日志 `.run/global-pagination-*.log`。

## 边界

适用于使用公共Pagination且通过公共request.get读取list/total分页的页面，以及公共组件驱动的本地分页；自建分页器或非标准返回/绕过公共客户端的请求不由本改动隐式改造。全部读取量和渲染量随实际数据规模增加，保留用户明确选择行为，默认页数不变。无后端重启、迁移或提交。
