# 已交付空库包应用部署验证

日期：2026-10-19。结论：**未通过应用部署验证**。数据库迁移和初始化通过，但后端按包内部署 profile 启动失败，不能判定该包可直接部署使用。

## 对象与隔离边界

- 实际解压交付文件 `npdms-172.17.90.179-verified-v501-20261019.tar.gz`，核对内外文件清单。没有重新构建或修改发布包。
- 后端为已冻结产物 `e4fcc870-6476-4e45-aba7-1e9a678dcc4b`，JAR SHA-256为 `b30e9de89cf920ac10f513b28a3fa7d3899912a00cb6a34841475913e617016a`。
- 新建专用 Schema `npdms_release_appval_33bc6d71a104` 和仅授权该库的临时应用账号；复用现有 Compose MySQL实例，不写现有业务库。
- Redis、MinIO由本次独立 Compose 项目 `npdms-appval-33bc6d71a104` 提供；后端尝试在宿主机59399运行。
- 运行采用包内的 `docker` profile、PropertiesLauncher、预编译MinIO适配器和应用配置。仅隔离数据库、Redis、日志、监听端口等环境地址，以及通过进程环境注入测试密钥；未替换业务实现。
- 本机为Windows宿主机和Debian WSL；没有登录172.17.90.179，也没有执行Ubuntu24.04主机上的完整systemd安装脚本。

## 已通过的检查

1. 真实MySQL空库成功执行包内4个迁移：B498、V499、V500、V501。Flyway validate通过，重复migrate无待执行迁移。
2. 使用包内原有 `bootstrap-admin-and-storage.sql`，以BCrypt成本12的测试密码哈希启用原管理员，并初始化1个数据库文件存储。未加入新的自动初始化工具。
3. 应用启动前执行包内 `verify.sql`，结果严格匹配：461张表、240个触发器、管理员已配置、1个存储；种子、业务表为空、物理外键、逻辑引用、菜单和风险字典差异查询均无结果。
4. 独立Redis、MinIO启动；使用原包固定摘要镜像建立私有桶及受限应用账号，MinIO返回 `permission=private`。这不代表应用Owner文件服务已完成验收。

## 阻断：部署profile缺失必需Bean

包内应用部署脚本使用：

```text
--spring.profiles.active=docker
```

实际日志：

```text
APPLICATION FAILED TO START
Parameter 9 of constructor in ...InspectionExecutionService required a bean
of type '...DeviceLogParsingApi' that could not be found.
```

来源核对：

- `InspectionExecutionService`构造函数直接依赖 `DeviceLogParsingApi` 和 `DeviceOpsLogSplitApi`。
- 对应DAC网关只在 `pms.integration.device-ops.enabled=true` 时装配；当前包的docker配置没有启用该条件。
- 之前59299业务闭环使用的运行配置为 `local,dac`，不能据此证明当前包的 `docker` 部署配置可启动。
- 包内脚本没有配置DAC请求/回调签名密钥及目标服务端点。已有 `application-dac.yaml` 需要这些外部配置；仅把profile名称改为 `docker,dac`，不能直接宣称部署已修复。

这是实际应用启动阻断，不是Flyway失败。本轮没有新增空实现、关闭巡检模块、绕过配置校验或修改发布包来使检查通过。

## 未通过与未执行的边界

后端未就绪，应用Owner的MinIO启动读写检查未完成。真实浏览器登录、菜单/项目空态、应用内文件上传下载均受此阻断，**没有计为通过**。Ubuntu24.04的systemd服务安装及目标服务器完整部署未执行。

部署入口需要与应用当前必需的DAC装配和安全配置对齐，再重新验证。此报告只给出已观察到的阻断，不把未执行步骤写成成功，也不扩大为整个应用的功能验收。

## 证据与清理

证据目录：`F:/npdms-empty-app-validation-20261019/`。

- `database-before-app.tsv`：应用启动前的完整空库校验结果。
- `flyway-migrate.log`、`flyway-validate.log`、`flyway-repeat.log`：迁移和重复执行回执。
- `backend-console.log`、`backend-file.log`：真实启动失败日志。
- `minio-initialization.log`：私有桶初始化结果。
- `state.json`、`infra-cleanup.log`、`verdict.json`：运行事实、清理及最终结论。

本次临时库、临时数据库账号和独立Redis/MinIO容器已清理，失败日志和解压制品保留。59191、59299、DAC48182及18199的进程身份与命令摘要均与验证前相同。未提交或推送Git变更。
