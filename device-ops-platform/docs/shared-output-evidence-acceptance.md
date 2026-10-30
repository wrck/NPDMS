# 共享输出与输入证据验收

日期：2026-09-08

## 已实现

- 连接与记录复用CollectionTaskPanel四页签，记录readonly没有执行按钮且imperative start也拒绝；解析结果集中在解析事实，侧栏仅配置。
- 独立evidence API恢复任务自身冻结脚本；新请求保存白名单实际提交快照，历史标RECONSTRUCTED_FACTS，不伪造原请求。
- 输入下载恢复UTF-8 BOM；受理后仅用服务端winner证据，不混用本地重试正文或提交时间。
- 终端命令pre深色，内嵌语义tab不被外层样式裁切，状态轨迹对比度修复，legacy合并命令级状态显示未知。
- 连接左卡header固定/body单滚动，配置紧凑，小屏自然流；Element Plus样式变量和组件复用。

## 自动化验证

- 最终Maven verify成功：413项、0失败、0错误、0跳过，包含真实隔离Docker MySQL与H2迁移/事务/权限测试。
- 最终前端22文件89项通过；类型检查、build通过。全量lint 0错误、562警告（未把既有格式警告写成零）。
- Chrome E2E 4项通过；采集fixture补齐证据API，两个路径均校验下载BOM+实际提交正文，保留console/pageerror无错断言。这是mock API回归，不替代真实环境。
- git diff --check通过。

## 真实分支环境部署

指定环境48181，原H2与原DPAPI/current-user密钥，local/Telnet true；callback/schedule/masterData禁用。不使用48182fixture。

先连续确认采集/解析空闲、核对PID与本工作区归属，再停机做离线私有备份、双文件hash验证。V19→V20成功，历史snapshot保持null。首次verify在运行JAR上repackage失败，随后部分指标出现类加载异常；停止自建进程、备份并重新完整verify恢复。此后从独立运行副本启动，禁止再次覆盖运行JAR。

最后CSS维护首次检测新增任务active1，未中断，等待其自然完成并多次稳定空闲后才维护。最终总数190与维护前一致，活动发布仍1.3.0，指定历史仍1.2.0，没有本次新采集、连接测试、重解析或发布操作。

最终运行PID41188，地址http://127.0.0.1:48181。运行副本位于target/deployed-runtime/20260908T094943039Z-v20-css；66个打包静态文件hash逐一匹配最终dist。不要清理正在运行的目录。

## 真实历史验真

任务4758371f-292f-45c1-b02d-b91e3249d7b0：

- evidence AVAILABLE、31 UTF-8字节、内容SHA与持久摘要一致、Cache-Control:no-store。
- provenance RECONSTRUCTED_FACTS，snapshot null；页面请求快照显示历史执行事实和明确省略字段，不再显示restoredCollectionId伪请求。
- 输入下载按钮可用，通过真实页面按钮事件收到了浏览器download事件；常规鼠标locator点击因IAB偶发超时，未冒充鼠标全程成功。下载字节完整性另由Chrome E2E验证。
- 解析事实页签selected=true且本次解析结果region只有1个；截图确认首目标展开、原1.2结果在页签内显示。
- 真实命令展开后computed style：pre背景rgb(16,24,32)、文字rgb(244,248,249)，白底已消除；输出pane高度约289px（1280×720）。最终步骤完成项rgb(99,213,199)、当前项rgb(244,248,249)，不再深字深底。
- 1280×720连接页截图确认三列与左卡滚动。只滚动到清除凭据按钮，截图确认用户名、密码、高级设置与底部操作可达，没有点击测试或执行。

## 视觉工具边界

IAB截图部分成功，部分返回browser screenshot activity capture failed for guest；部分locator点击超时，用明确页面元素事件检查后未将其等同于完整鼠标旅程。最终命令白底及步骤色以实际computed style复核，最终补丁的截图未成功。1024×768窄屏切换和控件存在已检查，但截图失败，完整窄屏视觉验收仍未确认。

未提交或推送Git。数据库和加密密钥备份位于工作区外私有目录，不进入仓库或文档正文。
