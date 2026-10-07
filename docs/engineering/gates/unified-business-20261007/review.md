# 新系统统一业务默认实现代码审查

审查日期：2026-10-07。固定分支 fix/visio-template-followup，已发布提交 834a6a5b2246a9ba1d291dec4ee3157bffd35426。

## 结论

当前已具备统一实体、模型目录、部分默认创建保存、动态查询、事务并发审计和前端 Host，但尚未达到“完整默认实现＋薄业务继承接入＋特殊方法重载”的目标。主要问题是公共能力没有连成可直接继承的一套实现，生产业务仍保留另一套实现链。无需推翻所有既有代码，也不能继续用逐 Owner 适配数量代表目标达成。

## 范围与证据等级

- 已发布代码：主线程在自身云电脑直接读取上述固定提交，静态检查默认后端、前端、生产接入样例并枚举全部生产模型描述符。未委派本轮代码审查。
- WIP：冻结 37 文件及之后 3 文件增量根据实施任务回执、文件清单和哈希记录；主线程未取得其全部原始文件做逐行独立复核。WIP 结论明确标为作者回执，不冒充已发布源码。
- 本轮未运行应用、构建、Flyway 或全量测试，不连接生产数据库。先前运行结果作为历史证据引用，不能视为本轮重跑。
- 本机项目创建和十项目迁移是独立活动，其未提交代码未并入本次云分支审查；不能据此认定本机改动已通过本轮审查。

## 已确认可保留的基础

- BaseBusinessEntity 已共享身份、租户、审计、逻辑删除与版本；长期业务仍有独立 DO/表，不需要改成通用关联数据。
- AbstractBusinessApplicationService 已有统一执行模板、事务、回执、审计及事件处理。
- MyBatis-Plus 已承接简单持久化和受控字段查询；复杂 XML 与默认 CRUD 可以共存。
- BusinessModelIntrospector 能遍历继承字段；模型目录与权限投影已有基础。
- BusinessEntityHost、Form、List 已有可复用渲染、编辑及回执恢复骨架；不是完全没有统一前端。

## 缺口清单

| 编号 | 分类 | 缺口 |
|---|---|---|
| G01 | 核心缺口 | 业务 API 未形成薄继承入口 |
| G02 | 核心缺口 | 默认服务不是完整 CRUD |
| G03 | 核心缺口 | 目录注册没有自动产生默认能力 |
| G04 | 核心缺口 | 项目权限仍是显式绑定和兼容名单 |
| G05 | 核心缺口 | 默认服务与生产业务仍存在并行实现 |
| G06 | 核心缺口 | 默认前端尚不能替代完整业务页面 |
| G07 | 能力缺口 | 实体约束没有完整进入默认验证 |
| G08 | 能力缺口 | 复杂查询扩展与薄 Service 重载尚未统一 |
| G09 | 核心缺口 | 发布代码的交付件还不是默认继承能力 |
| G10 | WIP 缺口 | 37 文件样例存在继承和旧材料归集边界 |
| G11 | WIP 阻塞 | 冻结工作树有未编译的三文件半成品 |
| G12 | 验收缺口 | 尚无标准业务薄继承生产闭环证据 |

### G01 业务 API 未形成薄继承入口

分类：核心缺口。

事实：已有具体通用路由 Controller，方法按 ownerModule/entityType 路径参数分发，但没有已实现的、绑定业务身份和专用路由即可继承整套映射的业务 Controller 基类。代码库也未发现项目业务薄子类使用这种基类。

影响：业务仍需独立写接口或直接暴露通用目录入口。通用 Controller 存在，不等于用户要求的业务 API 继承接入已完成。

最小修补方向：从现有映射抽取绑定实体身份的可继承公共入口；薄子类只定义业务路由和身份，不复制 CRUD。

