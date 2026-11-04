# 正式环境服务与漏更新检查

检查日期：2026-11-03（Asia/Shanghai）；实时复核约02:08—02:16。范围为正式四机、全部systemd服务清单、8个业务承载服务、应用制品、配置加载、迁移、任务、存储及外部依赖。沿用本次01:43—01:56已取得且适用的只读证据。只读检查，没有部署、重启、迁移、触发同步或修改业务数据。

结论：8个业务服务均运行，但不能判为全部正确、无漏更新。确认UMC配置未进入生效凭据、服务端DAC包落后；另有调度登记缺口、业务同步失败和事件积压。前后端主制品没有发现漏更新。

## 逐服务覆盖

| 服务 | 现网状态及版本 | 检查结论 |
|---|---|---|
| FE1 Nginx，172.20.19.2 | PID12960，active/enabled；SDP20261103_BRANDING | 发布清单验证成功、nginx配置检查成功；index与FE2一致 |
| FE2 Nginx，172.20.20.2 | PID11344，active/enabled；同一前端版本 | 发布清单验证成功、nginx配置检查成功；客户入口白名单保留 |
| 后端，172.17.73.104:443 | PID106906，active/enabled；SDP20261103_CONTRACT_PROJECTION | readiness UP，自动重启0；运行JAR及冻结源码摘要与当前工作树一致 |
| 独立DAC，172.17.73.104:8443 | PID17159，active/enabled；SDP20261102_cb21b3015 | readiness UP，自动重启0；鉴权读200、无凭据401，解析worker有效；制品落后，详见下文 |
| Redis | PID10912，active/enabled；回环6379 | 认证PING=PONG；AOF启用、写入状态ok；计数45为历史自动重启，当前检查未增长 |
| MinIO | PID97126，active/enabled；回环9000/9001 | 健康检查200，现存对象读取成功 |
| 存储TLS代理 | PID98101，active/enabled；BE9443 | 两个正式域名签名下载同一对象200，内容摘要一致；历史自动重启34，本次未增长 |
| MySQL，172.17.74.19:3306 | PID8940，active/enabled；8.4.13 | 应用及DAC均TLS1.3连接成功；当前无长事务积压；主库518、DAC24张业务表，不含Flyway历史表 |

所有PMS模块由统一后端装配，不将其虚构成独立服务。四台主机额外枚举了所有已加载服务、失败单元、监听及定时器，包含SSH、监控代理和系统维护服务。

## 确认的问题与处理方向

### 1. UMC配置写入了已不生效的凭据源

旧文件`/etc/credstore.encrypted/sdp-backend`有UMC地址、账号、密码、provider及证书指纹配置，但`datasource-credential.conf`清空并覆盖了原LoadCredentialEncrypted，实际使用`/etc/credstore.encrypted/sdp-backend-datasource-20261102`。实际凭据与当前`/run/credentials/sdp.service/application-secrets.properties`内容相同，均无这组UMC配置。进程环境、启动参数及应用配置进一步交叉检查见证据。

这不是简单“等待重启”：重启仍会加载覆盖后的文件。后续修复应将已批准的UMC配置合并进**真正生效**的加密凭据，保留当前数据源加密密钥及邮件配置；禁止把旧文件整体切回。检查只比较属性名和是否相同，未输出或保存秘密值。

### 2. 服务端DAC未换成现有较新冻结包

- 现网JAR SHA256：`fb1ecf254753b452e42c7ac98a928750b950bb170e5e69bdecd689e7ef6dce71`。
- 当前源码匹配的已冻结候选：`.run/formal-redeploy-20261102/releases/1161d8cb-d687-4118-9c6c-ba68b23c6f92/manifest.json`；JAR SHA256：`54fb7da26597c1ff8c919523c2e63131774ad0b196c72f5fd14f4208cdb65018`。
- 对1939个应用及内部模块文件逐项比较，5项不同：`application-desktop.yml`、`application-npdms.yml`、`DesktopDiscoveryVerifier.class`、`DesktopStatusController.class`、`desktop/status.html`。详见[dac-artifact-diff.json](dac-artifact-diff.json)。解析核心内部模块未发现差异，不能将其夸大成现网解析不可用。
- 新配置改用了`DAC_*`等变量名，现网覆盖配置仍引用`NPDMS_DAC_*`。升级前须验证最终属性覆盖和映射，不能仅替换JAR。

两台FE发布的桌面安装包均为SDP-DAC 1.1.8，90,385,061字节，SHA256 `5c2e872767daae12337be20351cf14d14b8fc6f7b1fcc218b20dc6170b1d4b84`，与本次冻结前端记录一致。旧1.1.5安装包留存不等于默认下载仍指向旧版本；未检查所有员工电脑的安装版本。

### 3. 调度配置及任务恢复存在缺口

现网实际使用Quartz **RAMJobStore**；旧配置中的`org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration`没有阻止当前Spring Boot 4.1 Quartz启动。线程、启动日志和业务执行日志一致证明调度正在工作，数据库QRTZ触发器表为空与内存存储一致。

