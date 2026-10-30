# 当前安装地址快照投影验收（2026-10-09）

按用户明确要求复用 `locationSnapshot.fallbackLocation`，统一设备当前完整地址的投影和读取。撤除上一增量新增的独立 installationAddress 字段及SQL读写依赖。

## 实现与数据保护

AST沿用位置生效事务、设备锁和版本CAS，按locationText补齐当前快照的fallbackLocation，保留站点等其他结构化内容。手工地址只产生文本字段，站点引用保持空，不伪造解析完成状态。位置历史仍原样保存命令的快照；幂等重放、冲突拒绝及旧安装关闭流程不变。

设备分页原locationSnapshot直接供序列号页消费，不进行安装补齐查询。200条以内每页一次请求，超过200条的全部展示仍遵循原全局分页分块行为；旧地址批量接口保留兼容。

V420已执行，不修改其内容或Flyway记录。前向V421按租户、当前位置指针、设备、项目、安装来源、生效时间和当前已完成状态严格匹配，补齐当前快照后移除V420引入的冗余列。实际核对24个当前安装投影：23个已有快照不改，仅17899180070026补齐手工文本；其他结构化内容不变。迁移前后62条位置历史和1181条安装记录逐行一致。

## 验证

- 后端13项通过：当前快照补齐、结构化内容及原始历史保留、手工地址不臆造引用、幂等重放、冲突/旧版本拒绝、设备可见范围、版本来源和维保统计、批量安装失败路径。前端4项运行回归及相关ESLint通过，后端package成功。
- 部署59191，PID12064，数据库npdms_domain_test；MySQL、Redis及DAC不重启。V421成功，冗余列已不存在。
- 真实Chromium项目992203060198选择全部显示40行，仅一次设备分页业务请求，0.630秒；版本和维保值与原详情接口核对一致。计数仅覆盖序列号表加载，不包含登录及项目其他面板请求。
- 项目1002完整站点/机房/机柜/槽位地址和手工地址均正确展示。从安装工作台维护手工地址并点击保存，返回已打开的序列号页立即更新。新增安装2108347143896870914已完成，当前设备快照fallbackLocation等于保存文本，站点引用为空，新增位置历史及安装快照仍为原始null；上一次安装正确关闭并保留。
- 证据：`.run/installation-snapshot-migration-evidence.json`、`.run/installation-snapshot-save-evidence.json`、`.run/dac-host/installation-address-projection-acceptance/acceptance.json`及40行截图、`.run/dac-host/installation-snapshot-save-acceptance/acceptance.json`和保存前后截图。测试/构建日志为`.run/installation-snapshot-{tests,package}.log`。

本记录只证明本次增量，不声明F-AST-001整体完成。业务验收新增记录保留，未提交或推送代码。
