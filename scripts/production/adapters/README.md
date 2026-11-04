# 四机部署适配器

2026-11-01需求方批准沿用测试包的RSA服务JWT及systemd加密凭据，不启用文件扫描。

复制来源为已交付的 `npdms-172.17.90.178-inspection-identityfix-20261101` 包所用源码：后端 `payload/adapter` 的两个配置类、DAC `ServerDacDeploymentConfiguration.java`。原文件保留。四机版仅将后端DAC端点约束改为与批准HTTPS地址完全一致，将MinIO本机端点参数化；令牌生命周期、权限声明、存储凭据引用及既有配置保护保持原实现。

`build_adapters.py` 在工作树工具链锁内针对冻结JAR编译独立 `deployment-adapter.jar`，不修改原应用JAR。编译结果记录应用/源码/适配器摘要；验证探针只用内存中的合成私钥，不保存令牌。通过 `PropertiesLauncher` 加载，适配器不进入默认开发运行配置。

这些测试只验证部署适配器，不能替代应用回归、目标服务器TLS/加密凭据加载、真实文件读写、DAC联调和浏览器验收。

MinIO桶名统一配置于`site.json`的`minio_bucket`（缺省`sdp`），渲染到后端`npdms.server-deployment.minio-bucket`和三机对象代理路径。桶名使用3～63位小写字母、数字、连字符，首尾为字母或数字。上传、访问域名及已有配置校验读取同一值；旧配置发生偏差时拒绝覆盖。既有部署切换必须保留旧桶、旧配置和对应代理路由，为新桶新增配置并在读写探针通过后选择为默认，不能只改访问域名。

升级前核对现网实际配置，不以新模板代替：本适配器要求`SERVER_IP`；2026-11-02正式旧配置使用`NPDMS_SERVER_IP`，升级时增加`SERVER_IP: "${NPDMS_SERVER_IP}"`映射。还须检查`minio-bucket`、`minio-endpoint`、`storage-public-origin`、`ready-file`以及运行凭据均可解析。完整新模板直接提供`SERVER_IP`，不需要旧键映射。
