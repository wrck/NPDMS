# 公共文件槽位浏览器验证

运行 `node tests/file-reference-browser/verify.mjs`；如 Playwright 在独立工具目录，设置 `NPDMS_BROWSER_PACKAGES` 为其 node_modules 路径，可用 `NPDMS_BROWSER_EXECUTABLE` 指定 Chromium。

使用真实共享 Vue 组件及文件 HTTP 客户端，Playwright 提供确定的文件响应；验证文件完成后消费者拒绝、整页刷新按完整稳定键恢复、字符串大 ID、新版本及已解绑状态。结果及截图保存在 `.run/cloud-20261006/file-reference-browser/`。

这个浏览器 fixture 不证明原生材料登记、真实登录授权或外部存储。稳定键 SQL、当前 Owner 拒绝、租户隔离、不可用 Provider 和生产 Long 序列化由 `FileReferenceDiscoveryPersistenceTest` 与专属 MySQL 子类单独验证。
