# UMC Java接口完整流程验证（2026-10-31）

结果：通过。用户授权配置UMC `https://172.17.73.105`、只重启59286，并用现有巡检日志验证上传、执行和结果回读。

## 配置与运行

- 复用Windows当前用户DPAPI保护密码，配置文件为被Git忽略的 `.run/direct-test/umc.encrypted.json`；启动时在内存解密并通过进程环境传入Spring配置，不在命令行、配置文件或日志中保存明文密码。URL、用户名和证书指纹作为非Secret配置保存。
- `.run/direct-test/umc_profile.py`验证配置并加载；原 `start-latest.py`接入该配置。实际Provider为 `JavaUmcInspectionGateway`，没有启用Node适配器。
- 使用证书SHA-256指纹 `ad4964ace6a18f060ba38c083a573868dd20eee552016c142a22eb7588700c89`。证书域名为 `device.dptech.com`，Java适配器以指纹与有效期核验IP目标，没有关闭全局TLS校验。
- 59286 PID由60916变更为67208，健康状态UP，`local,dac`。沿用原冻结运行包及bootstrap，不部署其他未提交业务修改。数据库迁移、自动建表与调度隔离参数保持原值。
- 其余4个Java进程13872、17924、56496、59468的PID与创建时间均一致；未另开Java服务。

## 业务接口与结果

复用巡检任务35（`INS-2116361085705486336`）、执行尝试24已有日志。通过正常登录会话、租户1、`If-Match`及`Idempotency-Key`调用正式Java业务接口；未直接修改数据库业务状态。

| 输入执行 | 已有日志名 | NPDMS报告 | 最终执行状态 | 报告状态 | 正式结果数 |
|---|---|---|---|---|---|
| 36 | `10.27.35.201` | 124 | COMPLETE | AVAILABLE | 35 |
| 35 | `110.27.35.201` | 123 | COMPLETE | AVAILABLE | 35 |

1. `POST /api/v1/pms/inspection-tasks/35/umc/actions/fetch-result`，先处理执行36，再处理旧执行35。Java后端读取已有日志并执行UMC登录、任务创建、离线日志上传与巡检启动、报告轮询、明细回读、DOC下载及平台文件归档。没有重新伪造设备日志或通过Node绕过Java后端。
2. `GET /api/v1/pms/inspection-tasks/35`回读完整正式结果。当前采用的执行36设备SN为 `01101244D23B000001`，型号 `ADX3000-GA-XI`，软件版本 `B311CM006D010P25PATCH25`。35项中20项PASS、12项ABNORMAL、3项UNJUDGED（UMC不支持）；接口成功不表示设备检查全通过。
3. 使用相同幂等键重放执行36的fetch请求，任务版本、报告集合及35条结果引用均未增加。
4. 通过平台文件授权接口下载报告124的DOC，HTTP 200，大小2194495字节，WordML格式及SHA-256与平台引用一致：`dc40828c7767b921a6eea297d8a2b97f39335ca77d9f07390db6e71a9a2590ff`。
5. 仅执行36完成时，`complete-execution`正确拒绝，提示旧执行仍未取得正式结果。旧执行35也经正式接口完成后，再调用 `POST /api/v1/pms/inspection-tasks/35/umc/actions/complete-execution` 成功；任务最终版本7、状态 `REPORT_PENDING`，尝试24为 `COMPLETED`。没有降低完成条件。

两份原始上传日志的文件引用、版本、文件名、SN及上传时间保持不变。两份报告共保留70条正式结果；当前设备采用最新执行36的35条，旧执行35保留为历史。

## 真实浏览器复核与边界

在 `http://127.0.0.1:19086/pms/inspection/tasks/35`确认：巡检执行已完成、报告生成是当前环节、报告就绪1/1；显示正常20、异常12、不支持3，DOC已保存，历史报告1份。

本次止于用户要求的上传、巡检执行和结果回读验证。未执行“确认报告并进入问题处理”、异常整改或业务闭环，不把12项异常和3项不支持标记为已解决。

本地证据位于 `.run/direct-test/umc-validation-20261031/`：`runtime-before.json`、`runtime-proof.json`、`configuration-proof.json`、`other-java-before.json`、`java-after.json`、`source-task-before.json`、`fetch-receipt.json`、`result-proof.json`、`fetch-old-log-receipt.json`、`complete-execution-final.json`、`task-final.json`及`umc-report-124.doc`。重启脚本为 `.run/direct-test/restart-umc-approved.ps1`，核验脚本为 `.run/direct-test/verify-umc-runtime.ps1`；均未提交Git。
