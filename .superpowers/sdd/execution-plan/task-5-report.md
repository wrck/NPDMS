# T-CP-005 执行报告

## 状态

前端机械导入已完成并可完成 Docker 生产构建。上游锁定快照自身存在
两处 Stylelint 属性排序错误，未修改该快照以维持来源一致性。

## 来源与范围

- 来源：`._codex_work/upstream/yudao-ui-admin-vue3`
- 锁定提交：`2d028c8f7a14dd2e532ac1a76d1fdf58840dc621`
- 目标：`yudao-ui/yudao-ui-admin-vue3/`
- 未创建 PMS 页面或 API。

## 一致性校验

- 源文件数：2,398。
- 目标源文件数：2,397。
- 逐文件 SHA-256 对比仅有一项差异：来源受跟踪的 `.env` 未导入。
- 排除原因：该文件含默认账号口令、API AES 密钥和百度地图 key；项目安全约束和 Git 提交规则禁止提交 `.env` 或凭据。
- 最终 Git 暂存文件数：2,389。除上述 `.env` 外，根仓库既有忽略规则还排除 5 个 `.env.*` 运行环境文件和 3 个 `.vscode/*` IDE 文件；这些文件仍保留在本地导入目录，不进入版本库。
- `src/views/bpm/`：53 个文件；`src/api/system/`：28 个文件；`src/api/infra/`：16 个文件；`src/**/pms/**`：0 个路径。

## Docker 验证

- 镜像：`node:20.19.6-bookworm`（Node `v20.19.6`）。
- 包管理器：Corepack 固定 `pnpm@9.15.5`（与 lockfile v9 对应）。
- `pnpm install --store-dir /pnpm/store --frozen-lockfile`：通过；`esbuild` 等上游构建脚本已实际执行。
- `pnpm build:prod`：通过，Vite `v8.1.4`，1m25s。
- `pnpm lint`：失败，锁定上游文件 `src/views/crm/statistics/product/components/ProductSalesList.vue` 第 291、296 行的 `font-weight` 属性排序不符合 Stylelint `order/properties-order`。本任务不修改机械快照。

## 构建告警

- 生产构建因安全排除 `.env` 报告未定义 `VITE_APP_TITLE`；这不阻止构建，T-CP-006 应以无凭据的 `.env.example` 或 Docker 环境注入提供运行配置。
- Vite 还报告上游 CSS 的 lightningcss `*zoom: 1` 兼容性告警；构建成功。

## 提交范围

- 仅暂存并提交 `yudao-ui/yudao-ui-admin-vue3/`。
- 不提交 `.env`、`.env.*`、`.vscode/`、`node_modules/`、`dist-prod/` 或本报告。
