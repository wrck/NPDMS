# 四机正式环境原生部署工具（第四版：数据统一/data）

部署工具统一安装到`/opt/sdp/tools/`：将本目录内容直接复制到该目录，保留脚本与`templates/`的相对位置。`operate.sh`按自身位置查找`release.py`，无需硬编码旧目录。

操作说明：[正式环境四机部署手册](../../docs/engineering/deployments/npdms-production-four-hosts.md)。脚本只在操作者明确调用时工作；没有自动SSH、写入密码、重置数据库或替换现有开发部署入口。

- `render.py site.json NEW_OUTPUT_DIRECTORY`：校验域名与数据库名，生成各机非秘密配置。
- `install-native.py COMPONENT VERSION ARCHIVE SHA256 --approved`：在指定主机校验并安装原生二进制包；不覆盖已有安装，不启动服务。
- `init-mysql.sh --approved-empty-database`：DB机上单独批准的空库初始化，拒绝非空数据目录，临时口令仅写受控内存日志。
- `prepare-data.sh be|db --approved`：确认目标IP和`/data`挂载后创建所需数据子目录，不搬迁现有数据。
- `record-credential.py SERVICE ACCOUNT RECORD_ID --state verified`：目标机root通过无回显交互将实际首次安装密码保存到`/opt/sdp/config/credentials/SERVICE/`，目录0700、文件0600，不覆盖、不修改账号。MySQL初始化自动记录临时root密码，正式轮换后另行记录；详见手册6.1。
- `release.py pack ROLE RELEASE_ID SOURCE NEW_TARGET`：冻结目录文件清单。
- `release.py verify ROLE DIRECTORY`：验证文件增删和SHA256；不等于审批或签名。
- `operate.sh ROLE preflight`：目标IP、系统/工具、配置检查。
- `operate.sh db|be infra-up --approved`：通过systemd启动本机MySQL或Redis/MinIO。
- `operate.sh be migrate-info|migrate-validate`：查询/验证迁移状态。
- `operate.sh be migrate --approved`：批准窗口内执行迁移。
- `operate.sh be dac-migrate-info|dac-migrate-validate`及`dac-migrate --approved`：独立DAC数据库迁移查询、验证、执行。
- `operate.sh ROLE publish KIND DIRECTORY --approved`：校验并复制到新版本目录，不激活。
- `operate.sh ROLE activate KIND RELEASE_ID --approved`：切换版本；后端重启，前端验证并reload，迁移目录只切换。
- `operate.sh ROLE service backend|dac|mysql|redis|minio start|stop|restart --approved`：只操作属于指定主机的服务，独立启停。
- `backup-db.sh --approved AGE_PUBLIC_RECIPIENT OUTPUT_DIRECTORY DATABASE`：DB主机流式加密逻辑备份。

ROLE：`fe1/fe2/be/db`；KIND：`frontend/backend/migrations/dac/dac-migrations`。后端HTTPS 443，DAC同机HTTPS 8443，各自systemd服务与加密凭据、独立启停。全部原生安装，部署、迁移、备份均不依赖Docker/Compose。Ubuntu上使用`bash`执行，安装器需要Python3.12以上。安装前由运维审核`/etc/sdp`配置与现有服务，秘密只能来自批准的秘密管理渠道。`--approved`是执行者对已经获得的变更授权的声明，不代表脚本产生审批。

本地验证入口：`python -m unittest discover -s scripts/production/tests -v`；Bash使用`bash -n`。该验证不连接四台服务器，也不证明业务应用能在正式环境启动。

第四版增加`/data`路径与挂载保护检查，总计18项测试通过；4个Bash脚本语法通过。日志按用户目录清单例外保留`/var/log`，程序/配置仍在`/opt`和`/etc`，内存秘密在`/run`。现有数据迁移与真实磁盘挂载未执行。

2026-10-30第三版本地验证：17项制品、原生包安全解包、配置及失败测试通过；三个Bash脚本语法通过。在本机WSL Debian中临时解包原生Nginx 1.22.1（不安装系统服务），两份站点配置校验及19项外网路径/方法检查通过，并核验URI、tenant-id透传及Authorization/Cookie剥离。测试仅用回环临时端口、TLS模拟后端与内存证书；未连接目标服务器。本版不沿用前版Compose检查作为原生服务验证证据。

可复现代理验证：在具备原生Nginx/curl/OpenSSL的隔离Linux环境执行`python3 scripts/production/tests/check_nginx.py`，需要可写`/dev/shm`；无需容器。验证版本不是生产软件版本选型。尚未验证目标Ubuntu的原生软件依赖与安装启动、systemd秘密注入/重启恢复、MySQL初始化、生产迁移、真实业务浏览器闭环。
