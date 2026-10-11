# 序列号详情请求批量化验收

用户报告40条序列号记录产生121次请求。原实现是设备页1次及每台详情、维保、安装记录各1次，且历史维保还可能产生额外分页。

## 实现与边界

- AST设备分页直接返回bootVersion/cpldVersion/warrantyMonths/extendedCount。版本只取DEVICE_LOG可用来源，续保次数统计全部未删除续保记录；AST查询只访问自身表，维持原设备租户/组织/合同/项目可见性及分页。
- IMP新增当前安装地址批量只读接口，查询对象承载tenantId/projectId/equipmentIds（最多200）；由服务端项目VIEW和查询权限授权，仅返回指定项目/设备、status2、effectiveFrom存在、effectiveTo为空的记录。空集合失败关闭，历史记录不被查询结果替换或修改。
- 前端移除逐台详情/维保/安装HTTP，普通页不超过200条时2次业务请求；全局全部仍按200条分块。安装查询失败时保留可读位置快照，不逐台重试。旧详情、维保、安装历史功能保持。
- SQL放Mapper XML，新增接口定义已更新docs/design/10-api-design.md。无跨模块biz依赖，无数据库迁移或业务数据写入。

## 验收

- 后端6测试类13项通过，涵盖原设备权限、DEVICE_LOG与ITR隔离、完整续保统计及软删除/其他租户排除、安装历史/其他项目/未请求设备排除、项目范围拒绝和空/超200/null设备参数校验；前端运行回归4项及相关ESLint通过，package通过。
- 已部署指定59191服务，PID32916，数据库npdms_domain_test及Redis/DAC/端口保持。
- 真实Chromium项目992203060198恰好40台，选择全部后40行更新，仅1次设备分页+1次当前安装地址查询，业务请求2次，实际从选择到显示耗时0.666秒。无网络替身或新增设备。
- 项目1002手工安装地址与完整站点/机房/机柜/槽位地址保留；前两台版本和维保字段与既有详情/维保接口一致。未写业务数据。
- 证据`.run/dac-host/serial-batch-acceptance/acceptance.json`及40行截图，脚本`.run/dac-host/accept-serial-batch.py`，日志`.run/serial-batch-{tests,package,deploy,browser}.log`。业务请求数仅统计序列号表本次加载，不包含项目详情其他面板或登录请求。