23条任务定义中15条启用、8条暂停。13种处理器在本次后端启动后已有成功执行记录；`deviceAssignedProjectionJob`（设备祖先投影）和`projectServiceManagerNotificationJob`（服务经理指派通知）虽启用，却没有任何执行历史，源码也未发现对应启动恢复注册器。现有按处理器登记的方式没有覆盖全部已启用任务，应补全恢复机制或明确持久化调度方案，不能只看数据库“启用”状态。

`projectTaskOutboxDeliveryJob`等暂停任务不应在本次检查中擅自启用；需结合其业务启用范围处理。

### 4. 基础同步有新失败，不能因服务健康而忽略

最近正式执行：P13在01:53失败，错误`P13_SOURCE_BINDING_CHANGED`；P14在01:54—01:59失败，错误`P14_BUSINESS_KEY_MISSING`。P30最近一次预览失败，但此前正式执行成功，两者必须分开报告。其他任务逐项最新记录保存在证据中。

真实浏览器基础同步页起初显示“请求未成功返回”及P14活动Run；只读刷新后提示与活动Run消失。数据库仍保留上述失败，不能把页面刷新成功记作同步成功。需要分别核查来源绑定和缺失业务键，不能绕过校验或重复盲跑。见[浏览器快照](base-sync-browser.txt)。

### 5. 事件积压与任务成功日志不一致

02:10只读统计：`ProjectRuleReevaluationRequested`待处理53,126条，全部关联`NO_TRACKING_CLOSED`项目；最早2026-11-02 02:36，最大重试3。近期日志持续出现`not-completed`。`TaskCompleted`待处理4,493条、`ProjectManagersChanged`待处理7条。

这是持久业务事件尚未完成，不可用Job日志status=1代表每条事件成功。应核查无跟踪关闭项目的重评终止语义、相应事件消费者及暂停任务范围；本次未改生命周期、未删除/伪造投递历史。

### 6. 系统维护与备份仍有缺口

四台主机logrotate失败：`/usr/lib/acct/accton-rotate`不存在，pacct轮转后脚本报错；不意味着所有日志都没有轮转，但系统轮转任务整体处于失败。FE1、FE2、DB另有fwupd-refresh失败。它们不属于业务包漏部署，也不应隐瞒。

目前检查的BE/DB定时器、root crontab及BE cron.d未发现业务数据库/对象存储持续备份任务。已有专项加密备份及解密摘要验证，不能等同于持续异机备份或恢复演练；外部备份平台是否覆盖尚未验证。磁盘、inode及时间同步未发现当前容量或时钟故障。

## 已确认没有漏更新或需纠正的记录

- 后端现网JAR SHA256为`9b3aa09995c0bf891b0a05b2ce801a4f88f9b2dd2804cb791096f0e3b142bbfd`；冻结源码摘要与本工作树当前后端Maven输入一致。
- 两台前端发布清单验证成功，index摘要均为`422de2da3f15c8df34213e53d1ce052c106e0527a88c781da9e5301738b62bae`；冻结前端文件逐项复核无内容差异。HTTPS证书身份验证及所列静态资源200证据见[https-static-checks.json](https-static-checks.json)。
- 主库迁移历史B531、V531.2、V533、V534及独立DAC至V26均成功；正式发布迁移集合validate通过。版本号跳过不等于漏迁移，未将本工作树所有历史SQL直接与正式库版本号机械比较。
- **更正先前回执的Quartz结论**：合同投影任务在2026-11-03 **02:00:00—02:00:02成功执行**。旧回执“自动执行未生效”判断不成立，保留原件并以本次现场证据纠正。本次没有手工触发该任务。
- 默认存储已由后续操作切回配置id=1，bucket为sdp；id=2非默认。配置更新时间为2026-11-02 23:45:42。这与23:37历史回执不同，不能将历史默认值当作现状或擅自切回。
- 邮件TLS握手通过，已有6条成功发送日志，早前2条失败保留；本次未另发邮件。LibreOffice 24.2.7.2存在，本次未重做文档业务转换。DAC解析器活动版本1.4.0、worker心跳有效，当前无采集/解析任务及回调积压。

## 证据与边界

现场命令及结果来自`.run/certificate-session-20261102/ssh/`的300—318、400—407，以及`.run/service-audit-20261103/db-ssh/`的001—003、400—403。精选脱敏输出汇总于[evidence.json](evidence.json)。使用systemctl、ss、release.py verify、SHA256、只读SQL、已有鉴权探针及真实浏览器只读刷新。

该检查覆盖服务及部署一致性，不能承诺所有业务用例均已验收。未重新发送通知、上传业务文件、执行设备命令、运行UMC业务采集、触发外部同步、重启演练或恢复数据。SPMS/CRM有连接及历史业务结果，但不等于其所有数据均无问题；文件扫描按既有部署范围不启用。未把旧报告、端口监听、HTTP200或服务健康当作完整业务验收。
