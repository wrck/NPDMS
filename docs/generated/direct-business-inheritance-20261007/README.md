# 直接继承业务主干：实现及验证记录

验证基准：`28617c7993b0c6edfc04c7e9def582cc02388510`，树 `0da50d75767286b112b6bba27f6cef139bbf8540`。产品代码不变的浏览器日志收尾复验基准为 `d36aba0aafd0fd6f5847035c9a6e533ca8699c03`，树 `0eb4008a94e2db27a431ae30534ff3c99e25cee8`。本记录之后的文档提交不改变上述产品代码。

## 结论

新增主干的两个普通业务和一个特殊业务已通过隔离 SQL/MVC 验证；两个普通业务的真实浏览器 CRUD、交付件和归集闭环已通过。旧框架和旧页面保留，兼容回归通过。**这不是所有生产业务接入完成，也不是生产登录、正式迁移或十个真实项目验收完成。**

业务 Controller → 类型化 Service → 本业务 Mapper 直接继承调用。普通业务不依赖全局目录注册或旧操作分发器。实体拥有独立表；统一材料继续使用 `plt_delivery_material`。新增公共材料 API 依据业务权限和项目范围授权，不要求旧模型工作台权限。

接入方式及扩展点见 [使用说明](../../coding/project-business-inheritance.md)；历史目标及保留旧方案决定见 [讨论记录](../../decisions/2026-10-07-unified-business-default-implementation.md)。实现与测试由主会话编写；另一既有验证环境仅执行测试，不修改实现。

## 验证结果

| 范围 | 结果 | 能证明什么 |
| --- | --- | --- |
| 主会话聚焦 Java | 28 通过 | Spring 泛型继承、默认接口、约束、权限、回执及相关既有契约 |
| 主会话前端 | 10 通过 | 两业务共用客户端、四字段上传、操作回执、失败/重复/过期响应、独立归集路由 |
| 主会话后端 `yudao-server -am package -DskipTests` | BUILD SUCCESS | 最终产品代码完整装配编译/打包，不等于测试或启动验收 |
| 较早基准 `88dca0e8` 四组 SQL | 48 通过、1 跳过 | 新主干与旧默认持久化/交付链回归；跳过项不计通过 |
| 最终基准 `28617c79` 三项直接继承 SQL/MVC | 3 通过 | 两空业务的独立表/接口 CRUD、null 清空、CAS、幂等回执、逻辑删除、同表交付件、引用保护；复杂 XML/业务 hook/专用 API；只读删除拒绝、跨租户读取拒绝 |
| 最终新主干浏览器 | 1 JUnit 通过 | 两业务创建/列表/读取/编辑/上传/精确回显、项目归集、取消与确认删除、逻辑历史保留 |
| 最终旧页面浏览器 | 1 JUnit、5 场景通过 | 旧页面与归集保留，登记失败回滚/重试、删除和重新上传、列表 HTTP 检查 |
| 最终前端传递依赖类型检查 | 失败，4 项既有错误 | 新修改文件未报告类型错误；不得称全量类型检查通过 |

最终组合执行 5 项 JUnit，零失败/错误。`d36aba0a` 新浏览器最终 54 条业务请求均 HTTP 200/业务码 0，无 `/business-models/` 调用。同一材料表中两类业务记录通过新的公共入口按项目展示。

## 失败与修复经过

1. `8691e06`：文件输入仍因归属加载而禁用，测试提前 `set_input_files`，没有发出上传请求。增加等待输入启用的断言，未降低上传/归属校验。
2. `0df886c3`：两业务 CRUD/上传已通过，但旧归集路由要求 `pms:business-model:query`，业务只有自身权限时返回业务码 403。增加独立公共材料入口和 `ProjectDeliveryCollection`；旧入口权限保留，不给角色补授工作台权限。
3. `28617c79`：新旧浏览器与新增权限反例全部通过。

4. `d36aba0a`：修复响应日志收尾时序，浏览器存活时完成响应体读取及所有断言；重跑新浏览器 1 项通过，54 条请求成功，捕获错误 0，浏览器/Vite/Maven 日志中无 `TargetClosedError`。最终 [原始合成数据结果](browser-result.json) 随报告保存。此前 `28617c79` 的关闭日志异常仍保留为历史证据。Maven/JDK 的依赖/弃用/动态 agent 警告仍存在，不称构建日志完全无警告。

## 可重复执行

按照仓库 `AGENTS.md`、`docs/development.md` 的 JDK 25/Maven、Node/pnpm 和 Docker Compose 基线准备环境。独占脚本拒绝复用已有测试容器，不连接 `npdms_domain_test`。

```bash
DELIVERY_TESTS='DirectBusinessCrudMySqlTest,DirectBusinessBrowserMySqlTest#inheritedPagesCreateEditUploadCollectAndDelete,DefaultBusinessDeliveryBrowserMySqlTest#browserTwoDefaultBusinessPagesAndCollection' \
DELIVERY_BROWSER=true scripts/tests/verify_default_business_delivery_mysql.sh
```

- SQL/MVC：`pms-module-platform/src/test/java/.../businessmodel/declared/DirectBusinessCrudMySqlTest.java`
- 新浏览器：同目录 `DirectBusinessBrowserMySqlTest.java`、`scripts/tests/run_direct_business_browser.py`
- 输出：`.run/default-business-delivery-20261007/mysql.log`、`.run/direct-business-browser/process.log`、`docs/generated/direct-business-browser-20261007/{result,http}.json`
- 旧浏览器输出：`docs/generated/default-business-delivery-20261007/`

## 边界与后续缺口

- SQL 为真实 MySQL，浏览器渲染实际 SFC，调用实际 MVC/业务服务。认证输送、项目范围及文件存储提供者仍为测试边界，不是完整生产登录/存储验收。
- 两个普通业务/特殊业务为隔离验收实体，不能据此声称需求分析、工勘或全部生产实体已经切换。
- 生产实体逐项接入、特殊状态/历史保护、模板任务与新主干连接、旧原生材料历史归集仍待分别审计和迁移。
- 类型检查旧错误：`DeviceCollection/CollectionDialog.vue:390`、`processDefinition/index.vue:151`、`ProjectSchedulePanel.vue:142`、`schedulePresentation.ts:148`。
- 本机真实项目迁移是独立任务，真实迁入仍为 0；本报告没有执行该迁移。