源码定位：
- [pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/businessmodel/BusinessModelController.java:49–166](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/businessmodel/BusinessModelController.java#L49-L166)

### G02 默认服务不是完整 CRUD

分类：核心缺口。

事实：默认命令只处理 CREATE 和 UPDATE，其他操作抛 OPERATION_NOT_DEFAULTED；标准操作枚举没有 DELETE。读取在另一个访问服务中，未形成可直接继承的完整业务接口。

影响：新增实体即使声明持久化，也不能继承获得普通删除及完整业务服务合同。删除必须有安全的默认条件，而不能简单向所有历史业务放开。

最小修补方向：补齐标准接口和默认 CRUD；普通逻辑删除在统一权限、版本、引用保护下执行，特殊禁止删除规则重载。

源码定位：
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/DefaultBusinessApplicationService.java:263–280](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/DefaultBusinessApplicationService.java#L263-L280)
- [pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/businessmodel/model/BusinessOperationDescriptor.java:18–20](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/businessmodel/model/BusinessOperationDescriptor.java#L18-L20)

### G03 目录注册没有自动产生默认能力

分类：核心缺口。

事实：静态逐项清点 79 个生产描述符构造点，77 个 operations 为 List.of()；只有 requirementAnalysis 和 siteSurvey 声明操作。Registry 校验并登记原描述符，没有自动补默认操作。79 个构造点全部使用令 scopeBinding=null 的兼容构造器。

影响：实体目录、统一读取和可执行业务是不同进度。79 不是业务根实体数量，也不能说其中 77 个业务完全不存在或不能通过原接口使用。

最小修补方向：标准业务继承/薄绑定应得到默认操作和权限规则；声明保留差异，不重复维护一整套默认配置。

源码定位：
- [pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/businessmodel/BusinessModelRegistry.java:27–67](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/businessmodel/BusinessModelRegistry.java#L27-L67)
- [pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/model/EngineeringBusinessModelContributor.java:202–239](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/model/EngineeringBusinessModelContributor.java#L202-L239)

### G04 项目权限仍是显式绑定和兼容名单

分类：核心缺口。

事实：BaseBusinessEntity 只有共性身份/版本，未定义项目归属；默认范围要求显式 scopeBinding，缺少即拒绝。已发布兼容层通过实体名称集合逐个接入 Project VIEW。

影响：安全拒绝是正确的，但普通项目业务不能因继承自然获得统一项目范围。最近四项绑定只补读取，不等于完整写入和交付能力。

最小修补方向：定义标准项目业务上下文与默认归属约定；项目权限算法只实现一次，非项目和特殊归属才扩展。不能以解除拒绝替代实现。

源码定位：
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/entity/BaseBusinessEntity.java:14–34](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/entity/BaseBusinessEntity.java#L14-L34)
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/access/DeclaredBusinessScopeSupport.java:37–53](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/access/DeclaredBusinessScopeSupport.java#L37-L53)
- [pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/businessmodel/LegacyProjectReadBindings.java:20–77](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/businessmodel/LegacyProjectReadBindings.java#L20-L77)

### G05 默认服务与生产业务仍存在并行实现

分类：核心缺口。

事实：例如设备配置仍独立编写 create/update/delete/get/page，并在业务服务调用 NativeAttachmentRegistration。它虽继承统一实体并接入统一读取，但 CRUD 和附件归集不是默认业务服务继承。

影响：现在是公共框架与原生业务并存，不能宣称项目业务已完整迁入。特殊设备校验合理，通用 CRUD/归集的重复部分仍需分离。

最小修补方向：保留该业务真实特殊规则，把通用骨架迁到继承路径；不得直接删除原状态校验或授权。

源码定位：
- [pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/configuration/ConfigurationServiceImpl.java:35–106](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/configuration/ConfigurationServiceImpl.java#L35-L106)
- [pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/configuration/ConfigurationController.java:29–95](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/configuration/ConfigurationController.java#L29-L95)

### G06 默认前端尚不能替代完整业务页面

分类：核心缺口。

事实：默认列表只有新建、刷新、打开行、加载更多，固定显示前 8 个可读字段；没有通用搜索/排序/删除操作区。默认表单对 OBJECT_LIST 使用 JSON 文本；Host 仍可跳转专用业务组件，交付/审批/历史面板按能力声明显示。

影响：已有可复用渲染骨架和扩展布局，但完整列表表单交互尚不是薄业务页面直接获得。专用视图本身不是错误，普通场景不应也依赖专用整页。

最小修补方向：补齐标准默认列表/表单/详情/操作区，元数据决定列与控件，插槽只处理差异；不要把所有专用页面一律视作不统一。

源码定位：
- [yudao-ui/yudao-ui-admin-vue3/src/components/BusinessEntity/BusinessEntityList.vue:4–39](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/yudao-ui/yudao-ui-admin-vue3/src/components/BusinessEntity/BusinessEntityList.vue#L4-L39)
- [yudao-ui/yudao-ui-admin-vue3/src/components/BusinessEntity/BusinessEntityHost.vue:45–60](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/yudao-ui/yudao-ui-admin-vue3/src/components/BusinessEntity/BusinessEntityHost.vue#L45-L60)
- [yudao-ui/yudao-ui-admin-vue3/src/components/BusinessEntity/BusinessEntityHost.vue:270–288](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/yudao-ui/yudao-ui-admin-vue3/src/components/BusinessEntity/BusinessEntityHost.vue#L270-L288)
- [yudao-ui/yudao-ui-admin-vue3/src/components/BusinessEntity/BusinessEntityForm.vue:80–113](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/yudao-ui/yudao-ui-admin-vue3/src/components/BusinessEntity/BusinessEntityForm.vue#L80-L113)

### G07 实体约束没有完整进入默认验证

分类：能力缺口。

事实：已能反射字段类型，读取 NotNull/NotBlank 必填信息；固定字段默认服务主要检查 required 是否空。该默认链未看到对实体完整 Bean Validation 约束的统一调用。

影响：长度、数值区间、格式等如果只写在实体注解中，不能据此认为默认 Map 输入通道自动校验。原生 DTO 校验不能替代默认通道的验证。

最小修补方向：合并输入得到待保存实体后统一执行约束校验，业务特殊校验另用 hook，前后端展示同一约束来源。

源码定位：
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/model/BusinessModelIntrospector.java:93–99](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/model/BusinessModelIntrospector.java#L93-L99)
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/DefaultBusinessApplicationService.java:493–499](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/DefaultBusinessApplicationService.java#L493-L499)

### G08 复杂查询扩展与薄 Service 重载尚未统一

分类：能力缺口。

事实：简单查询已有 MyBatis-Plus 动态 SQL，当前默认分页固定 id 升序，查询合同没有排序字段。复杂内容读取通过 BusinessEntityContentReader 扩展，而 ExtensibleBusinessApplicationService 主要开放校验和 Map 字段变化 hook。

影响：动态 CRUD 基础可复用，不应重写；但特殊查询、主从持久化和业务操作的扩展点尚未形成一套薄继承 API。当前 hook 不等于任意真实复杂业务都已验证。

最小修补方向：统一查询/保存扩展契约；简单默认实现，复杂 Mapper XML 由差异方法调用，数据范围不丢失。

源码定位：
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/access/DefaultBusinessEntityAccess.java:166–172](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/access/DefaultBusinessEntityAccess.java#L166-L172)
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/access/DefaultBusinessEntityAccess.java:230–235](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/access/DefaultBusinessEntityAccess.java#L230-L235)
- [pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/businessmodel/access/BusinessEntityPageQuery.java:6–13](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/api/businessmodel/access/BusinessEntityPageQuery.java#L6-L13)
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/ExtensibleBusinessApplicationService.java:83–112](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/ExtensibleBusinessApplicationService.java#L83-L112)

### G09 发布代码的交付件还不是默认继承能力

分类：核心缺口。

事实：发布版交付桥要求启用 DELIVERY，写入要求恰有一个声明 UPDATE；专业服务不在受支持扩展类型时要求自己的能力合同。新四字段 DefaultBusinessDeliveryService/Controller 尚未发布。

影响：普通实体继承后并不直接具有用户要求的上传 CRUD、四字段完成判断和统一归集。不能把专用上传适配完成视为默认框架完成。

最小修补方向：把公共交付能力纳入默认接口与实现，允许薄业务 Controller 继承暴露，无需每业务材料适配器。

源码定位：
- [pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/delivery/DeclaredBusinessDeliveryBridge.java:43–69](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/delivery/DeclaredBusinessDeliveryBridge.java#L43-L69)
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/BusinessOperationDispatcher.java:52–58](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/BusinessOperationDispatcher.java#L52-L58)
- [pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/DefaultBusinessApplicationService.java:309–322](https://github.com/wrck/NPDMS/blob/834a6a5b2246a9ba1d291dec4ee3157bffd35426/pms-module-platform/pms-module-platform-support/src/main/java/cn/iocoder/yudao/module/pms/platform/support/service/DefaultBusinessApplicationService.java#L309-L322)

### G10 37 文件样例存在继承和旧材料归集边界

分类：WIP 缺口。

事实：冻结 37 文件 WIP 的作者回执确认：DefaultBusinessDeliveryService/Controller 是独立公共能力，默认业务 Service 原本没有其方法；新归集只识别 DEFAULT_BUSINESS_DELIVERY 文件引用，旧原生材料未进入该新归集页。

影响：该 WIP 证明新普通材料样例链可运行，不等于默认业务 Service 已继承整套能力，也不等于同一表中所有旧材料已统一展示。

最小修补方向：复用已有授权读取路径统一展示普通与旧材料，并补默认服务/Controller 的继承入口；不得仅删除 SQL 条件绕过旧 Owner 和文件授权。

证据：实施任务 2026-10-07 04:04 与 04:14 冻结回执；见下方 WIP 状态说明。

### G11 冻结工作树有未编译的三文件半成品

分类：WIP 阻塞。

事实：作者冻结回执：37 文件原始包未变，其后修改 DefaultBusinessDeliveryApi、DefaultBusinessApplicationService、BusinessModelAccessConfiguration 三文件。API 已变，实际上传实现和 Record 构造未适配，Controller 基类未实施；增量未编译/测试。

影响：原 13 Java、4 浏览器场景、7 前端检查只对应原 37 文件快照，不能用于当前叠加增量工作树。此增量不可直接发布。

最小修补方向：继续保留原可复现快照与增量，后续批准实施时先完成接口装配和编译；本轮审查不修改或丢弃它。

证据：实施任务 2026-10-07 04:04 与 04:14 冻结回执；见下方 WIP 状态说明。

### G12 尚无标准业务薄继承生产闭环证据

分类：验收缺口。

事实：37 文件验证的 IT/declaredNote、IT/secondDelivery 都是测试夹具。MySQL 和生产服务链是真实执行，但项目范围/生命周期/文件存储端口为夹具；浏览器经 HttpServer→MockMvc，不是完整 Boot 登录链。整体 TypeScript 未通过。发布验证也保留 V374 原始迁移失败。

影响：现有证据可说明部分公共能力有效，不能证明新增长期生产业务薄继承就具备全套能力，不能证明完整部署。V374 是独立部署阻塞，不等于所有架构能力不可审查。

最小修补方向：以后按需求定义的标准业务、第二薄继承生产业务、特殊重载场景验证；分别标注数据库、真实登录、权限和文件存储覆盖，不用选择集数量替代验收。

证据：实施任务 2026-10-07 04:04 与 04:14 冻结回执；见下方 WIP 状态说明。

## 目录统计如何解释

model-inventory.json 由生产源码构造器静态枚举生成：79 个描述符构造点，77 个操作列表为空，79 个使用默认 scopeBinding=null 的兼容构造器。非空操作两项为 SOL/requirementAnalysis、SOL/siteSurvey。

此数字不是 79 个必须套项目模型的业务根，也不说明旧业务原接口不可运行；目录包含修订、关系、配置、权限、要求等辅助模型。最近四个 Project VIEW 绑定和商务读取扩展可提高可读性，但未改变该操作声明统计。

## 冻结 WIP 状态

- 原 37 文件：16 生产、6 测试源码、2 脚本、13 仓库证据；另 4 日志不在补丁。补丁 SHA256 b01712880a0cd3ad578e36dd952d129435c255d1e640d7e978f82203033d77eb。
- 原样例：两个独立测试业务表共享 plt_delivery_material，13 Java 检查含浏览器入口，其内 4 场景；另 7 前端检查。不能将 13＋4 当作互不重叠的测试总数。
- 文件存储、ProjectScopeApi 和生命周期为夹具，认证为测试上下文，扫描关闭；未验证完整登录、真实对象存储、全部生产业务接入和迁移部署。
- 追加未验证增量仅三生产文件；作者明确尚未适配完整，已冻结且无运行测试进程。
- DefaultBusinessDeliveryApi.java SHA256 1fc90ee7fec89024921357e964a52436c2aba03af4ed66168e177d41a8d215ae。
- DefaultBusinessApplicationService.java SHA256 42003d11dc63137ad2e8e239a1781ea8f59de03f9ec073b85326de5d61b64e6e。
- BusinessModelAccessConfiguration.java SHA256 b9b97e49be8b9c293396f75f4fb017fda7449ac9b67e0008780de999724fd4c0。

## 后续实施优先顺序建议

1. 以保存的用户目标定义标准业务能力边界，不从现有特殊业务复制标准。
2. 先补共同继承入口、完整默认 CRUD、项目归属及权限约定；复用已有事务/并发/审计，不降低保护。
3. 将统一交付件纳入默认接口与页面，并闭合新旧材料安全归集；完成已有三文件增量的装配。
4. 补齐默认前端交互、校验和复杂查询扩展点。
5. 用真实标准业务、第二薄继承业务和特殊重载场景验收，之后再按实际缺口迁移其它长期业务。

这些是代码审查建议，不是新增批准的角色、状态机或审批语义。本轮不继续实现，不提交推送，不修改历史 SQL。

## 交付内容

- docs/decisions/2026-10-07-unified-business-default-implementation.md：用户讨论目标和纠正记录。
- review.md：本审查。
- model-inventory.json：79 描述符逐项路径、行号和操作/范围表达式。
- source-files.json：本轮直接引用的源码哈希。
- inventory-review.py：静态目录枚举脚本。

## 审查完成后的用户指令

2026-10-07 04:26 用户批准将讨论记录与本报告推送到 fix/visio-template-followup，并按本目标继续实施默认实现链。以上源码结论仍固定在 834a6a5b，后续代码需另行验证，不能将本静态审查作为运行通过证明。
