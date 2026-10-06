# 已通过两原生入口独立交付

本补丁独立基于 `be007ec3379b3c3f6951fe7a4f0052eec4c11097`，仅接入 `SOL/briefing` 手工附件与旧 `ACC/acceptance`。在隔离检出上独立编译及定向单测47项、独占MySQL/实际浏览器21项、前端8项通过，零失败/零跳过；两页pageErrors为零。原工作树及原完整48文件包保留，KNO未通过草稿不在可集成补丁内。没有commit/push/上传/部署/真实迁移；父任务在186整合后安排下一批。

## 生产变更范围

- `engineering/service/attachment/supplemental/`：只装配BRIEFING手工用途；enum、Access、Sources、@Bean和原生交付访问SPI均只保留交底。没有KNO实体、策略或验证器分支。`BriefingServiceImpl` 保存事务归集实际文件，generate前按原生成权限与MANAGE范围归集/冻结；手工保存不能改写原生成URL/name/size/checksum。真实HTML/模板/来源快照与原生成策略保留。
- `acceptance/service/acceptance/LegacyAcceptanceAttachment*`、AcceptanceServiceImpl、AcceptanceMapper/typed Query/XML：实际旧ACC/acceptance根，更新事务归集、submit前按原submit权限冻结，精确Owner锁/CAS；原编号查询含软删除完整保留。保存DTO、DO、数据库不添加attachmentUrl字段，不补历史文件或revision，不移挂新acceptanceActivity。
- `AdditionalNativeAttachments.vue` 仅briefing/legacyAcceptance两adapter，加两原生index.vue：公共上传/文件引用与统一台账，GET已保存Owner→原生UPDATE→GET，归集失败复用文件重试，不保存脏表单；冻结/只读/迟到事件与响应保护，撤回后公共完成失效。

公告Mapper、服务、页面保持be007ec字节；无KNO @Bean、DeliveryAccess或材料Source装配。独立MySQL套件明确断言新增夹具无KNO原生策略/验证器。生产范围未改公共平台框架/API合同、禁改Contributor/工勘页面/两访问策略、迁移、项目创建/详情。

## 必要依赖与测试增量

| 文件/能力 | 集成要求 |
| --- | --- |
| `NativeAttachmentDeliveryMySqlTest.java` | **测试依赖，必须带入**。保留原六链测试/声明；新增默认空的extraMapper/schema/XML/declaration/bean/Owner初始化hook及可覆盖testJdbcUrl。默认URL仍28471；新NativeTwoDeliveryMySqlTest在28501独占库复用公共真实文件/材料/事务夹具。DeliveryOwnerAccess从实际上下文收集SPI验证器，原默认四个不变。原六链45项曾对相同hook增量复验通过；两入口的独立21项进一步验证实际扩展。 |
| `BriefingGenerateDocumentTest.java` | **测试依赖，必须带入**。只补新附件协作者/Owner锁mock，原真实文档/校验和/内容快照断言保持。 |
| `NativeTwoDeliveryMySqlTest.java` + 两个two脚本 | **独立验收依赖**。只声明两真实Owner；无需KNO Mapper/草稿。脚本tmpfs项目npdms-native-delivery-two-20261006，MySQL28501、HTTP28502、Vite28503，拒绝复用、退出清理；不跑Flyway。 |
| `DeviceConfigurationLogDownloadServiceTest.java` | **测试回归增量，生产两入口不依赖新资产实现**。在NativeGeneratedFileApi授权/Owner锁返回边界模拟竞争撤回，断言首次材料读→Owner授权→重读→拒绝内容/URL降级。资产生产代码及定位符/API均保持be007ec。23项在独立检出复验通过。 |
| 已有API与原业务权限 | 复用be007ec的FileArtifactApi、PlatformDeliveryMaterialApi、NativeGeneratedFileApi、ProjectScopeApi/ProjectAcceptanceContextApi与PermissionApi；无新增跨模块biz依赖/公共合同/种子。目录/数量义务仅在专用夹具创建，不能当生产必交种子。 |

## 证据与限制

[逐用例结果](test-results.json)、[两页真实浏览器](native-two-browser.json)及两张截图均来自本独立两入口检出。失败回滚/复用重试、同一材料归业务与项目、撤回失效、替换/解绑旧冻结材料不可用、VIEW-only冻结拒绝、精确原生操作权限、生成/手工分离、生成失败回滚、驳回后元数据保护和旧编号软删除语义保留。

方法权限控制器、真实服务与事务、公共文件/材料使用真实代码；actor17/tenant7、权限/项目树/生命周期/存储是受控端口。没有完整登录/真实角色树、真实外部存储/资产HTTP下载或Flyway部署通过证据，不能宣称186上的联合整合已通过。KNO持久scope合同、业务withdraw/terminate材料CAS合同、其余入口与完整业务任务仍未完成。
