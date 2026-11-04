# 正式环境服务修复与处理器对照

2026-11-03，Asia/Shanghai。承接服务检查后的已授权修复、部署与必要重启；本次补充测试环境处理器对照。未执行数据库迁移、清理历史或重置密钥。

## 处理器结论

只读查询测试库 `172.17.90.178:13306/npdms` 与正式库：测试22条任务、正式23条，共同22条的ID、处理器名、状态和Cron完全一致。正式库多出已发布的 `projectContractProjectionJob`，没有测试库已有而正式库漏配的定义。见[对照结果](job-comparison.json)、[测试清单](test-jobs.txt)。

- `deviceAssignedProjectionJob`、`projectServiceManagerNotificationJob`：原有实现和启用定义均存在，缺的是RAMJobStore重启后的恢复登记。新增统一 `JobStartupRegistrar`，复用既有服务恢复缺失触发器，保留启用/暂停状态、已有触发器及原本停止的调度器。无调度器时不读任务；已启用任务处理器无效仍启动失败。
- `cutoverExternalApprovalNotificationJob`：类存在，但未注册为运行Bean，外部通知生产端口未接齐。测试库同样暂停且无执行历史。[F-CUT-008 Task](../../../tasks/features/F-CUT-008.md)明确为NOT_READY，缺INT-10短信/邮件、INT-05钉钉Provider及联调。不能从测试环境复制出不存在的完整实现。本次保持暂停，启动恢复跳过未安装的暂停功能，不添加空发送器。
- 其余8项暂停定义保持原状；未点击全量“同步任务”或启用尚未交付功能。

## 部署与实际验证

后端最终版本 `SDP20261103_SERVICE_REPAIR_BACKEND_R2`，SHA256 `676685a80c6268b8facf56633e8b031ca6ee6a4ff2b410b18135cf19ea1f921c`；冻结源码摘要与工作树一致。PID114421，readiness UP，自动重启0。见[部署](backend-deployment.json)、[健康及恢复日志](backend-health.json)。

首版恢复器曾因暂停的外部审批任务缺少Bean而启动失败，已先回退原版本，再增加暂停未安装功能的处理与失败测试后部署R2；不将首版失败隐藏为一次成功发布。相关当前有效自动化测试31项通过：任务同步5、启动恢复5、项目运行协调6、规则事件7、任务事件8；受控构建成功，22项部署修复基线校验通过。原始日志在 `.run/service-repair-20261103/tests*.log`、`build-r2.log`。

两项恢复任务于03:04:30、03:05:00、03:05:30连续执行成功。数据库及真实浏览器结果一致，首条日志ID分别34761、34764。当前批次业务数量均0，因此证明定时执行恢复，不宣称已发送通知或处理了不存在的设备事件。见[执行结果](job-executions.json)、[任务页面](device-job-browser.txt)、[真实执行日志页](job-executions-browser.txt)。

## 同轮服务修复结果

- UMC配置仅将原有五项配置合入真正生效的加密凭据，保留其他值及加密备份。证书固定校验、登录、鉴权只读查询与退出成功，未创建UMC任务。见[UMC验证](umc-read.json)。
- 独立DAC已补发 `SDP20261103_SERVICE_REPAIR_DAC`，保留既有密钥并补新旧变量映射；PID112439、自动重启0。鉴权访问200、无鉴权401。Redis PONG/AOF正常，MinIO现存对象与两个正式域名下载内容一致。见[依赖检查](dependencies.json)。
- 项目规则重评补充识别既定终态NO_TRACKING_CLOSED，仍先校验租户，不直接改生命周期。实际任务每批处理50条、重试0；排队量由53126下降到本次统计的53026，仍在原30秒周期内逐批消化，未删除或直接标记事件完成。其余TaskCompleted、ProjectManagersChanged积压未伪造投递。
- 四机logrotate错误路径已修复且真实运行成功。离线固件元数据刷新定时器已停用并清理失败状态；这属于停止不适用的在线维护任务，不代表已完成固件更新。两台FE Nginx配置校验成功，业务服务保持运行。
- BE/DB每日04:30备份定时器已启用。首轮加密备份成功且完整解密读取校验通过：DB原始SQL约21.25GB、加密压缩约3.26GB。没有执行数据库恢复演练或异机复制；异机目的地仍待提供。见[DB备份验证](backup-db.json)。

P13/P14来源异常按用户“忽略”留在本次修复范围之外，原失败记录与业务校验保留。未把服务健康、任务成功日志或本机备份夸大为所有业务验收完成。
