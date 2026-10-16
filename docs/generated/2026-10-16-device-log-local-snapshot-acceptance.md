# 设备日志本地完整解析快照与 DAC 离线查看验收

日期：2026-10-16。来源：需求方明确批准“已有解析结果补存本地，不要重新上传或解析，DAC不可用时也能查看”，并批准本次59191部署及最多90秒临时暂停DAC验收。对应Task：F-IMP-004。

## 实现与边界

- AST新增`ast_device_log_parse_snapshot`保存原完整脱敏结果，并以明确列绑定归档文件、租户、项目、原任务、解析版本、文件引用、产物版本与来源摘要。原任务行锁加快照当前读串行化首次补存，仅插入，不覆盖。归档查看与已完成且已投影的状态查询使用本地结果，不获取DAC Provider。
- PLT独立上传及命令采集解析在原记录`result_snapshot_json`补存一次原成功响应，包含原警告和完整结果；在原解析行锁下写入，禁止替换同任务历史结果。详情仍先验证租户、项目及采集来源/文件可用性，再读本地；分页查询不加载完整快照正文。设备投影失败的既有幂等重试改用本地冻结结果，保持原行为，不重新解析。
- 原任务成功结果由原授权查询接口逐条补存，不提交解析、不上传文件、不连接设备。未补存且解析服务不可用时明确提示待恢复补存；未完成任务不伪造成功。本地缓存不会绕过项目范围、文件隔离、归属或SN校验。

## 验证与发布

1. `DeviceArchivedLogParsingServiceTest`16项、`DeviceLogParsingServiceTest`13项通过。包含离线结果与状态查询、原警告保留、来源/租户/范围/文件隔离拒绝、错误任务结果拒绝、并发首次补存不可替换及设备回写失败从本地重试。
2. 通过Docker Compose/Flyway仅执行前向V494、V495；迁移前后validate通过，既有安装记录及校验和未变化，不夹带未应用的其他迁移。首次前置validate因预期的pending V494提示失败，随后仅在前置校验中显式允许pending；迁移后使用常规validate通过。
3. 构建使用工作树工具链、依赖仓库及独占锁。首次产物被既有修复保护拒绝；经来源差异自审，对本次授权变更更新精确后继批准，嵌套ResultView的javap实现比较一致。保护程序支持同一基线下多条精确批准后继，以同时核验当前旧运行包与新包；不跳过条目、不扩大匹配范围。修改一字节的未批准类仍被拒绝。原批准记录保留在review证据中。
4. 最终冻结发布清单：`.run/build/releases/878c8ffe-508e-4811-8991-b0fc11be7f5a/manifest.json`，22项既有修复校验通过。经本次明确批准，用`scripts/worktree-backend.ps1`部署59191，PID28884，固定`local,dac`，数据库`npdms_domain_test`及端口/Redis保持原配置。
5. 已有AST39条、PLT23条成功结果全部补存，逐条比较本地结果与原任务响应一致，原归档/任务/解析版本/来源绑定保持不变。并发首次读取原历史日志通过。补存保留原查询链路中的设备事实幂等关联行为，不声称设备关联记录数量完全未变。
6. 按批准临时暂停DAC原PID20076，健康请求超时证明确实不可用。真实Chromium正常登录项目992203060198，从配置调试的“上传日志 / 文件历史”打开`02010950P231000015-v7.txt`，显示原解析版本1.3.2和6个日志块；文件历史中的v4显示原解析版本1.3.0，设备实体、字段与来源、识别情况及JSON页签可查看。无页面JavaScript错误，无上传或新解析写请求。
7. DAC暂停期间，39条AST及23条PLT原接口均返回原完整结果和原任务编号。finally恢复同一DAC进程，恢复状态0、健康检查200；另设90秒兜底恢复。未终止或重新启动DAC，未替换其数据。

## 证据

- `.run/build/local-log-snapshot-tests.log`：29项定向测试。
- `.run/build/local-log-snapshot-migration/`、`local-log-snapshot-platform-migration/`：迁移前后历史及Flyway记录。
- `.run/build/local-log-snapshot-review/`：精确后继审批保留、嵌套类比较和未知替换拒绝。
- `.run/build/local-log-snapshot-release.log`、`local-log-snapshot-deploy.log`：构建冻结与部署。
- `.run/build/local-log-snapshot-acceptance/backfill.json`、`platform-backfill.json`：62条原结果补存。
- 同目录`browser-evidence.json`、`dac-unavailable-evidence.json`、`latest-existing-result.png`、`historical-v4-result.png`：真实页面及DAC暂停/恢复证据。
- 复验脚本：`.run/build/backfill-local-log-snapshots.py`、`accept-offline-local-log-result.py`、`verify-offline-log-results.py`。

本次证明已有成功结果在DAC请求不可用时仍可查看，未实施新解析成功率、物理设备命令或整个Feature的重新验收。未提交或推送Git。
